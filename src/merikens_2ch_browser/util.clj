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
;
; Additional permission under GNU GPL version 3 section 7
;
; If you modify this Program, or any covered work, by linking or
; combining it with Clojure (or a modified version of that
; library), containing parts covered by the terms of EPL, the licensors
; of this Program grant you additional permission to convey the
; resulting work.{Corresponding Source for a non-source form of such
; a combination shall include the source code for the parts of clojure
; used as well as that of the covered work.}



(ns merikens-2ch-browser.util
  (:require [immutant.web :as immutant]
            [ring.util.request :refer [request-url]]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response]
            [hiccup.page :refer [include-css include-js]]
            [noir.request]
            [clj-http.client :as client]
            [taoensso.timbre :as timbre :refer [log]]
            [taoensso.timbre.appenders.rotor :as rotor]
            [merikens-2ch-browser.cursive :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [clojure.math.numeric-tower]
            [clj-time.core]
            [clj-time.format]
            [clj-time.local]
            [clj-json.core]
            [cheshire.core])
  (:import [org.owasp.html Sanitizers]
           [org.apache.commons.lang3 StringEscapeUtils]))



(defonce server-software (atom :immutant))
(defonce server (atom nil))

(defn stop-web-server
  [& [do-not-reset-server]]
  (log :info "Shutting down web server...")
  (Thread/sleep 5000)

  (cond
    (= @server-software :jetty)
    (do
      (when-not (nil? @server)
        (.stop @server)))

    (= @server-software :immutant)
    (do
      (when-not (nil? @server)
        (immutant/stop @server)))

    (= @server-software :http-kit)
    (do
      (when-not (nil? @server)
        (@server :timeout 100))))

  (if (not do-not-reset-server)
    (reset! server nil))

  (log :info "Web server was shut down successfully."))

(defn random-string
  ^String
  [^Long length]
  (let [salad "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"]
    (apply str (for [_ (range length)] (.charAt salad (rand-int (count salad)))))))

(defn random-element-id
  ^String
  []
  (random-string 16))

(defn create-element-id-for-post
  [index type context]
  (clojure.string/replace (str "post-" type "-" (:service context)"-" (:board context) "-" (:thread-no context) "-" index) #"[./]" "_"))

(defn string-to-id ^Long
  [^String s]
  (java.lang.Long/parseLong s))

(defn format-error
  ^clojure.lang.IPersistentVector
  [[error]]
  [:div.message-error error])

(defn format-validation-message
  ^clojure.lang.IPersistentVector
  [[error]]
  [:li error])

(defn current-url ^String [] (request-url noir.request/*request*))

(defn url-encoded-current-url ^String [] (url-encode (current-url)))

(defn javascript-button
  ^clojure.lang.IPersistentList
  [^String text
   ^String script
   & _]
  (let [element-id (random-element-id)]
    (list
      [:button
       (if script
         {:id element-id :class "javascript-button"}
         {:id element-id :class "javascript-button inactive"})
       text]
      [:script
       "$(document).ready(function() {"
       "$('#" element-id "').click(function () {"
       script
       "}); });"])))

(defn compact-javascript-button
  ^clojure.lang.IPersistentList
  [^String text
   ^String script
   &{ :keys [id style] :or { id nil style ""}}]
  (let [element-id (if id id (random-element-id))]
    (list
      [:button.compact
       (if script
         {:id element-id :class "javascript-button" :style style}
         {:id element-id :class "javascript-button inactive" :style style})
       text]
      [:script
       "$(document).ready(function() {"
       "$('#" element-id "').click(function () {"
       script
       "}); });"])))

(defn compact-javascript-checkbox
  ^clojure.lang.IPersistentList
  [^String text
           checked
   ^String script
   &{ :keys [id label-id style] :or {id nil style ""}}]
  (let [element-id       (if id       id       (random-element-id))
        label-element-id (if label-id label-id (random-element-id))
        input-attributes (if script
                           {:type "checkbox" :id element-id :class "" :style style}
                           {:type "checkbox" :id element-id :class "inactive" :style style})
        input-attributes (if checked (assoc input-attributes :checked "") input-attributes)]
    (list
      [:input input-attributes]
      [:label { :id label-element-id :class "compact" :for element-id} [:span] text]
      [:script
       "$(document).ready(function() {"
       "$('#" element-id "').click(function () {"
       script
       "}); });"])))

(defn link-button
  ^clojure.lang.IPersistentVector
  [^String url
   ^String text
   & _]
  [:button
   (if url
     {:onclick (str "location.href='" url "'") :class "link-button"}
     {:class "link-button inactive"})
   text])

(defn back-button []
  ^clojure.lang.IPersistentVector
  [:button {:onclick "window.history.back();" :class "link-button"} "戻る"])

(def sanitizer (-> (Sanitizers/FORMATTING)
                 (.and (Sanitizers/LINKS))
                 (.and (Sanitizers/BLOCKS))
                 (.and (Sanitizers/IMAGES))
                 (.and (Sanitizers/STYLES))))

; http://owasp-java-html-sanitizer.googlecode.com/svn/trunk/distrib/javadoc/org/owasp/html/Sanitizers.html
(comment defn sanitize-html
  ^String
  [^String code]
  (java-sanitize sanitizer code))

; TODO: Add type specifiers.
(defn format-time [timestamp]
  (-> "yyyy年MM月dd日 HH時mm分"
    (java.text.SimpleDateFormat.)
    (java-format-timestamp timestamp)))

(defn remove-html-tags
  ^String
  [^String s]
  (if s
    (clojure.string/replace s #"<(\"[^\"]*\"|'[^']*'|[^'\">])*>" "")
    nil))

(defn unescape-html-entities
  ^String
  [^String s]
  (if (not s)
    nil
    (-> s
      (StringEscapeUtils/unescapeHtml4)
      (clojure.string/replace #"&nbsp;" " ")
      (clojure.string/replace #"&gt;" ">")
      (clojure.string/replace #"&lt;" "<")
      (clojure.string/replace #"&quot;" "\"")
      (clojure.string/replace #"&amp;" "&"))))

(def ^:dynamic ^Integer *http-request-count* (ref 0))

(defn get-http-request-count
  ^Integer
  []
  @*http-request-count*)

(defn increment-http-request-count
  []
  (dosync (ref-set *http-request-count* (inc @*http-request-count*))))

(defn decrement-http-request-count
  []
  (dosync (ref-set *http-request-count* (dec @*http-request-count*))))

(defn wait-for-http-requests-to-be-processed
  []
  (loop []
    (if (>= 0 (get-http-request-count))
      nil
      (do
        ; (log :debug "wait-for-http-requests-to-be-processed: Waiting...")
        (Thread/sleep 100)
        (recur)))))

(defn bbn-check
  ^String
  []
  (try
    (let [response (clj-http.client/get "http://qb7.2ch.net/_403/c403.cgi" {:encoding "Shift_JIS"})
          your-host (nth (re-find #"<b>(.*)</b> \([0-9.]+\)" (:body response)) 1 nil)
          bbn-entry (and your-host
                         (re-find (re-pattern (str "<tr>\n.*\n.*\n.*\n.*\n.*<font color=red>" your-host "</font>.*\n</tr>")) (:body response)))
          sanitized (and bbn-entry
                         (-> bbn-entry
                           (clojure.string/replace #"<td>..</td>" " ")
                           (clojure.string/replace #"<input [^>]*>" " ")
                           (clojure.string/replace #"</?(tr|td)( [^>]+)?>" " ")
                           (clojure.string/replace #"</?(font)( [^>]+)?>" " ")
                           (clojure.string/replace #"[ \n\t]+" " ")
                           (clojure.string/replace #"^ " "")
                           (clojure.string/replace #" $" "")
                           (clojure.string/replace #"\( " "(")
                           (clojure.string/replace #" \)" ")")))
          time-passed    (and sanitized (Integer/parseInt (re-find #"[0-9]+$" sanitized)))
          time-remaining (and sanitized (- 120 time-passed))
          message        (and sanitized (str "あなたはバーボンハウスによって規制されています。<br>"
                                             (if (> time-remaining 0)
                                               (str "あと約" time-remaining "分で規制は解除されます。")
                                               (str "規制されてから" time-passed "分経過したのでもうすぐ規制は解除されます。"))
                                             "<br>"
                                             sanitized
                                             ))]
      message)
    (catch Throwable _
      nil)))

(defn get-ronin-session-id
  ^String
  []
  (try
    (let [request-body (str "ID=" (db/get-user-setting "ronin-email") "&"
                            "PW=" (db/get-user-setting "ronin-secret-key"))
          params {:decode-body-headers true :as :auto
                  :headers {"Content-Type" "application/x-www-form-urlencoded"
                            "User-Agent"   user-agent}
                  :body request-body
                  :body-encoding "ISO-8859-1"}
          url     "https://2chv.tora3.net/futen.cgi"
          response (client/post url params)]
      ; (log :debug "get-ronin-session-id:" (:body response))
      (if (and (= (:status response) 200)
               (re-find #"^SESSION-ID=" (:body response)))
        (clojure.string/replace (:body response) #"^SESSION-ID=" "")
        nil))
    (catch Throwable _
      nil)))

(defn internal-server-error
  ^clojure.lang.IPersistentMap
  []
  {:status  503
   :headers {"Content-Type" "text/plain; charset=utf-8"}
   :body    "503 Internal Server Error"})

(defn set-mousedown-event-handler
  ^clojure.lang.IPersistentVector
  [selector left-action right-action & rest]
  (let [middle-action (nth rest 0 nil)]
    [:script {:class "keep"}
     "$(document).ready(function() {"
     "$('" selector "')"
     "    .off('mousedown')"
     "    .mousedown(function(event) {"
     "        if (event.which == 1) {"
     left-action
     "            return false;"
     "        } else if (event.which == 2) {"
     (if middle-action
       "          event.preventDefault();")
     middle-action
     (if middle-action
       "          return false;")
     "        } else if (event.which == 3) {"
     right-action
     (if right-action
       "          return false;")
     "        }"
     "        return true;"
     "    })"
     (if right-action
       " .attr('oncontextmenu', 'return false;')")
     ";"
     "});"]))

(defn set-click-event-handler
  ^clojure.lang.IPersistentVector
  [selector left-action right-action & rest]
  (let [middle-action (nth rest 0 nil)]
    [:script {:class "keep"}
     "$(document).ready(function() {"
     "$('" selector "').off('click').click(function(event) {" left-action "})"
     (str ".off('mousedown')"
          ".mousedown(function(event) {"
          "    if (event.which == 2) {"
          (if middle-action
            "      event.preventDefault();")
          middle-action
          (if middle-action
            "      return false;")
          "    } else if (event.which == 3) {"
          right-action
          (if right-action
            "      return false;")
          "    }"
          "    return true;"
          "})")
     (if right-action
       " .attr('oncontextmenu', 'return false;')")
     ";"
     "});"]))

(defn post-id?
  ^Boolean
  [^String id]
  (boolean (re-find #"^ID:[a-zA-Z0-9+/.]+$" id)))

(defn configure-timbre
  []
  (let [filename-base  "merikens-2ch-browser"]
    (timbre/set-config!
      [:fmt-output-fn]
      (fn [{:keys [level throwable message timestamp]}
           & [{:keys [] :as _}]]
        (format "%s [%s] %s%s"
                timestamp
                (-> level name clojure.string/upper-case)
                (or message "")
                (or (and throwable (map str (.getStackTrace throwable))) ""))))
    (timbre/set-config! [:timestamp-pattern] "yyyy-MM-dd HH:mm:ss")

    (timbre/set-config!
      [:appenders :standard-out]
      {:enabled? false})
    (timbre/set-config!
      [:appenders :customized-standard-out]
      {:doc "Prints to *out*/*err*."
       :min-level nil :enabled? true :async? false :rate-limit nil
       :fn (fn [{:keys [error? output]}] ; Use any appender args
             (binding [*out* (if error? *err* *out*)]
               (println (clojure.string/replace output #"^[0-9-]+ [0-9:]+ \[[A-Z]+\] " ""))))})
    (timbre/set-config!
      [:appenders :rotor]
      {:min-level :debug,
       :enabled? true,
       :async? false,
       :max-message-per-msecs nil,
       :fn rotor/appender-fn})
    (timbre/set-config! [:middleware]
                        [(fn [m] (if (re-find #"Undertow request failed HttpServerExchange\{ GET /api-get-favorite-board-list\}" (str (nth (:args m) 0 ""))) nil m))
                         (fn [m] (if (re-find #"Database is already closed" (str (nth (:args m) 0 ""))) nil m))
                         (fn [m] (if (re-find #"Database is already closed" (str (nth (:args m) 1 ""))) nil m))
                         ])
    (timbre/set-config!
      [:shared-appender-config :rotor]
      {:path (str filename-base ".log"),
       :max-size (* 5 1024 1024),
       :backlog 1})))

(defn remove-ng-words-from-thread-title
  ^String
  [^String title]
  ; (log :debug "remove-ng-words-from-thread-title:" title)
  (if title
    (clojure.string/replace title #"[ 　\t]*(\[転載禁止\]|(©|\(c\))(2ch\.net|bbspink\.com))+[ 　\t]*" "")
    ""))

(defn remove-ng-words-from-cap
  ^String
  [^String cap]
  ; (log :debug "remove-ng-words-from-thread-title:" title)
  (if cap
    (clojure.string/replace cap #"[ 　\t]*(転載ダメ|転載せんといてや|＠転載は禁止|©(2ch\.net|bbspink\.com))+[ 　\t]*" "")
    ""))

; TODO: Add type specifiers.
(defn get-progress
  ^String
  [start-time done total]
  (let [time-passed (/ (- (System/currentTimeMillis) start-time) 1000)
        estimate    (/ (* time-passed total) done)
        remaining-time (- estimate time-passed)
        etc (clj-time.core/plus (clj-time.local/local-now) (clj-time.core/seconds remaining-time))]
    (str "(" done "/" total ", "
         ; (format "%.0fh"   (clojure.math.numeric-tower/floor (double (/      time-passed       3600))))
         ; (format "%02.0fm" (clojure.math.numeric-tower/floor (double (/ (rem time-passed 3600) 60))))
         ; (format "%02.0fs" (clojure.math.numeric-tower/floor (double    (rem time-passed   60))))
         ; " passed, "
          (format "%.0fh"   (clojure.math.numeric-tower/floor (double (/      remaining-time       3600))))
         (format "%02.0fm" (clojure.math.numeric-tower/floor (double (/ (rem remaining-time 3600) 60))))
         (format "%02.0fs" (clojure.math.numeric-tower/floor (double    (rem remaining-time   60))))
         " remaining, "
         "ETC: " (clojure.string/replace (clj-time.local/format-local-time etc :hour-minute) #"T" " ") ; :date-hour-minute
         ")")))

(defn create-board-info-map
  [items]
  (apply merge
         (map #(let [components (clojure.string/split %1 #"#")
                     service (first components)
                     board   (second components)]
                 (hash-map (keyword (str service "#" board)) (db/get-board-info service board)))
              (distinct (map #(let [{:keys [service board]} %1] (str service "#" board)) items)))))

(defn create-bookmark-map
  [service board]
  (apply merge (map #(hash-map (keyword (str (:thread-no %1))) (:bookmark %1))
                                           (db/get-bookmark-list-for-board service board))))

(defn valid-dat-content?
  [dat-content]
  ; (log :debug "valid-dat-content?")
  (try
    (and (not (re-find #"<title>もうずっと人大杉</title>" dat-content))
         (not (re-find #"あれ\? ★<><>404<>ないぞ<>404 だ" dat-content))
         (re-find #"<>" dat-content)
         (re-find #"\n$" dat-content))
    (catch Throwable _
      false)))

(defn valid-thread-in-html-in-potato-format?
  [html-body]
  (re-find #"<div class=\"thread\">" html-body))

(defn valid-thread-in-html?
  [html-body]
  (or (valid-thread-in-html-in-potato-format? html-body)
      (and (re-find #"<dl class=\"thread\"" html-body)
           ; (not (re-find #"<img src=\"[^\"]+/bucket-full.png\">" html-body))
           )))


(defn include-css-no-cache
  [script]
  (include-css (str script "?" (rand-int 1000000000))))

(defn include-js-no-cache
  [script]
  (include-js (str script "?" (rand-int 1000000000))))



(defn mobile-device?
  []
  (let [ua (ring.util.response/get-header noir.request/*request* "user-agent")]
    (log :info "User Agent:" ua)
    (or (re-find #"[^a-zA-Z]iP(hone|ad|od)[^a-zA-Z]" ua)
        (re-find #"[^a-zA-Z]Android[^a-zA-Z]" ua))))



(defn process-replacement-patterns
  [s patterns]
  (if (zero? (count patterns))
    s
    (process-replacement-patterns
      (clojure.string/replace s (first (first patterns)) (second (first patterns)))
      (rest patterns))))



(defn generate-json-response
  [data]
  {:status  200
   :headers {"Content-type" "application/json; charset=utf-8"}
   ; :body    (clj-json.core/generate-string data)
   :body    (cheshire.core/generate-string data)})



