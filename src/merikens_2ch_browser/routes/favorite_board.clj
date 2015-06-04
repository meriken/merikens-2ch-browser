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



(ns merikens-2ch-browser.routes.favorite-board
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [ring.handler.dump]
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
            [com.climate.claypoole :as cp]))



(defn count-new-posts-and-threads-in-board
  [board-url refresh]
  ; (timbre/debug "count-new-posts-and-threads-in-board:" board-url without-refresh)
  (let [{:keys [server service board]} (split-board-url board-url)
        body (get-subject-txt board-url refresh)
        all-items (clojure.string/split body #"\n")
        items (if (shitaraba-url? board-url) (drop-last all-items) all-items)
        board (if (shingetsu-server? server)
                (clojure.string/replace board #"_[0-9A-F]+$" "")
                board)
        bookmark-map (create-bookmark-map service board)]
    (if (nil? body)
      (throw (Exception.))
      (let [results (for [item items]
                      (try
                        (let [parts (if (or (shitaraba-url? board-url) (machi-bbs-url? board-url))
                                      (re-find #"^(([0-9]+)\.cgi),(.*)\(([0-9]+)\)$" item)
                                      (re-find #"^(([0-9]+)\.dat)<>(.*) +\(([0-9]+)\)$" item))
                              res-count (Integer/parseInt (nth parts 4))
                              thread-no (nth parts 2)
                              bookmark  (or ((keyword thread-no) bookmark-map)
                                            (db/get-bookmark service board thread-no))
                              new-thread? (nil? bookmark)
                              new-res?  (and bookmark (> bookmark 0) (> (- res-count bookmark) 0))
                              new-res-count (if (and bookmark new-res?) (- res-count bookmark) 0)]
                          (if bookmark
                            (db/update-thread-res-count service server board (java.lang.Long/parseLong thread-no) res-count))
                          {:new-threads (if new-thread? 1 0), :new-posts new-res-count})
                        (catch Throwable t
                          {:new-threads 0, :new-posts 0})))]
        {:new-threads (apply + (map :new-threads results))
         :new-posts   (apply + (map :new-posts results))}))))

(defn favorite-board-list-item-to-html
  [favorite-board bubbles refresh]
  (let [{:keys [service board]} favorite-board
        {:keys [server board-name]} (db/get-board-info service board)
        board-name (if board-name board-name "(板名未設定)")
        board-name-plus (str board-name (cond (= service "2ch.sc") "[sc]"
                                              (= service "2ch.net") "[net]"
                                              (= service "open2ch.net") "[op]"))
        board-url (create-board-url server board)
        item-id (random-element-id)
        new-thread-bubble-id (random-element-id)
        new-post-bubble-id   (random-element-id)
        display-name (str board-name " [" service "]")]
    (list
      [:div.bbs-menu-item
       {:id item-id}
       (try
         (let [{:keys [new-threads new-posts]} (if bubbles (count-new-posts-and-threads-in-board board-url refresh))
               new-post-count-class (clojure.string/replace (str "new-post-count-" service "-" board) #"[./]" "_")
               board-name-class (clojure.string/replace (str "board-name-" service "-" board) #"[./]" "_")]
           (list
             [:div {:class board-name-class
                    :style (str "float:left;"
                                (if (and bubbles (or (> new-threads 0) (> new-posts 0))) "font-weight:bold;" ""))}
              (escape-html board-name-plus)]
             (if bubbles
               (let []
             [:div {:style "float:right;"}
              (if (and bubbles (>= new-threads 0)) [:div {:id new-thread-bubble-id
                                                          :class (str "bubble "
                                                                      "new-threads "
                                                                      (if (> new-threads 0) "non-zero " ""))}
                                                    new-threads])
              (if (and bubbles (>= new-posts   0)) [:div {:id new-post-bubble-id
                                                          :class (str "bubble "
                                                                      "new-posts "
                                                                      (if (> new-posts 0) "non-zero " "")
                                                                      new-post-count-class)}
                                                    new-posts])]))))
         (catch Throwable t
           (escape-html board-name-plus)))]
      [:script
       "$(document).ready(function() {"
       "$('#" item-id "')"
       ".draggable({"
       "start: favoriteBoardListStartDragging,"
       "stop:  favoriteBoardListStopDragging,"
       "containment: '#favorite-board-list',"
       "axis: 'y'"
       "})"
       ".hover(function(event) {"
       "$(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent');"
       "});"

       "$('#" new-thread-bubble-id "').mousedown(function(e) {"
       "e.preventDefault();"
       "e.stopPropagation();"
       "if (e.which != 1 || $(this).text() == '0') return;"
       "});"

       "$('#" new-post-bubble-id "').mousedown(function(e) {"
       "e.preventDefault();"
       "e.stopPropagation();"
       "if (e.which != 1 || $(this).text() == '0') return;"
       "loadNewPostsInBoard(decodeURIComponent('" (ring.util.codec/url-encode board-name) "'), '" board-url "');"
       "});"

       "$('#" item-id "')"
       ; ".off('mousedown')"
       ".mousedown(function(event) {"
       "    if (event.which == 1) {"
       "         updateThreadList(decodeURIComponent('" (ring.util.codec/url-encode display-name) "'), '" board-url "');"
       "    } else if (event.which == 2) {"
       "        event.preventDefault();"
       "        updateThreadList(decodeURIComponent('" (ring.util.codec/url-encode display-name) "'), '" board-url "', '', '', true);"
       "        return false;"
       "    } else if (event.which == 3) {"
       "        displayBoardMenu(event, '" board-url "', '" server "', '" service "', '" board "');"
       "        return false;"
       "    }"
       "    return true;"
       "})"
       ".attr('oncontextmenu', 'return false;');"

       "});"])))

(defn api-get-favorite-board-list
  [bubbles refresh]
  ; (timbre/debug "api-get-favorite-board-list")
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (try
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
      (increment-http-request-count)
      (let [result (html (cp/pmap
                           :builtin
                           favorite-board-list-item-to-html
                           (db/get-favorite-boards)
                           (repeat (= bubbles "1"))
                           (repeat (= refresh "1"))))]
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        result)
      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        "お気に板の取得に失敗しました。"))))

(defn api-add-favorite-board
  [board-url]
  ; (timbre/debug "api-add-favorite-board")
  ; (timbre/debug board-url)
  (if (not (check-login))
    (ring.util.response/not-found "404 Not Found")
    (let
      [board-url-parts (re-find #"^http://([a-z0-9.]+)/(.*)/$" board-url)
       server          (nth board-url-parts 1)
       board           (nth board-url-parts 2)
       service         (server-to-service server)]
      (if (not (db/get-favorite-board service board))
        (do
          (db/add-favorite-board {:user-id   (:id (session/get :user))
                                  :service   service
                                  :server    server
                                  :board     board})
          "OK")))))

(defn api-remove-favorite-board
  [board-url]
  ; (timbre/debug "api-remove-favorite-board")
  ; (timbre/debug board-url)
  (when (check-login)
    (let
      [board-url-parts (re-find #"^http://([a-z0-9.]+)/(.*)/$" board-url)
       server          (nth board-url-parts 1)
       board           (nth board-url-parts 2)
       service         (server-to-service server)]
      (if (db/get-favorite-board service board)
        (db/delete-favorite-board service board))
      "OK")))

(defn api-is-favorite-board
  [board-url]
  (when (check-login)
    (let
      [board-url-parts (re-find #"^http://([a-z0-9.]+)/(.*)/$" board-url)
       server          (nth board-url-parts 1)
       board           (nth board-url-parts 2)
       service         (server-to-service server)]
      (if (db/get-favorite-board service board)
        "true"
        "false"))))

(defn move-element-in-list
  [l from to]
  (let [vec (into [] l)]
    (if (or (< from 0) (<= (count vec) from)
            (< to 0)   (<= (count vec) to)
            (= from to))
      l
      (let [e      (nth vec from)
            before (if (<= from 0)                 nil (subvec vec 0 from))
            after  (if (>= from (dec (count vec))) nil (subvec vec (inc from)))
            new-vec (into [] (concat before after))
            new-to  to
            new-before (if (<= new-to 0)                     nil (subvec new-vec 0 new-to))
            new-after  (if (>= new-to (count new-vec)) nil (subvec new-vec new-to))]
        (concat new-before (vector e) new-after)))))

(defn api-move-favorite-board
  [old-position new-position]
  (when (check-login)
    (let
      [favorite-boards (move-element-in-list (db/get-favorite-boards) (Integer/parseInt old-position) (Integer/parseInt new-position))]
      (timbre/debug favorite-boards)
      (db/delete-all-favorite-boards)
      (doall
        (for [favorite-board favorite-boards]
          (db/add-favorite-board favorite-board)))
      "OK")))

(defn board-name-page
  [board-url service server board]
  ; (timbre/debug "board-name-page")
  (when (check-login)
    (layout/popup-window
      (list
        (include-js "/js/board-name.js")
        (form-to
          [:post "/handle-board-name"]
          [:div#popup-window-title "「" board-url "」の名前を設定"]

          (text-field {:id "board-name" :placeholder "新しい板の名前"} "name" (:board-name (db/get-board-info service board))) [:br]
          (hidden-field "service" service)
          (hidden-field "server"  server)
          (hidden-field "board"   board)
          (submit-button {:id "popup-window-submit-button"} "設定"))))))

(defn handle-board-name
  [service server board name]
  ; (timbre/debug "handle-board-name")
  (try
    (if (not (check-login))
      (redirect "/login")
      (let []
        (if (>= 0 (count name)) (throw (Exception.)))
        (db/update-board-name service server board name)
        (html
          [:script
           "opener.loadFavoriteBoardList(false);"
           "opener.loadFavoriteBoardList(true);"
           "close();"])))

    (catch Throwable t
      (redirect "/board-name-page?error=1"))))

(defroutes favorite-board-routes
  (GET "/api-get-favorite-board-list"
       [bubbles refresh]
       (api-get-favorite-board-list bubbles refresh))
  (GET "/api-is-favorite-board"
       [board-url]
       (api-is-favorite-board (trim board-url)))
  (GET "/api-add-favorite-board"
       [board-url]
       (api-add-favorite-board (trim board-url)))
  (GET "/api-remove-favorite-board"
       [board-url]
       (api-remove-favorite-board (trim board-url)))
  (GET "/api-move-favorite-board"
       [old-position new-position]
       (api-move-favorite-board old-position new-position))
  (GET "/board-name-page"
       [board-url service server board]
       (board-name-page board-url service server board))
  (POST "/handle-board-name"
        [service server board name]
        (handle-board-name service server board name)))