(ns merikens-2ch-browser.aborn-ids
  (:require [ajax.core :refer [GET POST]]
            [hipo.core :as hipo]
            [jayq.core :refer [$ width inner-width outer-width height inner-height outer-height attr css append ajax]]
            [jayq.util :refer [log wait]]
            [merikens-2ch-browser.util :refer [random-string
                                               random-element-id
                                               px->int
                                               calculate-scrollbar-width]])
  (:require-macros [jayq.macros :refer [ready let-ajax]]))



;;;;;;;;;;;;;;;;;;;
; PAGE COMPONENTS ;
;;;;;;;;;;;;;;;;;;;

(def page
  '([:div#title "あぼ～んID管理"]
     [:div#headings [:div.heading "ID"]]
     [:div#entries.scrollbar]
     [:button#submit-button "更新"]))

(def component-list (map #(keyword (clojure.string/replace (str (first %1)) #"^:[^#]*#" "#")) page))



;;;;;;;;;;;;
; RESIZING ;
;;;;;;;;;;;;


(defn get-window-width
  []
  (+ (-> ($ :#title) (outer-width))
     (-> ($ :body) (css "padding-left") (px->int))
     (-> ($ :body) (css "padding-right") (px->int))))

(defn get-window-horizontal-diff
  []
  (- (get-window-width) (-> ($ js/window) (inner-width))))

(defn get-window-height
  []
  (+ (apply + (map #(.outerHeight ($ %1) true) component-list))
     (int (-> ($ :body) (css "padding-top") (clojure.string/replace "px" "")))
     (int (-> ($ :body) (css "padding-bottom") (clojure.string/replace "px" "")))))

(defn get-window-vertical-diff
  []
  (- (get-window-height) (-> ($ js/window) (inner-height))))

(defn set-pattern-width
  []
  (let [$pattern ($ :.entry-item.pattern)]
    (when (pos? (count $pattern))
      (width $pattern
             (- (width ($ :#entries))
                (apply +
                       (map #(outer-width ($ %1))
                            '(:.entry-item.delete-button)))
                (apply +
                       (map #(-> ($ :.entry-item.pattern) (css %1) (px->int))
                            '("border-left" "padding-left" "padding-right" "border-right")))
                (calculate-scrollbar-width)))
      (width ($ :.entry)
             (apply + (map #(outer-width ($ %1)) '(:.entry-item.pattern :.entry-item.delete-button))))
      )))

(defn update-border-bottom
  []
  (-> ($ :.entry:last) (css "border-bottom" "1px black solid"))
  (.remove ($ :#entries-filler))
  (log (apply + (map #(outer-height ($ %1)) (js->clj ($ :.entry)))))
  (log (height ($ :#entries)))
  (when (> (apply + (map #(outer-height ($ %1)) (js->clj ($ :.entry))))
           (height ($ :#entries)))
    (-> ($ :.entry:last) (css "border-bottom" "none")))
  (let [gap (- (height ($ :#entries))
               (apply + (map #(outer-height ($ %1)) (js->clj ($ :.entry)))))]
    (when (pos? gap)
      (-> ($ "<div>")
        (attr "id" "entries-filler")
        (width (- (apply + (map #(outer-width ($ %1)) (js->clj ($ (keyword ".entry:last > .entry-item"))))) 2))
        (height gap)
        (css "border-right" "2px black solid")
        (.insertAfter ($ :.entry:last))))))

(defn resize-components
  []
  (let [window-horizontal-diff (get-window-horizontal-diff)]
    (doall (map #(width ($ %1) (- (width ($ %1)) window-horizontal-diff)) component-list))
    (height ($ :#entries) (- (height ($ :#entries)) (get-window-vertical-diff)))
    (set-pattern-width)
    (update-border-bottom)))



;;;;;;;;;;;;;;;;;;
; INITIALIZATION ;
;;;;;;;;;;;;;;;;;;

(defn ^:export init
  []
  (ready
    (enable-console-print!)

    (append ($ js/document.body) (hipo/create page))
    (.resizeTo js/window
      (+ (get-window-width) (- (.-outerWidth js/window) (.-clientWidth (.-documentElement js/document))))
      (+ (get-window-height) (- (.-outerHeight js/window) (.-clientHeight (.-documentElement js/document)))))
    (.moveBy js/window (- (/ (get-window-horizontal-diff) 2)) (- (/ (get-window-vertical-diff) 2)))
    (.resize ($ js/window) (fn [] (resize-components)))
    (resize-components)

    ; Add post filters.
    (let-ajax [aborn-posts {:url "api-aborn-ids" :dataType :json :cache false}]
      (doall (map
               #(let [pattern (aget %1 "pattern")
                      entry-id (random-element-id)
                      delete-button-id (random-element-id)]
                  (append
                    ($ :#entries)
                    (hipo/create
                      [:div.entry {:id entry-id}
                       [:div.entry-item.pattern        (if (zero? (count pattern)) "-" pattern)]
                       [:div.entry-item.delete-button {:id delete-button-id}]]))
                  (let [$entry ($ (str "#" entry-id))]
                    (.click ($ (str "#" delete-button-id))
                      (fn []
                        (.remove $entry)
                        (update-border-bottom)))
                    (.attr $entry "pattern" pattern)))
               aborn-posts))

      ; Activate the submit button.
      (.click ($ :#submit-button)
        (fn []
          (ajax {:url "/api-aborn-ids"
                 :contentType "application/json; charset=UTF-8"
                 :data (.stringify
                         js/JSON
                         (clj->js
                           {"post-filters"
                            (map
                              #(identity {:pattern (.attr ($ %) "pattern")})
                              (js->clj ($ :.entry)))}))
                 :method "POST"
                 :async true
                 :success  #(do
                              (log "success")
                              (.close js/window))
                 :error    #(log "error")
                 :complete #(log "complete")})))

      (set-pattern-width)
      (update-border-bottom))))
