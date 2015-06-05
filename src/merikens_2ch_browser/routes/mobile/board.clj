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



(ns merikens-2ch-browser.routes.mobile.board
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
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
            [merikens-2ch-browser.cursive :refer :all]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.routes.favorite-board :refer [count-new-posts-and-threads-in-board]]
            [com.climate.claypoole :as cp]))



(defn mobile-thread-list-item-to-html
  [item
   board-url
   bookmark-map
   _ ; refresh
   ]
  (let [dat (second (re-find #"^([0-9]+\.(dat|cgi))(<>|,)" item))
        ; created (-> dat
        ;           (clojure.string/replace #"\.(dat|cgi)$" "")
        ;           (Long/parseLong )
        ;           (* 1000)
        ;           (clj-time.coerce/from-long)
        ;           (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
        ; time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
        after-dat (clojure.string/replace item #"^[0-9]+\.(dat|cgi)(<>|,)" "")
        res-count (Integer/parseInt (clojure.string/replace (clojure.string/replace after-dat #"^.* ?\(" "") #"\)$" ""))
        board-url-parts (re-find #"^http://([a-z0-9.-]+)/(.*)/$" board-url)
        server     (nth board-url-parts 1)
        board      (nth board-url-parts 2)
        board      (if (shingetsu-server? server) (clojure.string/replace board #"_[0-9A-F]+$" "") board)
        thread-no  (clojure.string/replace dat #"\.(dat|cgi)$" "")
        thread-url (create-thread-url server board thread-no)
        ; speed      (float (/ res-count (/ time-since-creation 60 60 24)))
        service    (server-to-service server)
        ; new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
        ; res-count-id (clojure.string/replace (str "post-count-" service "-" board "-" thread-no) #"[./]" "_")
        ; thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
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

    (db/mark-thread-as-active service server board thread-no)
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

(defn mobile-board-page
  [board-url max-count refresh]
  (if (not (check-login))
    (html [:script "open('./mobile-login', '_self');"])
    (try
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
      (increment-http-request-count)
      (let [; start-time   (System/nanoTime)
            {:keys [service board]} (split-board-url board-url)
            board-info   (db/get-board-info service board)
            body         (get-subject-txt board-url true)
            _            (if (nil? body) (throw (Exception. "Failed to obtain subject.txt.")))
            ; subject-txt-time-spent (* (- (System/nanoTime) start-time) 0.000001)
            board-name   (:board-name board-info)
            bookmark-map (apply merge (map #(hash-map (keyword (str (:thread-no %1))) (:bookmark %1))
                                           (db/get-bookmark-list-for-board service board)))
            all-lines    (clojure.string/split body #"\n")
            items        (cp/pmap
                           number-of-threads-for-thread-list
                           mobile-thread-list-item-to-html
                           (if (shitaraba-url? board-url) (drop-last all-lines) all-lines) ; Shitaraba sucks.
                           (repeat board-url)
                           (repeat bookmark-map)
                           (repeat refresh))
            sorted-items (sort #(cond
                                  (and      (:new-thread? %1)       (:new-thread? %2))  0
                                  (and      (:new-thread? %1)  (not (:new-thread? %2))) -1
                                  (and (not (:new-thread? %1))      (:new-thread? %2))  +1
                                  (and      (:viewed? %1)           (:viewed? %2))      (- (:new-res-count %2) (:new-res-count %1))
                                  (and      (:viewed? %1)      (not (:viewed? %2)))     -1
                                  (and (not (:viewed? %1))          (:viewed? %2))      +1
                                  :else                                                 0)
                               items)
            max-count       (try (Integer/parseInt max-count) (catch Throwable _ nil))
            filtered-items  (if max-count (take max-count sorted-items) sorted-items)
            page-url-base   (str "./mobile-board?board-url=" (to-url-encoded-string board-url))
            select-id       (random-element-id)
            result       (layout/mobile-login-required
                           [:div.board {:data-role "page" :data-dom-cache "true" :data-tap-toggle "false" :data-title board-name}
                            ; [:div {:data-role "header" :data-position "fixed"}　
                            ;  (link-to {:data-role "button" :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-back" :data-rel "back"}
                            ;         "#"
                            ;         "戻る")
                            ;  [:h1 (escape-html board-name)]
                            ;  (link-to {:data-role "button" :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-bars" :data-direction "reverse"}
                            ;         "/mobile-main"
                            ;         "目次")]
                            [:div {:role "main" :class "ui-content"}
                             [:ul {:data-role "listview"} (map :items-in-html filtered-items)]]
                            [:div {:data-role "footer" :data-position "fixed" :data-tap-toggle "false"}
                             [:div {:style "float:left"}
                             [:button {:class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-refresh"
                                       :onclick "location.reload(true);"}
                              "更新"]
                             (link-to {:data-role "button"
                                       :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-edit"
                                       ; :onclick "window.location.replace($(this).attr('href')); return false;"
                                       }
                                      (str "./mobile-post"
                                           "?thread-url="  (to-url-encoded-string "")
                                           "&thread-title="  (to-url-encoded-string "")
                                           "&handle="  (to-url-encoded-string "")
                                           "&email="  (to-url-encoded-string "")
                                           "&message="  (to-url-encoded-string "")
                                           "&board-url="  (to-url-encoded-string board-url)
                                           "&board-name="  (to-url-encoded-string ""))
                                            "スレ立て")]
                           [:div {:style "float:right"}
                             [:select {:id select-id
                                       :name "max-count"
                                       :data-native-menu "false"
                                       :data-mini "true"
                                       :data-inline "true"
                                       :data-iconpos "noicon"}
                              [:option {:value (str page-url-base "&max-count=50")} "50"]
                              [:option {:value (str page-url-base "&max-count=100")} "100"]
                              [:option {:value (str page-url-base "&max-count=200")} "200"]
                              [:option {:value      page-url-base                  } "全て"]]]]
                            [:script
                             "$('#" select-id "').change(function () {"
                             ; "    $('body').pagecontainer('change', $(this).val());"
                             "window.location.replace($(this).val());"
                             "});"
                             "$(document).on( 'pageshow', function( event, ui){"
                             "var myselect = $('#" select-id "');"
                             "if (myselect.length > 0) {"
                             "myselect[0].selectedIndex = " (cond (= max-count 50)  0
                                                                  (= max-count 100) 1
                                                                  (= max-count 200) 2
                                                                  :else             3) ";"
                             "myselect.selectmenu('refresh');"
                             "}"
                             "});"
                             ]])]

        (doall (map #(let [{:keys [service server board thread-no res-count new-thread?]} %1]
                       (if new-thread?
                          (db/update-bookmark service board thread-no 0))
                        (db/update-thread-res-count service server board thread-no res-count))
                    filtered-items))

        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        result)
      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (print-stack-trace t)
        "スレッド一覧の取得に失敗しました。"))))



(defn mobile-favorite-board-list-item-to-html
  [favorite-board refresh]
  (let [{:keys [service board]} favorite-board
        {:keys [server board-name]} (db/get-board-info service board)
        board-name (if board-name board-name "(板名未設定)")
        board-name-plus (str board-name (cond (= service "2ch.sc") "[sc]"
                                              (= service "2ch.net") "[net]"
                                              (= service "open2ch.net") "[op]"))
        board-url (create-board-url server board)
        ; item-id (random-element-id)
        ; display-name (str board-name " [" service "]")
        board-counts-classes (clojure.string/replace (str "favorite-board-counts favorite-board-counts-" service "-" board) #"[./]" "_")]
    (list
      [:li {:data-icon "false"}
       [:a {:href (str "./mobile-board?board-url=" (to-url-encoded-string board-url) "&max-count=" default-maximum-count-for-mobile-board)
            :onclick "removeDomCachesForBoards();"}
        [:span {:class board-counts-classes}
         (if refresh
           (try
             (let [{:keys [new-threads new-posts]} (count-new-posts-and-threads-in-board board-url refresh)]
               (list [:span {:class (str "ui-li-count favorite-board-list new-threads" (if (= new-threads 0) " zero" ""))} new-threads]
                     [:span {:class (str "ui-li-count favorite-board-list new-posts"   (if (= new-posts   0) " zero" ""))} new-posts  ]))
             (catch Throwable _ nil)))]
        (escape-html board-name-plus)]])))

(defn mobile-favorite-boards-page
  [refresh]
  (layout/mobile-login-required
    [:div.favorite-boards {:data-role "page" :data-dom-cache "true" :data-title "お気に板"}
     [:div {:role "main" :class "ui-content"}
      [:ul#favorite-boards {:data-role "listview" :data-count-theme "b"}
       (cp/pmap
         :builtin
         mobile-favorite-board-list-item-to-html
         (db/get-favorite-boards)
         (repeat refresh))]]

     [:div {:data-role "footer" :data-position "fixed" :data-tap-toggle "false"}
      [:button {:class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-refresh"
                :onclick "location.reload(true);"}
      "更新"]
      ;(link-to {:data-role "button"
      ;          :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-refresh"
      ;          :onclick "removeDomCachesForFavoriteBoards();"}
      ;         "/mobile-favorite-boards?refresh=1"
      ;         "更新")
      ]]))

(defn get-board-counts
  [favorite-board refresh]
  (let [{:keys [service board]} favorite-board
        {:keys [server]} (db/get-board-info service board)
        ; board-name (if board-name board-name "(板名未設定)")
        ; board-name-plus (str board-name (cond (= service "2ch.sc") "[sc]"
        ;                                       (= service "2ch.net") "[net]"
        ;                                       (= service "open2ch.net") "[op]"))
        board-url (create-board-url server board)
        ; item-id (random-element-id)
        ; display-name (str board-name " [" service "]")
        ]
    (try
      (let [{:keys [new-threads new-posts]} (count-new-posts-and-threads-in-board board-url refresh)]
        {:service    service,
         :board      board,
         :newThreads new-threads,
         :newPosts   new-posts})
      (catch Throwable _
        {:service    service
         :board      board
         :newThreads 0,
         :newPosts   0}))))

(defn api-mobile-get-favorite-board-counts
  [refresh]
  (if (not (check-login))
    nil
    {:body (into []
                 (cp/pmap
                   :builtin
                   get-board-counts
                   (db/get-favorite-boards)
                   (repeat refresh)))}))

(defroutes mobile-board-routes
  (GET "/mobile-board"
       [board-url max-count refresh]
       (mobile-board-page board-url max-count (= refresh "1")))
  (GET "/mobile-favorite-boards"
       [refresh]
       (mobile-favorite-boards-page (= refresh "1")))
  (GET "/api-mobile-get-favorite-board-counts"
       [refresh]
       (api-mobile-get-favorite-board-counts (= refresh "1"))))
