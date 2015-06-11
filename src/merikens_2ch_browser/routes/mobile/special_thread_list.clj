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
            [taoensso.timbre :refer [log]]
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



(defn mobile-convert-special-thread-list-item-to-html
  [item index context board-info-map]
  ; (log :debug item)
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

          ; created (-> thread-no
          ;           (* 1000)
          ;           (clj-time.coerce/from-long)
          ;           (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
          ; time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
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
      (log :debug "mobile-convert-special-thread-list-item-to-html: Exception caught:" (str t))
      (log :debug "item:" item)
      (log :debug "index:" index)
      (log :debug "context:" context)
      (print-stack-trace t)
      nil)))

(defn mobile-get-special-thread-list
  [items context title]
  (log :debug "mobile-get-special-thread-list:" (count items))
  (try
    (increment-http-request-count)
    (.setPriority (java.lang.Thread/currentThread) web-sever-thread-priority)
    (log :info "Preparing thread list...")
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
                                (catch Throwable _ 0))
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
      (log :info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
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
