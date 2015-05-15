; This file is part of Meriken's 2ch Browser.
;
; Meriken's 2ch Browser is free software: you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation, either version 3 of the License, or
; (at your option) any later version.
;
; Meriken's 2ch Browser is distributed in the hope that it will be useful,
; but WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
; GNU General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with Meriken's 2ch Browser.  If not, see <http://www.gnu.org/licenses/>.



(ns merikens-2ch-browser.aborn-filters
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
  '([:div#title "あぼ～んフィルタ管理"]
     [:div#headings
      [:div.heading.pattern "パターン(正規表現)"]
      [:div.heading.board "板"]
      [:div.heading.thread-title-pattern "スレタイ(正規表現)"]]
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
      (let [pattern-width (- (width ($ :#entries))
                         (apply +
                                (map #(outer-width ($ %1))
                                     '(:.entry-item.board :.entry-item.thread-title-pattern :.entry-item.delete-button)))
                         (apply +
                                (map #(-> ($ :.entry-item.pattern) (css %1) (px->int))
                                     '("border-left" "padding-left" "padding-right" "border-right")))
                         (calculate-scrollbar-width))]
        (width $pattern pattern-width)
        (width ($ :.heading.pattern) pattern-width)
        ))))

(defn update-border-bottom
  []
  (-> ($ :.entry:last) (css "border-bottom" "1px black solid"))
  (.remove ($ :#entries-filler))
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

(def add-empty-entry-if-necessary)

(defn add-post-filter
  [pattern board thread-title-pattern]
  (let [entry-id         (random-element-id)
        delete-button-id (random-element-id)]
    (append
      ($ :#entries)
      (hipo/create
        [:div.entry {:id entry-id}
         [:input.entry-item.pattern    {:type "text" :value pattern}]
         [:input.entry-item.board      {:type "text" :value board}]
         [:input.entry-item.thread-title-pattern     {:type "text" :value thread-title-pattern}]
         [:div.entry-item.delete-button {:id delete-button-id}]]))
    (let [$entry ($ (str "#" entry-id))]
      (.click ($ (str "#" delete-button-id))
        (fn []
          (.remove $entry)
          (add-empty-entry-if-necessary)
          (update-border-bottom)))
      (.on (.children $entry ".pattern") "input" add-empty-entry-if-necessary)
      (.on (.children $entry ".board") "input" add-empty-entry-if-necessary)
      (.on (.children $entry ".thread-title-pattern") "input" add-empty-entry-if-necessary)
      (.attr $entry "pattern" pattern)
      (.attr $entry "board"   board)
      (.attr $entry "thread-title-pattern"  thread-title-pattern))))

(defn add-empty-entry-if-necessary
  []
  (when (or (zero? (count (js->clj ($ :.entry))))
            (pos? (count (.val (.children ($ :.entry:last) ".pattern"))))
            (pos? (count (.val (.children ($ :.entry:last) ".board"))))
            (pos? (count (.val (.children ($ :.entry:last) ".thread-title-pattern")))))
    (add-post-filter "" "" "")
    (set-pattern-width)
    (update-border-bottom))

  (let [entry-count (count (js->clj ($ :.entry)))
        last-entry ($ :.entry:last)
        second-to-last-entry ($ (str ".entry:nth-of-type(" (dec entry-count) ")"))]
    (when (and (<= 2 (count (js->clj ($ :.entry))))
               (zero? (count (.val (.children last-entry ".pattern"))))
               (zero? (count (.val (.children last-entry ".board"))))
               (zero? (count (.val (.children last-entry ".thread-title-pattern"))))
               (zero? (count (.val (.children second-to-last-entry ".pattern"))))
               (zero? (count (.val (.children second-to-last-entry ".board"))))
               (zero? (count (.val (.children second-to-last-entry ".thread-title-pattern")))))
      (.remove last-entry)
      )))

(defn add-post-filters
  []
  (let-ajax [aborn-posts {:url "api-aborn-filters" :dataType :json :cache false}]
      (doall (map
               #(let [pattern (aget %1 "pattern")
                      board   (aget %1 "board")
                      thread-title-pattern  (aget %1 "thread-title")]
                  (add-post-filter pattern board thread-title-pattern))
               aborn-posts))
      (add-post-filter "" "" "")

      ; Activate the submit button.
      (.click ($ :#submit-button)
        (fn []
          (ajax {:url "/api-aborn-filters"
                 :contentType "application/json; charset=UTF-8"
                 :data (.stringify
                         js/JSON
                         (clj->js
                           {"post-filters"
                            (remove nil?
                                    (map
                                      #(let [pattern      (.val (.children ($ (clj->js %)) ".pattern"))
                                             board        (.val (.children ($ (clj->js %)) ".board"))
                                             thread-title (.val (.children ($ (clj->js %)) ".thread-title-pattern"))]
                                         (if (and (pos? (count pattern)))
                                           {:pattern      pattern
                                            :board        board
                                            :thread-title thread-title}))
                                      (js->clj ($ :.entry))))}))
                 :method "POST"
                 :async true
                 :success  #(do
                              (log "success")
                              (.close js/window))
                 :error    #(log "error")
                 :complete #(log "complete")})))

      (set-pattern-width)
      (update-border-bottom)))

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

    (add-post-filters)))
