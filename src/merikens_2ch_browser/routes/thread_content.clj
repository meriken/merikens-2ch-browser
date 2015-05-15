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



(ns merikens-2ch-browser.routes.thread-content
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            ; [clojure.data.json :as json]
            [ring.handler.dump]
            [ring.util.response :as response]
            [compojure.core :refer :all]
            [noir.response :refer [redirect]]
            [noir.request]
            [noir.session :as session]
            [noir.validation :refer [rule errors? has-value? on-error]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]
            [hiccup.form :refer :all]
            [hiccup.element :refer :all]
            [hiccup.util :refer [escape-html]]
            [taoensso.timbre :as timbre]
            [clj-http.client :as client]
            [pandect.algo.sha256 :refer :all]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [clj-time.predicates]
            ; [cheshire.core]
            [clj-json.core]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.routes.image :refer [set-up-download ng-image? ng-image-url?]]
            [merikens-2ch-browser.db.core :as db]
            [com.climate.claypoole :as cp]
            [clojure.data.codec.base64 :as base64])
  (:import org.h2.util.Profiler
           org.jsoup.Jsoup))




(defn convert-message-in-post-into-plain-text
  [text]
  ; (timbre/debug text)
  (if (nil? text)
    ""
    (-> text
      ; Remove junky hyperlinks.
      (clojure.string/replace #"(?i)<a([^>]+)>(.+?)</a>" "$2")

      ; Remove img tags for icons.
      (clojure.string/replace #"(?i)<img[^>]+src *= *\"http://([^>]*)\"([^>]*)>" #(str "sssp://" (second %1)))

      ; Remove unnecessary tags.
      (clojure.string/replace #"(?i)<img[^>]+>"     "") ; xpic.sc
      (clojure.string/replace #"(?i)</?font[^>]*>"  "")
      (clojure.string/replace #"(?i)</?em>"         "") ; 2ch.sc
      (clojure.string/replace #"(?i)</?div[^>]*>"   "") ; open2ch.net
      (clojure.string/replace #"(?i)<br clear=all>" "") ; open2ch.net

      ;
      (process-replacement-patterns replacement-patterns-for-message-in-post)

      (clojure.string/replace "[ \n\t]+" " ")
      (clojure.string/replace "<br>" "\n")
      (unescape-html-entities) ; "&nbsp;" does not work this way. Oh well.
      )))

; </b></b>zQm993vzJI<b> ; open
; </b>◆Meriken//XXX<b> ; sc
; <font color=#8B008B> </b>◆rBEoblancQ <b>＠Grape Ape ★</font> ; 2ch.net
; <b>いわし ★</b>
(defn split-name-field
  [name-field]
  (try
    (let [name-field (-> name-field
                       (clojure.string/replace "◆</b></b>" "</b>◆") ; open sucks
                       (clojure.string/replace #"</b>$"    ""))
          after-handle (nth (clojure.string/split name-field #" *</b> *") 1 "")]
      {:handle-or-cap (remove-ng-words-from-cap (unescape-html-entities (remove-html-tags (nth (clojure.string/split name-field #" *</b> *") 0))))
       :tripcode                                (unescape-html-entities (remove-html-tags (nth (clojure.string/split after-handle #" *<b> *") 0)))
       :cap           (remove-ng-words-from-cap (unescape-html-entities (remove-html-tags (nth (clojure.string/split after-handle #" *<b> *") 1 ""))))})
    (catch Throwable t
      (timbre/debug "split-name-field:" (str t))
      (print-stack-trace t)
      nil)))

(defn convert-post-in-dat-to-html　[post precomputed-index context]
  (let [thread-url    (:thread-url context)
        {:keys [service server board thread]} context
        delta         (if (or (:shitaraba? context) (:machi-bbs? context)) 1 0)
        items         (clojure.string/split post #"<>")
        index         (if (or (:shitaraba? context) (:machi-bbs? context)) (Integer/parseInt (first items)) precomputed-index)
        special-color (nth (re-find #"<font color=#([0-9A-Fa-f]+)>" (nth items delta))
                           1
                           nil)
        {:keys [handle-or-cap tripcode cap]} (split-name-field (nth items delta))
        email         (unescape-html-entities (remove-html-tags (nth items (+ 1 delta) nil)))
        etc           (unescape-html-entities (remove-html-tags (nth items (+ 2 delta) nil)))
        etc           (cond
                        (and (:shitaraba? context)
                             (pos? (count (nth items (+ 5 delta) nil))))
                        (str etc " ID:" (nth items (+ 5 delta) nil))

                        (and (or (:machi-bbs? context))
                             (pos? (count (nth items (+ 5 delta) nil))))
                        (str etc " [" (nth items (+ 5 delta) nil) "]")

                        :else
                        etc)
        message       (convert-message-in-post-into-plain-text (nth items (+ 3 delta) nil))]
    (if (or ((:display-post-fn context) index)
            (some #{index} (:posts-to-keep context)))
      ((:convert-raw-post-to-html context) index handle-or-cap tripcode cap email etc message special-color context))))

(defn get-res-count-for-dat-file
  [dat-file]
  (count (re-seq #"\n" (new String (:content dat-file) (get-encoding-for-get-method (:server dat-file))))))

(defn download-entire-dat-file-through-net-api
  [context]
  ; (timbre/debug "download-entire-dat-file-through-net-api")
  (let [start-time (System/nanoTime)
        {:keys [service server original-server current-server board thread-no]} context
        current-time (quot (System/currentTimeMillis) 1000)
        ronin?     (= (db/get-user-setting "use-ronin") "true")
        session-id (clojure.string/replace (:body (client/post "https://api.2ch.net/v1/auth/"
                                                               {:decode-body-headers true :as "ASCII"
                                                                :headers {"User-Agent"   ""
                                                                          "X-2ch-UA" net-api-x-2ch-ua
                                                                          "Content-Type" "application/x-www-form-urlencoded"}
                                                                :body (str "ID="  (if ronin? (db/get-user-setting "ronin-email") "")
                                                                           "&PW=" (if ronin? (db/get-user-setting "ronin-secret-key") "")
                                                                           "&KY=" net-api-app-key "&CT=" current-time "&HB=" (sha256-hmac (str net-api-app-key current-time) net-api-hm-key))
                                                                }))
                                           #"^SESSION-ID=Monazilla/1.00:"
                                           "")
        dat-url  (str "https://api.2ch.net/v1/" (clojure.string/replace server #"\..*$" "") "/" board "/" thread-no)
        options  {:as :byte-array
                  :socket-timeout 10000
                  :conn-timeout   30000
                  :headers {"Content-Type" "application/x-www-form-urlencoded"
                            "Connection" "close"
                            "Accept-Encoding" "gzip"
                            "User-Agent"   net-api-user-agent}
                  :body (str "sid=" session-id
                             "&hobo=" (sha256-hmac (str "/v1/" (clojure.string/replace server #"\..*$" "") "/" board "/" thread-no session-id net-api-app-key) net-api-hm-key)
                             "&appkey=" net-api-app-key)}
        options  (merge options (proxy-server dat-url :get))
        response (client/post dat-url options)
        dat-content (new String (:body response) (get-encoding-for-get-method server))
        res-count   (count (re-seq #"\n" dat-content))]
    (if (or (not (= (:status response) 200))
            (< 1 (count (:trace-redirects response)))
            (not (valid-dat-content? dat-content)))
      (throw (Exception. "Not a valid DAT file.")) ; 血バケツ? バーボン? 人大杉?
      (let [headers (into {} (for [[k v] (:headers response)] [(keyword k) v]))
            old-dat-file (db/get-dat-file service board thread-no)
            new-dat-file {:user_id       (:id (session/get :user))
                          :service       service
                          :server        original-server
                          :board         board
                          :thread_no     (Long/parseLong thread-no)
                          :etag          (:ETag headers)
                          :last-modified (:Last-Modified headers)
                          :content       (:body response)
                          :res_count     res-count
                          :size          (count (:body response))
                          :source_url    dat-url}]
        (when (or (nil? old-dat-file)
                  (>= res-count (:res-count old-dat-file)))
          (if old-dat-file
            (db/delete-dat-file service board thread-no))
          (db/add-dat-file new-dat-file))
        (timbre/info (str "    Downloaded DAT file through API (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms, " (count (:body response)) " bytes)."))
        dat-content))))

(defn download-entire-dat-file-from-specific-server
  [dat-url context]
  ; (timbre/debug "download-entire-dat-file-from-specific-server: " dat-url)
  (let [{:keys [service server original-server current-server board thread-no]} context
        options     {:as :byte-array
                     :socket-timeout 60000
                     :conn-timeout   60000
                     :headers {"User-Agent"    user-agent
                               "Cache-Control" "no-cache"}}
        options     (merge options (proxy-server dat-url :get))
        response    (clj-http.client/get dat-url options)
        dat-content (new String (:body response) (get-encoding-for-get-method server))
        rokka? (re-find #"^http://rokka\." dat-url) ; Rokka adds "Success Archive\n" to the dat file (16 bytes).
        rokka-magic-string-length (count "Success Archive\n")
        res-count   (count (re-seq #"\n" dat-content))
        res-count   (if rokka? (dec res-count) res-count)]
    (timbre/debug "download-entire-dat-file-from-specific-server: res-count:" res-count)
    (if (or (not (= (:status response) 200))
            (< 1 (count (:trace-redirects response)))
            (not (valid-dat-content? dat-content)))
      (throw (Exception. "Not a valid DAT file.")) ; 血バケツ? バーボン? 人大杉?
      (let [headers (into {} (for [[k v] (:headers response)] [(keyword k) v]))
            old-dat-file (db/get-dat-file service board thread-no)
            new-dat-file {:user_id       (:id (session/get :user))
                          :service       service
                          :server        original-server
                          :board         board
                          :thread_no     (Long/parseLong thread-no)
                          :etag          (:ETag headers)
                          :last-modified (:Last-Modified headers)
                          :content       (if rokka?
                                           (byte-array (drop rokka-magic-string-length (:body response)))
                                           (:body response))
                          :res_count     res-count
                          :size          (if rokka?
                                           (- (count (:body response)) rokka-magic-string-length)
                                           (count (:body response)))
                          :source_url    dat-url}]
        (when (or (nil? old-dat-file)
                  (>= res-count (:res-count old-dat-file)))
          (if old-dat-file
            (db/delete-dat-file service board thread-no))
          (db/add-dat-file new-dat-file))
        dat-content))))

(defn download-entire-current-dat-file
  [context]
  ; (timbre/debug "download-entire-current-dat-file")
  (let [start-time (System/nanoTime)
        {:keys [service original-server board thread-no thread-url]} context
        dat-url (thread-url-to-dat-url thread-url)]
    (try
      ; (timbre/debug "download-entire-current-dat-file: Downloading:" dat-url)
      (let [result (download-entire-dat-file-from-specific-server dat-url context)]
        (timbre/info (str "    Downloaded current DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
        result)

      (catch Throwable t
        ; (timbre/debug "download-entire-current-dat-file: Download failed:" dat-url)
        (timbre/info (str "    Failed to download current DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
        (throw t)))))

(defn download-entire-archived-dat-file
  "Try to download every archived dat file available.
The most recent version will (hopefully) be stored in the database."
  [context]
  ; (timbre/debug "download-entire-archived-dat-file")
  (let [start-time (System/nanoTime)
        {:keys [service original-server board thread-no thread-url]} context
        locations (list [(thread-url-to-kako-dat-url thread-url true)  true]     ; kako log with ".gz"
                        [(thread-url-to-kako-dat-url thread-url false) true]     ; kako log without ".gz"
                        ; [(thread-url-to-offlaw2-dat-url thread-url)    false]    ; offlaw2
                        (if (or (net-url? thread-url) (bbspink-url? thread-url))
                          [(thread-url-to-rokka-dat-url thread-url)  true]       ; Rokka
                          nil)
                        [(str "http://mimizun.com/log/2ch/" board "/" thread-no ".dat") false]) ; mimizun

        results (cp/pmap :builtin
                         #(let [dat-url %1
                                mark-as-archived? %2]
                            (try
                              (if (nil? dat-url)
                                (throw (Exception.)))
                              (download-entire-dat-file-from-specific-server dat-url context)
                              ; (when mark-as-archived?
                              ;  (db/mark-thread-as-archived service original-server board thread-no)
                              ; (timbre/debug "download-entire-archived-dat-file: mark-as-archived? true" (str "(url: " dat-url ")"))
                              ;  )
                              ; (timbre/debug "download-entire-archived-dat-file: Download succeeded:" dat-url)
                              true
                              (catch Throwable t
                                ; (timbre/debug "download-entire-archived-dat-file: Download failed:" dat-url)
                                false)))
                         (map first locations)
                         (map second locations))

        found-one (< 0 (count (remove not results)))]

    ; (timbre/debug "found-one:" found-one)
    (if (not found-one)
      (throw (Exception.)))
    (timbre/info (str "    Downloaded archived DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
    (:content (db/get-dat-file service board thread-no))))

(defn update-dat-file-through-net-api
  [context]
  ; (timbre/debug "update-dat-file")
  (let [start-time (System/nanoTime)
        {:keys [service server board thread thread-no thread-url]} context
        dat-url              (thread-url-to-dat-url thread-url)
        dat-file-in-database (db/get-dat-file service board thread)]
    (try
      (let [current-time (quot (System/currentTimeMillis) 1000)
            session-id (clojure.string/replace (:body (client/post "https://api.2ch.net/v1/auth/"
                                                                   {:decode-body-headers true :as "ASCII"
                                                                    :headers {"User-Agent"   ""
                                                                              "X-2ch-UA" net-api-x-2ch-ua
                                                                              "Content-Type" "application/x-www-form-urlencoded"}
                                                                    :body (str "ID=&PW=&KY=" net-api-app-key "&CT=" current-time "&HB=" (sha256-hmac (str net-api-app-key current-time) net-api-hm-key))
                                                                    }))
                                               #"^SESSION-ID=Monazilla/1.00:"
                                               "")
            dat-url  (str "https://api.2ch.net/v1/" (clojure.string/replace server #"\..*$" "") "/" board "/" thread-no)
            options  {:as :byte-array
                      :decompress-body false
                      :socket-timeout 10000
                      :conn-timeout   30000
                      :headers {"Accept"            "*/*"
                                "Accept-Language"   "ja"
                                "If-Modified-Since" (:last-modified dat-file-in-database)
                                "If-None-Match"     (:etag dat-file-in-database)
                                "Range"             (str "bytes= " (- (:size dat-file-in-database) 1) "-")
                                "Cache-Control"     "no-cache"
                                "Content-Type" "application/x-www-form-urlencoded"
                                "Connection" "close"
                                "User-Agent"   net-api-user-agent}
                      :body (str "sid=" session-id
                                 "&hobo=" (sha256-hmac (str "/v1/" (clojure.string/replace server #"\..*$" "") "/" board "/" thread-no session-id net-api-app-key) net-api-hm-key)
                                 "&appkey=" net-api-app-key)}
            options  (merge options (proxy-server dat-url :get))
            response (client/post dat-url options)
            status (:status response)]
        (if (or (not (or (= (:status response) 200)
                         (= (:status response) 206)))
                (< 1 (count (:trace-redirects response))))
          (throw (Exception.)))
        (let [headers (into {} (for [[k v] (:headers response)] [(keyword k) v]))
              new-content (if (= status 206)
                            (byte-array (concat (:content dat-file-in-database) (byte-array (subvec (vec (:body response)) 1))))
                            (:body response))
              converted   (new String new-content (get-encoding-for-get-method server))
              res-count   (count (re-seq #"\n" converted))]
          (if (and (= status 206) (not (= (first (:body response)) 10)))
            (download-entire-current-dat-file context) ; あぼ－ん
            (do
              ; (timbre/debug "update-dat-file: res-count:" res-count)
              (db/update-dat-file
                service
                board
                (Long/parseLong thread)
                (:ETag headers)
                (:Last-Modified headers)
                new-content
                (count new-content)
                res-count)
              (timbre/info (str "    Updated DAT file through API (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms, " (count (:body response)) " bytes)."))
              converted))))
      (catch Throwable t
        (cond
          ; not modified
          (= (str "clj-http: status 304"))
          (do
            ; (timbre/debug "status: 304")
            (timbre/info (str "    DAT file is up-to-date (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
            (new String (:content dat-file-in-database) (get-encoding-for-get-method server)))
          ; あぼーん
          (= (str "clj-http: status 416"))
          (do
            ; (timbre/debug "status: 416")
            (timbre/info (str "    Failed to update DAT file through API (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
            (download-entire-current-dat-file context))
          :else
          (do
            (timbre/debug (str t))
            (timbre/info (str "    Failed to update DAT file through API (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
            (download-entire-current-dat-file context)))))))

(defn update-dat-file
  [context]
  ; (timbre/debug "update-dat-file")
  (let [start-time (System/nanoTime)
        {:keys [service server board thread thread-url]} context
        dat-url              (thread-url-to-dat-url thread-url)
        dat-file-in-database (db/get-dat-file service board thread)]
    (try
      (let [headers  {"Accept"            "*/*"
                      "User-Agent"        user-agent
                      "Accept-Language"   "ja"
                      "If-Modified-Since" (:last-modified dat-file-in-database)
                      "If-None-Match"     (:etag dat-file-in-database)
                      "Range"             (str "bytes= " (- (:size dat-file-in-database) 1) "-")
                      "Cache-Control"     "no-cache"}
            options  {:as :byte-array
                      :headers headers
                      :decompress-body false
                      :socket-timeout 180000
                      :conn-timeout   180000}
            options  (merge options (proxy-server dat-url :get))
            response (clj-http.client/get dat-url options)
            status (:status response)]
        (if (or (not (or (= (:status response) 200)
                         (= (:status response) 206)))
                (< 1 (count (:trace-redirects response))))
          (throw (Exception.)))
        (let [headers (into {} (for [[k v] (:headers response)] [(keyword k) v]))
              new-content (if (= status 206)
                            (byte-array (concat (:content dat-file-in-database) (byte-array (subvec (vec (:body response)) 1))))
                            (:body response))
              converted   (new String new-content (get-encoding-for-get-method server))
              res-count   (count (re-seq #"\n" converted))]
          (if (and (= status 206) (not (= (first (:body response)) 10)))
            (download-entire-current-dat-file context) ; あぼ－ん
            (do
              ; (timbre/debug "update-dat-file: res-count:" res-count)
              (db/update-dat-file
                service
                board
                (Long/parseLong thread)
                (:ETag headers)
                (:Last-Modified headers)
                new-content
                (count new-content)
                res-count)
              (timbre/info (str "    Updated DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms, " (count new-content) " bytes)."))
              converted))))
      (catch Throwable t
        (cond
          ; not modified
          (= (str "clj-http: status 304"))
          (do
            ; (timbre/debug "status: 304")
            (timbre/info (str "    DAT file is up-to-date (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
            (new String (:content dat-file-in-database) (get-encoding-for-get-method server)))
          ; あぼーん
          (= (str "clj-http: status 416"))
          (do
            ; (timbre/debug "status: 416")
            (timbre/info (str "    Failed to update DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
            (download-entire-current-dat-file context))
          :else
          (do
            (timbre/debug (str t))
            (timbre/info (str "    Failed to update DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
            (download-entire-current-dat-file context)))))))

(defn generate-display-post-fn
  [context res-count]
  ; (timbre/debug "generate-display-post-fn:" res-count)
  (let [options   (:thread-options context)
        options   (if (>= 0 (count options)) "1-" options)
        parts     (clojure.string/split options #"[,+]")
        max-count (:max-count context)
        start     (:start context)
        start     (if (nil? start) 1 start)]
    (fn [index]
      (some
        identity
        (map
          #(or
             false ; (and (not (re-find #"n" %)) (= index 1))
             (and
               (<= index res-count)
               (>= index start)
               (or (nil? max-count) (< index (+ start max-count)))
               (or (not (:new-posts-only context))
                   (nil? (:bookmark context))
                   (>= index (:bookmark context)))
               (cond
                 (re-find #"^[0-9]+n?$" %)
                 (let [opt-index (java.lang.Integer/parseInt (second (re-find #"^([0-9]+)n?$" %)))]
                   (= index opt-index))

                 (re-find #"^[0-9]+n?-$" %)
                 (let [opt-lower-index (java.lang.Integer/parseInt (second (re-find #"^([0-9]+)n?-$" %)))]
                   (<= opt-lower-index index))

                 (re-find #"^[0-9]+n?-[0-9]+n?$" %)
                 (let [opt-lower-index (java.lang.Integer/parseInt (second (re-find #"^([0-9]+)n?-([0-9]+)n?$" %)))
                       opt-upper-index (java.lang.Integer/parseInt (nth    (re-find #"^([0-9]+)n?-([0-9]+)n?$" %) 2))]
                   (and (<= opt-lower-index index) (<= index opt-upper-index)))

                 (re-find #"^l[0-9]+n?$" %)
                 (let [opt-count# (java.lang.Integer/parseInt (second (re-find #"^l([0-9]+)n?$" %)))]
                   (< (- res-count opt-count#) index))

                 :else
                 true)))
          parts)))))

(defn find-references-in-post
  [message res-count]
  (let [anchors (re-seq #"(>>?|＞＞?|≫)(([0-9]{1,4})(-[0-9]{1,4})?,)*([0-9]{1,4})(-[0-9]{1,4})?" message)]
    (if (nil? anchors)
      nil
      (->> anchors
        (map first)
        (map #(-> %1
                (clojure.string/replace #"(>>?|＞＞?|≫|n)" "")
                (split #",")))
        (apply concat)
        (map #(cond
                (re-find #"^[0-9]+$" %1) (list (Integer/parseInt %1))
                (re-find #"^([0-9]+)-$" %1) (range (Integer/parseInt (nth (re-find #"^([0-9]+)-$" %1) 1))
                                                   (inc res-count))
                (re-find #"^([0-9]+)-([0-9]+)$" %1) (range (Integer/parseInt (nth (re-find #"^([0-9]+)-([0-9]+)$" %1) 1))
                                                           (inc (Integer/parseInt (nth (re-find #"^([0-9]+)-([0-9]+)$" %1) 2))))
                :else nil))
        (apply concat)
        (distinct)))))

(defn determine-which-posts-to-keep
  [posts res-count display-post-fn context]
  (let [messages-in-plain-text (map #(-> %1
                                       (clojure.string/split #"<>")
                                       (nth (if (or (:shitaraba? context) (:machi-bbs? context)) 4 3) "")
                                       (convert-message-in-post-into-plain-text))
                                    posts)
        references  (apply concat
                           (remove  nil? (map
                                           #(let [references (find-references-in-post (:message %1) res-count)]
                                              (if references
                                                (for [reference references]
                                                  {:from (:index %1) :to reference})
                                                nil))
                                           (map #(do {:index %1 :message %2})
                                                (range 1 (inc (count posts)))
                                                messages-in-plain-text))))
        posts-to-keep (loop [posts-to-keep (remove #(not (display-post-fn %1)) (range 1 (inc (count posts))))]
                        (let [to-list (map :to
                                           (apply concat
                                             (for [post posts-to-keep]
                                               (remove #(not (= (:from %1) post)) references))))
                              from-list (map :from
                                           (apply concat
                                             (for [post posts-to-keep]
                                               (remove #(not (= (:to %1) post)) references))))
                              result (distinct (concat posts-to-keep to-list from-list))]
                          ; (timbre/debug "posts-to-keep" posts-to-keep)
                          ; (timbre/debug to-list)
                          ; (timbre/debug from-list)
                          ; (timbre/debug result)
                          (if (= (set result) (set posts-to-keep))
                           result
                            (recur result))
                          ))]

   ; (timbre/debug posts-to-keep)
    posts-to-keep))

(defn process-thread-in-dat-format
  [dat-content context]
  ; (timbre/debug "process-thread-in-dat-format: Calling convert-post-in-dat-to-html.")
  (let
    [start-time (System/nanoTime)
     {:keys [service server board thread thread-url]} context
     posts         (clojure.string/split dat-content #"\n")
     title         (nth (clojure.string/split (first posts) #"<>")
                        (if (or (:shitaraba? context) (:machi-bbs? context)) 5 4)
                        nil)
     title         (if title
                     (unescape-html-entities title)
                     (:title (db/get-thread-info service board thread)))
     res-count     (if (or (:shitaraba? context) (:machi-bbs? context))
                     (Integer/parseInt (first (clojure.string/split (last posts) #"<>")))
                     (count posts))
     ; _             (timbre/debug "res-count:" res-count)
     display-post-fn (generate-display-post-fn context res-count)
     posts-in-html (if (:count-posts context)
                     nil
                     (doall
                       (cp/pmap
                         number-of-threads-for-thread-content
                         convert-post-in-dat-to-html
                         posts
                         (range 1 (inc res-count))
                         (repeat (-> context
                                   (assoc :thread-title    title)
                                   (assoc :res-count       res-count)
                                   (assoc :display-post-fn display-post-fn)
                                   (assoc :posts-to-keep   (determine-which-posts-to-keep posts res-count display-post-fn context))
                                   )))))]
    ; (timbre/debug "process-thread-in-dat-format: Calls to convert-post-in-dat-to-html are done.")
    (db/update-thread-title service server board thread title)
    (db/update-thread-res-count service server board thread res-count)
    (timbre/info (str "    Processed DAT file (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
    posts-in-html))

(defn get-posts-in-current-dat-file
  [context]
  ; (timbre/debug "get-posts-in-current-dat-file")
  (let [{:keys [service server board thread]} context
        dat-file-in-database  (db/get-dat-file service board thread)
        dat-content           (if (and (or (net-server? server) (bbspink-server? server)) use-net-api)
                                (if (and dat-file-in-database
                                         (:active? context))
                                  (update-dat-file-through-net-api context)
                                  (download-entire-dat-file-through-net-api context))
                                (if (and dat-file-in-database
                                         (:active? context))
                                  (update-dat-file context)
                                  (download-entire-current-dat-file context)))
        res-count             (count (clojure.string/split dat-content #"\n"))

        thread-info           (db/get-thread-info service board thread)
        retry?                (and dat-file-in-database thread-info (> (:res-count thread-info) res-count))
        ;_                     (timbre/debug "get-posts-in-current-dat-file: retry?" retry?)
        dat-content           (if retry?
                                (if (and (net-server? server) use-net-api)
                                  (download-entire-dat-file-through-net-api context)
                                  (download-entire-current-dat-file context))
                                dat-content)]
    (process-thread-in-dat-format dat-content context)))

(defn get-posts-in-archived-dat-file
  [context]
  ; (timbre/debug "get-posts-in-archived-dat-file")
  (let [{:keys [service server board thread]} context
        dat-file-in-database  (db/get-dat-file service board thread)
        dat-content           (new String
                                   (if (and (net-server? server) use-net-api)
                                     (download-entire-dat-file-through-net-api context)
                                     (download-entire-archived-dat-file context))
                                   (get-encoding-for-get-method server))]
    (process-thread-in-dat-format dat-content context)))

(defn convert-post-from-read-cgi-to-html　[post context]
  (let [res-count (:res-count context)
        thread-url (:thread-url context)
        {:keys [service server board thread]} context

        items         (clojure.string/split post #"<dd>")
        subitems      (clojure.string/split (first items) #"( ：(<a href=\"mailto:.*\">|<a href=\"/cdn-cgi/l/email-protection#[a-z0-9]+\">|<font color=green>)<b>)|(</b>(</font>|</a>))：")

        split-name    (clojure.string/split (nth subitems 1 "") #" *</?b> *")
        special-color (second (re-find #"<font color=#([0-9A-Fa-f]+)>" (first split-name)))
        handle-or-cap (remove-ng-words-from-cap (unescape-html-entities (remove-html-tags (nth split-name 0 ""))))
        tripcode                                (unescape-html-entities (remove-html-tags (nth split-name 1 "")))
        cap           (remove-ng-words-from-cap (unescape-html-entities (remove-html-tags (nth split-name 2 ""))))

        index         (Integer/parseInt (first subitems))
        email         (unescape-html-entities (remove-html-tags (second (re-find #"<a href=\"mailto:([^\"]*)\"><b>" (first items)))))
        etc           (unescape-html-entities (remove-html-tags (nth subitems 2 "")))
        message       (convert-message-in-post-into-plain-text (second items))]
    ((:convert-raw-post-to-html context) index handle-or-cap tripcode cap email etc message special-color context)))

(defn process-thread-in-html
  [html-body context]
  (if (not (valid-thread-in-html? html-body))
    (throw (Exception.)))
  (let [start-time (System/nanoTime)
        {:keys [service server board thread]} context
        title (second (re-find #"<h1 [^>]+>(.*)</h1>" html-body))
        title (if title (unescape-html-entities title) title)
        thread-body (second (re-find #"(?s)<dl class=\"thread\"[^>]*>\n<dt>(.*)<br><br>\n</dl>" html-body))
        posts (clojure.string/split thread-body #"<br><br>\n<dt>")
        res-count (count posts)
        ; _ (timbre/debug "res-count:" res-count)
        processed-posts (if (:count-posts context)
                          (repeat res-count "")
                          (doall
                            (cp/pmap
                              number-of-threads-for-thread-content
                              convert-post-from-read-cgi-to-html
                              posts
                              (repeat (-> context
                                        (assoc :thread-title    title)
                                        (assoc :res-count       res-count)
                                        (assoc :display-post-fn (generate-display-post-fn context res-count)))))))]
    (db/update-thread-title service server board thread title)
    (db/update-thread-res-count service server board thread res-count)
    (if (re-find #"<div[^>]*>■ このスレッドは過去ログ倉庫に格納されています</div>" html-body)
      (db/mark-thread-as-archived service server board thread))
    (timbre/info (str "    Processed html page (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
    processed-posts))

(defn get-posts-through-read-cgi
  [context]
  (let [{:keys [service server original-server board thread thread-url]} context
        source-url (str (create-thread-url server board thread) "?v=pc")
        options    (get-options-for-get-method thread-url)
        options    (if (net-url? thread-url)
                     (assoc options
                            :headers
                            (assoc (:headers options)
                                   "User-Agent"
                                   (ring.util.response/get-header noir.request/*request* "user-agent")))
                     options)
        ; _ (timbre/debug (str options))
        response-first-try (clj-http.client/get source-url options)
        retry? (re-find #"error 3001</title>" (:body response-first-try))
        server (if retry? original-server server)
        response (if retry?
                   (clj-http.client/get (create-thread-url server board thread) options)
                   response-first-try)]
    (if (or (not (= (:status response) 200))
            (re-find #"error 3001</title>" (:body response))
            (re-find #"<center><font color=red>■ このスレッドは過去ログ倉庫に格納されています</font></center>" (:body response))
            (< 1 (count (:trace-redirects response)))
            (not (valid-thread-in-html? (:body response))))
      (throw (Exception. "Not a valid html file."))
      (let [processed-thread (process-thread-in-html (:body response) context)
            thread-in-html   (db/get-thread-in-html service board thread)
            res-count        (count processed-thread)]
        (if thread-in-html
          (db/update-thread-in-html service board thread (:body response) res-count)
          (db/add-thread-in-html {:user_id    (:id (session/get :user))
                                  :service    service
                                  :server     server
                                  :board      board
                                  :thread_no  (Long/parseLong thread)
                                  :content    (:body response)
                                  :res_count  res-count
                                  :source_url source-url}))
        processed-thread))))

(defn convert-post-in-json-to-html　[post context]
  (let [res-count (:res-count context)
        thread-url (:thread-url context)
        {:keys [service server board thread]} context

        split-name    (re-find #"^(([^◆★]+)?)((◆[A-Za-z0-9./+]+)?)[ \t]*(([^★]+★[^★]*)?)[ \t]*$" (nth post 1 ""))
        handle-or-cap (remove-ng-words-from-cap (unescape-html-entities (remove-html-tags (nth split-name 1 ""))))
        tripcode                                (unescape-html-entities (remove-html-tags (nth split-name 3 "")))
        cap           (remove-ng-words-from-cap (unescape-html-entities (remove-html-tags (nth split-name 5 ""))))
        special-color (second (re-find #"<font color=#([0-9A-Fa-f]+)>" (nth post 1 "")))
        ; _             (timbre/debug "split-name:   " split-name)
        ; _             (timbre/debug "handle-or-cap:" split-name)
        ; _             (timbre/debug "tripcode:     " tripcode)
        ; _             (timbre/debug "cap:          " cap)

        index         (nth post 0 1)
        email         (unescape-html-entities (remove-html-tags (nth post 2 "")))
        id            (nth post 4 nil)
        be            (nth post 5 nil)
        _             (if (zero? (nth post 3 0)) ; http://anago.2ch.sc/test/read.cgi/software/1421383668/555-556
                        (throw (Exception. "Invalid timestamp.")))
        timestamp     (clj-time.core/to-time-zone
                          (clj-time.coerce/from-long (* (nth post 3 0) 1000))
                          (clj-time.core/time-zone-for-offset +9))
        etc           (str
                        (clj-time.format/unparse (clj-time.format/formatter-local "yyyy/MM/dd(") timestamp)
                        (case (clj-time.core/day-of-week timestamp)
                          1 "月"
                          2 "火"
                          3 "水"
                          4 "木"
                          5 "金"
                          6 "土"
                            "日")
                        (clj-time.format/unparse (clj-time.format/formatter-local ") HH:mm:ss") timestamp))
        etc           (if (pos? (count id)) (str etc " ID:" id) etc)
        etc           (if (pos? (count be)) (str etc " BE:" be) etc)
        etc           (unescape-html-entities (remove-html-tags etc))
        message       (convert-message-in-post-into-plain-text (nth post 6 ""))]
    ((:convert-raw-post-to-html context) index handle-or-cap tripcode cap email etc message special-color context)))

(defn process-thread-in-json
  [response-body-clj context]
  (let [start-time (System/nanoTime)
        {:keys [service server board thread-no]} context

        res-count (get response-body-clj "total_count")
        posts     (get response-body-clj "comments")
        title     (unescape-html-entities (nth (get response-body-clj "thread") 5 ""))
        ; _ (timbre/debug (str "res-count: " res-count))
        ; _ (timbre/debug (str "title: " title))
        ; _ (throw (Exception. "Not a valid JSON response."))

        processed-posts (if (:count-posts context)
                          (repeat res-count "")
                          (doall
                            (cp/pmap
                              number-of-threads-for-thread-content
                              convert-post-in-json-to-html
                              posts
                              (repeat (-> context
                                        (assoc :thread-title    title)
                                        (assoc :res-count       res-count)
                                        (assoc :display-post-fn (generate-display-post-fn context res-count)))))))]
    (db/update-thread-title service server board thread-no title)
    (db/update-thread-res-count service server board thread-no res-count)
    (timbre/info (str "    Processed thread in JSON (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
    processed-posts))

(defn get-posts-in-json
  [context]
  (let [start-time (System/nanoTime)
        {:keys [service server original-server board thread thread-url]} context
        _ (if (not (or (net-server? server) (bbspink-server? server))) (throw (Exception. "JSON is not supported.")))

        source-url (thread-url-to-json-url (create-thread-url server board thread))
        options    (get-options-for-get-method thread-url)
        options    (if (net-url? thread-url)
                     (assoc options
                            :headers
                            (assoc (:headers options)
                                   "User-Agent"
                                   (ring.util.response/get-header noir.request/*request* "user-agent")))
                     options)
        ; _ (timbre/debug (str options))
        response-first-try (clj-http.client/get source-url options)
        retry? (or (not (= (:status response-first-try) 200))
                   (< 1 (count (:trace-redirects response-first-try)))
                   (zero? (count (:body response-first-try))))
        source-url (thread-url-to-json-url (create-thread-url (if retry? original-server server) board thread))
        response (if retry?
                   (clj-http.client/get source-url options)
                   response-first-try)
        ; response-body-clj (json/read-str (:body response))
        ; response-body-clj (cheshire.core/parse-string (:body response))
        response-body-clj (clj-json.core/parse-string (:body response))
        ; _ (timbre/debug (str response-body-clj))
        ]
    (timbre/info (str "    Downloaded thread in JSON (" (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms)."))
    (if (or (not (= (:status response) 200))
       (< 1 (count (:trace-redirects response)))
            (zero? (count (:body response))))
      (throw (Exception. "Not a valid JSON response."))
      (let [res-count        (get response-body-clj "total_count")
            processed-thread (process-thread-in-json response-body-clj context)
            thread-in-json   (db/get-thread-in-json service board thread)]
        (if thread-in-json
          (db/update-thread-in-json service board thread (:body response) res-count)
          (db/add-thread-in-json {:user_id    (:id (session/get :user))
                                  :service    service
                                  :server     server
                                  :board      board
                                  :thread_no  (Long/parseLong thread)
                                  :content    (:body response)
                                  :res_count  res-count
                                  :source_url source-url}))
        processed-thread))))

(defn get-posts-from-database
  "Returns posts in the database. Returns nil if the thread is not saved in the database."
  [context]
  ; (timbre/debug "get-posts-from-database")
  (try
    (let [{:keys [service server board thread]} context
          dat-file       (db/get-dat-file service board thread)
          thread-in-html (db/get-thread-in-html service board thread)
          thread-in-json (db/get-thread-in-json service board thread)]
      ; Return the longest version.
      (cond (and dat-file
                 (or (nil? thread-in-html)
                     (nil? (:res-count thread-in-html))
                     (and (:res-count dat-file)
                          (:res-count thread-in-html)
                          (>= (:res-count dat-file) (:res-count thread-in-html))))
                 (or (nil? thread-in-json)
                     (nil? (:res-count thread-in-json))
                     (and (:res-count dat-file)
                          (:res-count thread-in-json)
                          (>= (:res-count dat-file) (:res-count thread-in-json)))))
            (process-thread-in-dat-format (new String (:content dat-file)
                                               (get-encoding-for-get-method (:server dat-file)))
                                          context)

            (and thread-in-html
                 (or (nil? thread-in-json)
                     (nil? (:res-count thread-in-json))
                     (and (:res-count thread-in-html)
                          (:res-count thread-in-json)
                          (>= (:res-count thread-in-html) (:res-count thread-in-json)))))
            (process-thread-in-html (:content thread-in-html) context)

            thread-in-json
            (process-thread-in-json (clj-json.core/parse-string (:content thread-in-json)) context)

            :else
            nil))

    (catch Throwable t
      (print-stack-trace t)
      nil)))

