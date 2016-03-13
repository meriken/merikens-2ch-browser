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



(ns merikens-2ch-browser.url
  (:require [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.cursive :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [clojure.stacktrace :refer [print-stack-trace]]
            [taoensso.timbre :refer [log]]
            [clj-time.core]
            [clj-time.coerce]
            [clj-http.client]))



(defn shitaraba-server?
  ^Boolean
[^String server]
  (boolean (or (re-find #"^jbbs\.shitaraba\.net$" server)
               (re-find #"^jbbs\.livedoor\.jp$" server))))

(defn machi-bbs-server?
  ^Boolean
[^String server]
  (boolean (re-find #"\.machi\.to$" server)))

(defn net-server?
  ^Boolean
[^String server]
  (boolean (re-find #"\.2ch\.net$" server)))

(defn sc-server?
  ^Boolean
[^String server]
  (boolean (re-find #"\.2ch\.sc$" server)))

(defn open-server?
  ^Boolean
[^String server]
  (boolean (re-find #"\.open2ch\.net$" server)))

(defn bbspink-server?
  ^Boolean
[^String server]
  (boolean (re-find #"\.bbspink\.com$" server)))

(defn shingetsu-server?
  ^Boolean
[^String server]
  (boolean (or (re-find #"^ygg\.(io|ch)$" server)
               (re-find #"^shingetsu\.ygch\.net$" server))))

(defn sc-url?
  ^Boolean
[^String url]
  (boolean (re-find #"^http://[a-z0-9]+\.2ch\.sc/" url)))

(defn net-url?
  ^Boolean
[^String url]
  (boolean (re-find #"^http://[a-z0-9]+\.2ch\.net/" url)))

(defn open-url?
  ^Boolean
[^String url]
  (boolean (re-find #"^http://[a-z0-9]+\.open2ch\.net/" url)))

(defn bbspink-url?
  ^Boolean
[^String url]
  (boolean (re-find #"^http://[a-z0-9]+\.bbspink\.com/" url)))

(defn shitaraba-url?
  ^Boolean
[^String url]
  (boolean (or (re-find #"^http://jbbs\.shitaraba\.net/" url)
               (re-find #"^http://jbbs\.livedoor\.jp/" url))))

(defn machi-bbs-url?
  ^Boolean
[url]
  (boolean (re-find #"^http://[a-z0-9]+\.machi\.to/" url)))

(defn server-to-service
  ^String
[^String server]
  (if (or (= server "jbbs.livedoor.jp") (= server "jbbs.shitaraba.net"))
    "jbbs.shitaraba.net"
    (clojure.string/replace server #"^[0-9a-z]+\." "")))

(defn split-board-url
  ^clojure.lang.IPersistentMap
[^String board-url]
  (try
    (let
      [parts (if (shitaraba-url? board-url)
               (re-find #"^ *https?://([a-z0-9.]+)/([a-z0-9]+/[0-9]+)/? *$" board-url)
               (re-find #"^ *https?://([a-z0-9.-]+)/([a-zA-Z0-9_]+)/? *$" board-url))]
      (if parts
        (let [original-server (nth parts 1)
              board           (nth parts 2)
              service         (server-to-service original-server)
              current-server  (:server (db/get-board-info service board))
              server          (if current-server current-server original-server)]
          {:service service
           :server  server
           :board   board})
        nil))
    (catch Exception _
      nil)))

(defn split-kako-thread-url
  ^clojure.lang.IPersistentMap
[^String thread-url]
  (try
    (let [parts (re-find #"^ *http://([a-z0-9.]+)/([a-zA-Z0-9.]+)/kako/[0-9/]+/([0-9]+)\.html *$" thread-url)]
      (if parts
        (let [original-server (nth parts 1)
              board           (nth parts 2)
              service         (server-to-service original-server)
              current-server  (:server (db/get-board-info service board))
              server          (if (and current-server
                                       (nil? rest)
                                       (not (some #{:original-server} rest)))
                                current-server original-server)]
          {:service   service
           :server    server
           :current-server  current-server
           :original-server original-server
           :board     board
           :thread    (nth parts 3)
           :thread-no (nth parts 3)})
        nil))
    (catch Throwable _
      nil)))

(defn split-thread-url
  ^clojure.lang.IPersistentMap
[^String thread-url
 & rest]
  (if (re-find #"/kako/[0-9/]+\.html *$" thread-url)
    (split-kako-thread-url thread-url)
    (try
      (let
        [parts (cond
                 (shitaraba-url? thread-url)
                 (re-find #"^ *https?://(jbbs\.shitaraba\.net)/(bbs|bbs/lite)/read\.cgi/([a-zA-Z0-9.]+(/[0-9]+))/([0-9]+)(/(((l[0-9]+)|([0-9]+n?)|([0-9]+n?-([0-9]+)?n?)))?)? *$" thread-url)

                 (machi-bbs-url? thread-url)
                 (re-find #"^ *https?://([a-z0-9.]+)/(bbs)/read\.cgi/([a-zA-Z0-9.]+())/([0-9]+)(/((((l[0-9]+)n?|([0-9]+n?)|([0-9]+n?-([0-9]+)?n?))[,+])*((l[0-9]+n?)|([0-9]+n?)|([0-9]+n?-([0-9]+)?n?)))?)? *$" thread-url)

                 :else
                 (re-find #"^ *https?://([a-z0-9.-]+)/(test)/read\.cgi/([a-zA-Z0-9.]+())/([0-9]+)(/((((l[0-9]+)n?|([0-9]+n?)|([0-9]+n?-([0-9]+)?n?))[,+])*((l[0-9]+n?)|([0-9]+n?)|([0-9]+n?-([0-9]+)?n?)))?)? *$" thread-url)
                 )]
        (if parts
          (let [original-server (nth parts 1)
                board           (nth parts 3)
                service         (server-to-service original-server)
                current-server  (:server (db/get-board-info service board))
                server          (if (and current-server
                                         (nil? rest)
                                         (not (some #{:original-server} rest)))
                                  current-server original-server)]
            {:service   service
             :server    server
             :current-server  current-server
             :original-server original-server
             :board     board
             :thread    (nth parts 5)
             :thread-no (nth parts 5)
             :posts     (nth parts 7)
             :options   (nth parts 7)})
          nil))
      (catch Throwable _
        nil))))

(defn thread-url-to-dat-url
  ^String
[^String thread-url]
  (let [{:keys [server board thread]} (split-thread-url thread-url)]
    (cond
      (shitaraba-url? thread-url)
      (str "http://" server "/bbs/rawmode.cgi/" board "/" thread "/")

      (machi-bbs-url? thread-url)
      (str "http://" server "/bbs/offlaw.cgi/2/" board "/" thread "/")

      :else
      (str "http://" server "/" board "/dat/" thread ".dat"))))

; ex: http://awabi.2ch.net/test/offlaw2.so?shiro=kuma&bbs=english&key=1345426022&sid=ERROR
(comment defn thread-url-to-offlaw2-dat-url
  ^String
[^String thread-url]
  (let [{:keys [original-server board thread]} (split-thread-url thread-url)]
    (str "http://" original-server "/test/offlaw2.so?shiro=kuma&bbs=" board "&key=" thread "&sid=ERROR")))

(defn thread-url-to-rokka-dat-url
  ^String
[^String thread-url]
  (let [{:keys [service original-server board thread]} (split-thread-url thread-url)
        host-name (clojure.string/replace original-server #"\..*$" "")]
    (str "http://rokka." service "/" host-name "/" board "/" thread "/?sid=" (get-ronin-session-id))))

(defn thread-url-to-json-url
  ^String
[^String thread-url]
  (let [{:keys [server original-server board thread-no]} (split-thread-url thread-url)
        host-name (clojure.string/replace original-server #"\..*$" "")]
    (cond
      (or (net-server? server) (bbspink-server? server))
      (str "http://itest.2ch.net/public/newapi/client.php"
           "?subdomain=" host-name
           "&board=" board
           "&dat=" thread-no)

      :else
      nil)))

(defn get-encoding-for-get-method
  ^String
[^String server]
  ; (log :debug url)
  (cond
    (shitaraba-server? server)
    "EUC-JP"
    :else
    "Windows-31J"))

(defn get-encoding-for-post-method
  ^String
[^String server]
  ; (log :debug url)
  (cond
    (shitaraba-server? server)
    "EUC-JP"
    :else
    "Windows-31J"))

(defn get-options-for-get-method
  ^clojure.lang.IPersistentMap
[^String url]
  ; (log :debug url)
  (let [headers {"User-Agent"    user-agent
                 "Cache-Control" "no-cache"}
        options {:headers        headers
                 :socket-timeout 60000
                 :conn-timeout   180000}
        options (merge options (proxy-server url :get))]
    (cond
      (re-find #"^http://[a-z0-9]+\.2ch\.(net|sc)/" url)
      (assoc options :as "Windows-31J")

      (re-find #"^http://jbbs\.shitaraba\.net/" url)
      (assoc options :as "EUC-JP")

      (re-find #"^http://([a-z0-9]+\.)?open2ch\.net/" url)
      (-> options
        (assoc :decode-body-headers true)
        (assoc :as :auto))

      :else
      (assoc options :as "Windows-31J"))))

(defn create-thread-url
  ^String
[^String server
 ^String board
 thread-no]
  (cond
    (= server "jbbs.shitaraba.net")
    (str "http://" server "/bbs/read.cgi/" board "/" thread-no "/")

    (re-find #"^[a-z]+\.machi\.to$" server)
    (str "http://" server "/bbs/read.cgi/" board "/" thread-no "/")

    :else
    (str "http://" server "/test/read.cgi/" board "/" thread-no "/")))

(defn create-board-url
  ^String
[^String server
 ^String board]
  (str "http://" server "/" board "/"))

; ex: http://pc.2ch.net/test/read.cgi/os/1008379128/
;     http://pc.2ch.net/os/kako/1008/10083/1008379128.dat.gz
(defn thread-url-to-kako-dat-url
  [thread-url add-gz]
  (let [{:keys [board thread]} (split-thread-url thread-url)
        server (second (re-find #"^http://([a-z0-9.]+)/" thread-url))]
    (if (= (count thread) 10)
      (str "http://" server "/" board "/kako/" (subs thread 0 4) "/" (subs thread 0 5) "/" thread ".dat" (if add-gz ".gz" ""))
      (str "http://" server "/" board "/kako/" (subs thread 0 3) "/" thread ".dat" (if add-gz ".gz" "")))))

(defn to-url-encoded-string [s & [encoding]]
  ; (log :debug s)
  (let [encoding (or encoding "UTF-8")]
    (apply str (for [ch (.getBytes (str s) encoding)] (format "%%%02X" ch)))))

(defn get-subject-txt
  [^String board-url
   ^Boolean refresh]
  ; (log :debug "get-subject-txt:" board-url)
  (try
    (let [{:keys [server service board]} (split-board-url board-url)
          board-info (db/get-board-info service board)
          subject-txt-url (str board-url "subject.txt")
          now (clj-time.coerce/to-long (clj-time.core/now))]
      (try
        (if (and (:subject-txt board-info)
                 (or (not refresh)
                     (<= (- now (get-time (:time-subject-txt-retrieved board-info)))
                         wait-time-for-downloading-subject-txt)))
          (do
            ; (log :info "Retrieved from cache:" subject-txt-url)
            (:subject-txt board-info))
          (let [response (clj-http.client/get subject-txt-url (get-options-for-get-method board-url))]
            (if (or (not (= (:status response) 200))
                    (< 1 (count (:trace-redirects response))))
              (do
                ; (log :info "Retrieved from cache:" subject-txt-url)
                (:subject-txt board-info))
              (do
                (db/update-subject-txt service server board (:body response))
                ; (log :info "Downloaded:" subject-txt-url
                ;              now
                ;              (.getTime (:time-subject-txt-retrieved board-info))
                ;              (- now (.getTime (:time-subject-txt-retrieved board-info)))
                ;              wait-time-for-downloading-subject-txt)
                (:body response)))))
        (catch Throwable _
          ; (log :debug "get-subject-txt: Unexpected Exception:" (str t) board-url)
          ; (log :debug "get-subject-txt: Returning (:subject-txt board-info)")
          (:subject-txt board-info))))
    (catch Throwable _
      ; (log :debug "get-subject-txt: Unexpected Exception:" (str t) board-url)
      ; (log :debug "get-subject-txt: Returning nil")
      nil)))

(defn is-thread-active?
  [server board thread-no]
  ; (log :debug "    is-thread-active?:" server board thread-no)
  (try
    (let [board-url (create-board-url server board)
          subject-txt (get-subject-txt board-url true)
          thread-no-list (map #(clojure.string/replace %1 #"\.(dat<>|cgi,).*$" "")
                              (clojure.string/split subject-txt #"\n"))
          ; _ (log :debug "subject-txt:" subject-txt)
          ; _ (log :debug "    thread-no-list:" (count thread-no-list))
          result      (boolean (or (nil? subject-txt) (some #(= %1 (str thread-no)) thread-no-list)))]
      ; (log :debug "    is-thread-active?:" result)
      result)
    (catch Throwable _
      ; (log :error "    is-thread-active?: Unexpected Exception:" (str t) server board thread-no)
      ; (print-stack-trace t)
      true)))



(defn check-dat-file-availability
  [thread-url]
  (if (and (not download-dat-files-from-2ch-net-and-bbspink-com)
           (or (net-url? thread-url) (bbspink-url? thread-url)))
    (throw (Exception. "DAT files are not available."))))

(defn check-thread-in-html-availability
  [thread-url]
  (if (or (and (not download-threads-in-html-from-2ch-net-and-bbspink-com)
               (or (net-url? thread-url) (bbspink-url? thread-url)))
          (not (or (net-url? thread-url) (bbspink-url? thread-url))))
          (throw (Exception. "Threads in HTML are not available."))))

(defn check-thread-in-json-availability
  [thread-url]
  (if (or (and (not download-threads-in-json-from-2ch-net-and-bbspink-com)
               (or (net-url? thread-url) (bbspink-url? thread-url)))
          (not (or (net-url? thread-url) (bbspink-url? thread-url))))
          (throw (Exception. "Threads in JSON are not available."))))



(defn get-menu-url
  [service]
  (case service
    "2ch.sc" "http://menu.2ch.sc/bbsmenu.html"
    "2ch.net" "http://menu.2ch.net/bbsmenu.html"
    "open2ch.net" "http://open2ch.net/menu/pc_menu.html"
    "machi.to" "http://kita.jikkyo.org/cbm/cbm.cgi/m0.99/bbsmenu.htlm"
    "ygg.ch" "http://ygg.ch/bbsmenu.html"))
