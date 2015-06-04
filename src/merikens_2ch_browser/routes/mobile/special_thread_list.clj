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



(ns merikens-2ch-browser.routes.mobile.special-thread-list
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [ring.handler.dump]
            [ring.util.codec :refer [url-encode url-decode]]
            [compojure.core :refer :all]
            [noir.response :refer [redirect]]
            [noir.request]
            [noir.validation :refer [rule errors? has-value? on-error]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]
            [hiccup.form :refer :all]
            [hiccup.element :refer :all]
            [hiccup.util :refer [escape-html]]
            [taoensso.timbre :as timbre :refer [log]]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.routes.thread-list :refer [add-highlights-to-thread-title-and-escape-html
                                                             update-res-count-for-board
                                                             update-res-count-for-multiple-threads]]))



(defn mobile-thread-list-item-to-html
  [item board-url bookmark-map refresh]
  (let [dat (second (re-find #"^([0-9]+\.(dat|cgi))(<>|,)" item))
        created (-> dat
                  (clojure.string/replace #"\.(dat|cgi)$" "")
                  (Long/parseLong )
                  (* 1000)
                  (clj-time.coerce/from-long)
                  (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
        time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
        after-dat (clojure.string/replace item #"^[0-9]+\.(dat|cgi)(<>|,)" "")
        res-count (Integer/parseInt (clojure.string/replace (clojure.string/replace after-dat #"^.* ?\(" "") #"\)$" ""))
        thread-id (random-string 16)
        board-url-parts (re-find #"^http://([a-z0-9.-]+)/(.*)/$" board-url)
        server     (nth board-url-parts 1)
        board      (nth board-url-parts 2)
        thread-no  (clojure.string/replace dat #"\.(dat|cgi)$" "")
        thread-url (create-thread-url server board thread-no)
        speed      (float (/ res-count (/ time-since-creation 60 60 24)))
        service    (server-to-service server)
        new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
        res-count-id (clojure.string/replace (str "post-count-" service "-" board "-" thread-no) #"[./]" "_")
        thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
        bookmark   (or ((keyword thread-no) bookmark-map)
                       (db/get-bookmark service board thread-no))
        ; thread-info (db/get-thread-info service board thread-no)
        new-thread? (nil? bookmark)
        new-res?   (and bookmark (> bookmark 0) (> (- res-count bookmark) 0))
        new-res-count (cond
                        (nil? bookmark) res-count
                        new-res? (- res-count bookmark)
                        (= res-count bookmark) 0
                        :else 0)
        title      (unescape-html-entities (clojure.string/replace after-dat #" *\([0-9]+\)$" ""))
        title      (remove-ng-words-from-thread-title title)
        real-title (if (sc-url? board-url) (clojure.string/replace title #"^★" "") title)]

    {:new-thread?   new-thread?
     :viewed?       (and bookmark (> bookmark 0))
     :new-res-count new-res-count
     :service       service
     :server        server
     :board         board
     :thread-no     thread-no
     :res-count     res-count
     :items-in-html [:li {:data-icon "false"}
                     [:a {:href (str "./mobile-thread?thread-url=" (to-url-encoded-string thread-url))
                          :onclick "removeDomCachesForThreads();"
                          :style "white-space: normal;"}
                      [:span
                       (if (or new-thread? (and bookmark (> bookmark 0)))
                         [:span {:class "ui-li-count"
                                 :style (if (or new-thread? (> new-res-count 0))
                                          "color: white; border: red; background: red; text-shadow: none;"
                                          "color: white; border: gray; background: gray; text-shadow: none;")}
                          (if new-thread? "新" new-res-count)])]
                      [:span {:style (cond
                                       new-thread?                   "color: red;"
                                       (> new-res-count 0)           ""
                                       (and bookmark (> bookmark 0)) ""
                                       :else                         "")}
                       (escape-html real-title)]
                      [:span {:style (if new-thread?
                                       "color: red; font-weight: normal;"
                                       "color: gray; font-weight: normal;")}
                       " (" res-count ")"]]]}))

(defn mobile-convert-special-thread-list-item-to-html
  [item index context board-info-map]
  ; (timbre/debug item)
  (try
    (let [{:keys [service server board thread-no]} item
          thread-info (db/get-thread-info service board thread-no)
          {:keys [title res-count]} thread-info
          title      (remove-ng-words-from-thread-title title)
          ; board-info {:server server :board-name "名無しの板"}
          ; board-info (db/get-board-info service board)
          board-info ((keyword (str service "#" board)) board-info-map)
          current-server (:server board-info)
          thread-url (create-thread-url (if current-server current-server server) board thread-no)
          ; board-name (str (:board-name board-info) (cond (= service "2ch.sc") "(sc)"
          ;                                                (= service "2ch.net") "(net)"
          ;                                                (= service "open2ch.net") "(op)"))

          created (-> thread-no
                    (* 1000)
                    (clj-time.coerce/from-long)
                    (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
          time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
          ; thread-id (random-string 16)
          ; speed      (float (/ (if (nil? res-count) 1 res-count) (/ time-since-creation 60 60 24)))
          bookmark      (db/get-bookmark service board thread-no)
          new-thread? (nil? bookmark)
          new-res?   (or new-thread? (and bookmark (> bookmark 0) (> (- res-count bookmark) 0)))
          new-res-count (cond
                          (nil? bookmark) res-count
                          new-res? (- res-count bookmark)
                          (= res-count bookmark) 0
                          :else "")
                          ; :else 0)
          ; new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
          ; res-count-id (clojure.string/replace (str "post-count-" service "-" board "-" thread-no) #"[./]" "_")
          ; thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
          ; regex-search-pattern (:regex-search-pattern context)
          ]

      {:new-thread?   new-thread?
       :viewed?       (and bookmark (> bookmark 0))
       :new-res-count new-res-count
       :service       service
       :server        server
       :board         board
       :thread-no     thread-no
       :res-count     res-count
       :items-in-html [:li {:data-icon "false"}
                       [:a {:href (str "./mobile-thread?thread-url=" (to-url-encoded-string thread-url))
                            :onclick "removeDomCachesForThreads();"
                            :style "white-space: normal;"}
                        [:span
                         (if (or new-thread? (and bookmark (> bookmark 0)))
                           [:span {:class "ui-li-count"
                                   :style (if (or new-thread? (and (number? new-res-count) (> new-res-count 0)))
                                            "color: white; border: red; background: red; text-shadow: none;"
                                            "color: white; border: gray; background: gray; text-shadow: none;")}
                            (if new-thread? "新" new-res-count)])]
                        [:span {:style (cond
                                         new-thread?                   "color: red;"
                                         (and (number? new-res-count) (> new-res-count 0)) ""
                                         (and bookmark (> bookmark 0)) ""
                                         :else                         "")}
                         (escape-html title)]
                        [:span {:style (if new-thread?
                                         "color: red; font-weight: normal;"
                                         "color: gray; font-weight: normal;")}
                         " (" res-count ")"]]]})

    (catch Throwable t
      (timbre/debug "mobile-convert-special-thread-list-item-to-html: Exception caught:" (str t))
      (timbre/debug "item:" item)
      (timbre/debug "index:" index)
      (timbre/debug "context:" context)
      (print-stack-trace t)
      nil)))

(defn mobile-get-special-thread-list
  [items context title]
  (timbre/debug "mobile-get-special-thread-list:" (count items))
  (try
    (increment-http-request-count)
    (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
    (timbre/info "Preparing thread list...")
    (let [start-time (System/nanoTime)
          _              (update-res-count-for-multiple-threads items true)
          board-info-map (create-board-info-map items)
          processed-items (remove nil? (map mobile-convert-special-thread-list-item-to-html
                                            items
                                            (range 1 (inc (count items)))
                                            (repeat context)
                                            (repeat board-info-map)))
          sorted-items (sort #(try
                                (cond
                                  (and      (:new-thread? %1)       (:new-thread? %2))  0
                                  (and      (:new-thread? %1)  (not (:new-thread? %2))) -1
                                  (and (not (:new-thread? %1))      (:new-thread? %2))  +1
                                  (and      (:viewed? %1)           (:viewed? %2))      (- (:new-res-count %2) (:new-res-count %1))
                                  (and      (:viewed? %1)      (not (:viewed? %2)))     -1
                                  (and (not (:viewed? %1))          (:viewed? %2))      +1
                                  :else                                                 0)
                                (catch Throwable t 0))
                             processed-items)
          result       (layout/mobile-login-required
                         [:div.board {:data-role "page" :data-dom-cache "true" :data-tap-toggle "false" :data-title title}
                          [:div {:role "main" :class "ui-content"}
                           [:ul {:data-role "listview"} (map :items-in-html sorted-items)]]
                          [:div {:data-role "footer" :data-position "fixed" :data-tap-toggle "false"}
                           [:div {:style "float:left"}
                            [:button {:class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-refresh"
                                      :onclick "location.reload(true);"}
                             "更新"]]
                           [:div {:style "float:right"}]]])]

      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
      (decrement-http-request-count)
      (timbre/info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
      result)
    (catch Throwable t
      (print-stack-trace t)
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
      (decrement-http-request-count)
      (html [:div.message-error "スレッド一覧の読み込みに失敗しました。" [:br] (bbn-check)]))))



(defn mobile-favorite-threads-page
  []
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (mobile-get-special-thread-list (db/get-favorite-threads)
                                    {}
                                    "お気にスレ")))

(defn mobile-recently-viewed-threads-page
  []
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (mobile-get-special-thread-list (db/get-recently-viewed-threads)
                                    {}
                                    "最近読んだスレ")))

(defn mobile-recently-posted-threads-page
  []
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (mobile-get-special-thread-list (db/get-recently-posted-threads)
                                    {}
                                    "書込履歴")))

(defroutes mobile-special-thread-list-routes
  (GET "/mobile-favorite-threads"
       []
       (mobile-favorite-threads-page))
  (GET "/mobile-recently-viewed-threads"
       []
       (mobile-recently-viewed-threads-page))
  (GET "/mobile-recently-posted-threads"
       []
       (mobile-recently-posted-threads-page)))
