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



(ns merikens-2ch-browser.routes.home
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [ring.handler.dump]
            [compojure.core :refer :all]
            [noir.response :refer [redirect]]
            [noir.request]
            [noir.session :as session]
            [noir.validation :refer [rule errors? has-value? on-error]]
            [noir.io]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]
            [hiccup.util :refer [escape-html]]
            [taoensso.timbre :refer [log]]
            [clj-http.client :as client]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [noir.request]
            [merikens-2ch-browser.cursive :refer :all]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.db.schema :as schema]))




(defn home-page []
  (cond
    (mobile-device?)
    (redirect "/mobile")

    (session/get :user)
    (redirect "/main")

    :else
    (layout/public
      [:h1 (escape-html app-name)]
      [:div#inner-body
       [:p "「" (escape-html app-name) "」にようこそ! "]
       (if (nil? (db/get-user 1))
         [:p "初めての方は" (link-to "/register" "管理者アカウントを作成") "してください。"])])))



(defn get-bbs-menu-content
  [menu-url]
  (html
    "<div>"
    (let [body (:body (client/get menu-url (get-options-for-get-method menu-url)))]
      (for [item (split body #"\n")]
        (cond
          (re-find #"<B>(チャット|ツール類|他のサイト|おとなの時間|特別企画)</B>" item)
          nil

          (re-find #"http://info.2ch.sc/guide/|お絵かき板|＋記者登録所" item)
          nil

          (re-find #"<B>" item)
          (let [heading-id (random-string 16)
                items-id   (random-string 16)]
            (list
              "</div>"
              [:div.bbs-menu-item.bbs-menu-heading
               {:id heading-id}
               (escape-html (-> item
                              (clojure.string/replace #"^.*<B>" "")
                              (clojure.string/replace #"</B>.*$"  "")))]
              (set-mousedown-event-handler (str "#" heading-id)
                                           (str "if ($('#" items-id "').css('display') !== 'none') {"
                                                "$('#" items-id "').hide();"
                                                "} else {"
                                                "$('#" items-id "').show();"
                                                "}")
                                           nil)
              [:script "$(document).ready(function() { $('#" items-id "').hide(); });"]
              "<div id=" items-id ">"
              ))

          (re-find #"^<(A HREF|a href)=http://[a-z0-9.]+(2ch\.net|bbspink\.com|2ch\.sc|open2ch\.net|machi\.to)/[a-zA-Z0-9]+/( TARGET=_blank)?>" item)
          (let [item-id (random-string 16)
                board-url  (-> item
                             (clojure.string/replace #"^<(A HREF|a href)=" "")
                             (clojure.string/replace #"( TARGET=_blank)?>.*$" ""))
                board-name (-> item
                             (clojure.string/replace #"^<(A HREF|a href)=http://[a-z0-9.]+(2ch\.net|bbspink\.com|2ch\.sc|open2ch\.net|machi\.to)/[a-zA-Z0-9]+/( TARGET=_blank)?>" "")
                             (clojure.string/replace #"</[Aa]>(<BR>|<br>)?$" ""))
                ; site-name  (-> board-url
                ;              (clojure.string/replace #"^http://[^.]+\." "")
                ;              (clojure.string/replace #"/.*$" ""))
                server       (nth (re-find #"http://([a-zA-Z0-9.]+)/([a-zA-Z0-9.]+)/?$" board-url) 1)
                board        (nth (re-find #"http://([a-zA-Z0-9.]+)/([a-zA-Z0-9.]+)/?$" board-url) 2)
                service      (server-to-service server)
                display-name (str board-name " [" service "]")]
            ; (log :debug service server board)
            (db/update-board-server service server board)
            (if (nil? (:board-name (db/get-board-info service board)))
              (db/update-board-name service server board board-name)) ; TODO: Use SETTINGS.TXT instead.
            (list
              [:div.bbs-menu-item {:id item-id} "&nbsp;" (escape-html board-name)]
              (set-mousedown-event-handler (str "#" item-id)
                                           (str "updateThreadList(decodeURIComponent('" (ring.util.codec/url-encode display-name) "'), '" board-url "');")
                                           (str "displayBoardMenu(event, '" board-url "', '" server "', '" service "', '" board "');")
                                           (str "updateThreadList(decodeURIComponent('" (ring.util.codec/url-encode display-name) "'), '" board-url "', '', '', true);")))))))
    "</div>"))

(defn api-get-bbs-menu-content
  [menu-url]
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (try
      (increment-http-request-count)
      (.setPriority (java.lang.Thread/currentThread) web-sever-thread-priority)
      (let [result (get-bbs-menu-content menu-url)]
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        result)
      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (print-stack-trace t)
        "板一覧の取得に失敗しました。"))))


(defn api-get-server-info
  []
  (if (not (check-admin-login))
    (html [:script "open('/login', '_self');"])
    (let [runtime (Runtime/getRuntime)
          mb      (* 1024 1024)
          max-memory  (double (/ (.maxMemory runtime) mb))
          total-memory  (double (/ (.totalMemory runtime) mb))
          free-memory (double (/ (.freeMemory runtime) mb))
          used-memory (- total-memory free-memory)
          database-name (case (:subprotocol schema/db-spec)
                          "h2"         "H2"
                          "hsqldb"     "HyperSQL"
                          "mysql"      "MySQL"
                          "postgresql" "PostgreSQL"
                          (:subprotocol schema/db-spec))]
      (html
        [:div [:div {:style "float:left;"} "メモリ(使用中):"] [:div {:style "float:right;"} (format "%.0fMB" used-memory)]] [:div {:style "clear: both;"}]
        [:div [:div {:style "float:left;"} "メモリ(合計):"  ] [:div {:style "float:right;"} (format "%.0fMB" total-memory)]] [:div {:style "clear: both;"}]
        [:div [:div {:style "float:left;"} "メモリ使用率:"  ] [:div {:style "float:right;"} (format "%.0f%%" (* (/ used-memory total-memory) 100))]] [:div {:style "clear: both;"}]
        [:div [:div {:style "float:left;"} "メモリ(最大):"  ] [:div {:style "float:right;"} (format "%.0fMB" max-memory)]] [:div {:style "clear: both;"}]
        [:div [:div {:style "float:left;"} "データベース:"  ] [:div {:style "float:right;"} database-name]] [:div {:style "clear: both;"}]))))

(defn main-page []
  (cond
    (mobile-device?)
    (redirect "/mobile-main")

    :else
    (layout/main
      ; JavaScript
      [:script
       "var imageThumbnailNGSource             = '" image-thumbnail-ng-src "';"
       "var imageThumbnailFailedSource         = '" image-thumbnail-failed-src "';"
       "var imageThumbnailDownloadFailedSource = '" image-thumbnail-download-failed-src "';"
       "var imageThumbnailSpinnerSource        = '" image-thumbnail-spinner-src "';"
       "var thresholdForUpdatingNewPostCounts  = " threshold-for-updating-new-post-counts ";"
       "var maximumNumberOfImageDownloads      = " maximum-number-of-image-downloads-for-web-browser ";"
       "var animationDurationForImageViewer    = " animation-duration-for-image-viewer ";"]
      (include-js-no-cache "/js/jquery.tablesorter.js")
      (include-js-no-cache "/js/jquery.mousewheel.js")
      (include-js-no-cache "/js/imagesloaded.pkgd.js")
      (include-js-no-cache "/js/ZeroClipboard.js")
      (include-js-no-cache "/js/main.js")
      (include-js-no-cache "/js/clojurescript.js")

      ; These boards are not listed in the menu.
      (do
        (db/update-board-name "2ch.net" "qb5.2ch.net" "saku2ch" "削除要請")
        (db/update-board-name "2ch.net" "qb7.2ch.net" "operate2" "運用情報(金)")
        (db/update-board-name "2ch.net" "peace.2ch.net" "sakhalin" "2ch開発室")
        (db/update-board-name "2ch.net" "peace.2ch.net" "myanmar" "また挑戦。")
        (db/update-board-name "2ch.net" "peace.2ch.net" "yangon" "また挑戦２。")
        (db/update-board-name "2ch.net" "invisible.2ch.net" "invisible" "INVISIBLE")
        nil)

      ; These boards were (probably) deleted.
      (do
        (db/update-board-name "2ch.net" "hato.2ch.net" "sato" "忍者の里")
        (db/update-board-name "2ch.net" "ipv6.2ch.net" "refuge" "避難所")
        (db/update-board-name "2ch.net" "yuzuru.2ch.net" "mu" "幻の大陸")
        nil)

      ; Update board information in the background.
      (do (future (doall (pmap #(try (get-bbs-menu-content %1) (catch Throwable _))
                               (list "http://menu.2ch.sc/bbsmenu.html"
                                     "http://menu.2ch.net/bbsmenu.html"
                                     "http://open2ch.net/menu/pc_menu.html"))))
        nil)

      [:div#panes
       [:div#left-panes-wrapper
        [:div#left-panes.scrollbar
         [:div#left-panes-content
          (for [left-pane left-panes]
            (cond
              (= (first left-pane) :account-menu)
              (list [:h2#account-menu-title app-name]
                    ; must use set-click-event-handler with a popup window
                    [:div#account-menu.left-panes-inner-body
                     ; user settings
                     [:div#user-settings.bbs-menu-item "ユーザー:" (str (:display-name (session/get :user)))]
                     (set-click-event-handler "#user-settings" "openUserSettingsWindow();" nil)
                     [:div#aborn-filters.bbs-menu-item "あぼ～んフィルタ管理"]
                     (set-click-event-handler "#aborn-filters" "openAbornFiltersWindow();" nil)
                     [:div#aborn-posts.bbs-menu-item "あぼ～んレス管理"]
                     (set-click-event-handler "#aborn-posts" "openAbornPostsWindow();" nil)
                     [:div#aborn-ids.bbs-menu-item "あぼ～んID管理"]
                     (set-click-event-handler "#aborn-ids" "openAbornIDsWindow();" nil)
                     [:div#logout.bbs-menu-item "ログアウト"]
                     (set-mousedown-event-handler "#logout" "window.open('/logout', '_self');" nil)
                     ; admin settings
                     (if (check-admin-login)
                       (list
                         [:div#admin-settings.bbs-menu-item "管理者設定"]
                         (set-click-event-handler "#admin-settings" "openAdminSettingsWindow();" nil)
                         [:div#shutdown.bbs-menu-item "サーバー停止"]
                         (set-mousedown-event-handler "#shutdown" "window.open('/shutdown', '_self');" nil)))]
                    (if (= (second left-pane) :open)
                      [:script "$(document).ready(function() { openAccountMenu(); });"]
                      [:script "$(document).ready(function() { closeAccountMenu(); });"]))

              (= (first left-pane) :server-info)
              (list (if (check-admin-login)
                      (list
                        [:h2#server-info-panel-title "サーバー情報"]
                        [:div#server-info-panel.left-panes-inner-body]
                        [:script
                         "$(document).ready(function() {"
                         (if (= (second left-pane) :open)
                           "openServerInfoPanel();"
                           "closeServerInfoPanel();")
                         "});"])))

              (= (first left-pane) :favorite-boards)
              (list [:h2#favorite-board-list-title "お気に板"]
                    [:div#favorite-board-list-toolbar.left-panes-inner-body.bbs-menu-tools
                     (compact-javascript-button "新着更新" "updateFavoriteBoardList();")
                     (compact-javascript-checkbox
                       "自動更新"
                       (if (= (db/get-user-setting "enable-automatic-updates-for-favorite-board-list") "true") true false)
                       "updateUserSetting('enable-automatic-updates-for-favorite-board-list', ($('#automatic-updates-for-favorite-board-list-checkbox').is(':checked') ? 'true' : 'false'));"
                       :id "automatic-updates-for-favorite-board-list-checkbox")]
                    [:div#favorite-board-list.left-panes-inner-body]
                    (if (= (second left-pane) :open)
                      [:script "openFavoriteBoardList();"]
                      [:script "closeFavoriteBoardList();"]))

              (= (first left-pane) :special-menu)
              (list [:h2#special-menu-title "特別"]
                    [:div#special-menu-toolbar.left-panes-inner-body.bbs-menu-tools
                     (compact-javascript-button "新着更新" "updateSpecialMenu();")
                     (compact-javascript-checkbox
                       "自動更新"
                       (if (= (db/get-user-setting "enable-automatic-updates-for-special-menu") "true") true false)
                       "updateUserSetting('enable-automatic-updates-for-special-menu', ($('#automatic-updates-for-special-menu-checkbox').is(':checked') ? 'true' : 'false'));"
                       :id "automatic-updates-for-special-menu-checkbox")
                     (compact-javascript-button
                       "スレ取得"
                       (str "$('#update-special-threads-button').prop('disabled', true);"
                            "$.ajax({ url: '/api-update-special-threads', async: true, complete: function(result, textStatus) { $('#update-special-threads-button').prop('disabled', false); }});")
                       :id "update-special-threads-button")]
                    [:div#special-menu.left-panes-inner-body]
                    (if (= (second left-pane) :open)
                      [:script "openSpecialMenu();"]
                      [:script "closeSpecialMenu();"]))

              (= (first left-pane) :2ch-net)
              (list [:h2#bbs-menu-2ch-net-title.bbs-menu-title "板一覧 (2ch.net)"]
                    [:div#bbs-menu-2ch-net.left-panes-inner-body.bbs-menu-inner-body]
                    (if (= (second left-pane) :open)
                      [:script "openBBSMenu('#bbs-menu-2ch-net', '#bbs-menu-2ch-net-title', 'http://menu.2ch.net/bbsmenu.html');"]
                      [:script "closeBBSMenu('#bbs-menu-2ch-net', '#bbs-menu-2ch-net-title', 'http://menu.2ch.net/bbsmenu.html');"]))

              (= (first left-pane) :2ch-sc)
              (list [:h2#bbs-menu-2ch-sc-title.bbs-menu-title "板一覧 (2ch.sc)"]
                    [:div#bbs-menu-2ch-sc.left-panes-inner-body.bbs-menu-inner-body]
                    (if (= (second left-pane) :open)
                      [:script "openBBSMenu('#bbs-menu-2ch-sc', '#bbs-menu-2ch-sc-title', 'http://menu.2ch.sc/bbsmenu.html');"]
                      [:script "closeBBSMenu('#bbs-menu-2ch-sc', '#bbs-menu-2ch-sc-title', 'http://menu.2ch.sc/bbsmenu.html');"]))

              (= (first left-pane) :open2ch-net)
              (list [:h2#bbs-menu-open2ch-net-title.bbs-menu-title "板一覧 (open2ch.net)"]
                    [:div#bbs-menu-open2ch-net.left-panes-inner-body.bbs-menu-inner-body]
                    (if (= (second left-pane) :open)
                      [:script "openBBSMenu('#bbs-menu-open2ch-net', '#bbs-menu-open2ch-net-title', 'http://open2ch.net/menu/pc_menu.html');"]
                      [:script "closeBBSMenu('#bbs-menu-open2ch-net', '#bbs-menu-open2ch-net-title', 'http://open2ch.net/menu/pc_menu.html');"]))

              (= (first left-pane) :machi-bbs)
              (list [:h2#bbs-menu-machi-bbs-title.bbs-menu-title "板一覧(まちBBS)"]
                    [:div#bbs-menu-machi-bbs.left-panes-inner-body.bbs-menu-inner-body]
                    (if (= (second left-pane) :open)
                      [:script "openBBSMenu('#bbs-menu-machi-bbs', '#bbs-menu-machi-bbs-title', 'http://kita.jikkyo.org/cbm/cbm.cgi/m0.99/bbsmenu.html');"]
                      [:script "closeBBSMenu('#bbs-menu-machi-bbs', '#bbs-menu-machi-bbs-title', 'http://kita.jikkyo.org/cbm/cbm.cgi/m0.99/bbsmenu.html');"]))

              (= (first left-pane) :image-download-info)
              (list [:h2#download-status-panel-title "自動画像ダウンロード"]
                    [:div#download-status-panel-toolbar.left-panes-inner-body.bbs-menu-tools
                     (compact-javascript-checkbox
                       "有効"
                       (if (= (db/get-user-setting "download-images") "true") true false)
                       "configureImageDownloading();"
                       :id "automatic-downloading-checkbox")
                     (compact-javascript-button "中断" "stopCurrentDownloads();")
                     (if (check-admin-login) (compact-javascript-button "設定" "openImageDownloadSettingsWindow();"))]
                    [:div#download-status-panel.left-panes-inner-body.bbs-menu-inner-body]
                    [:script
                     "$(document).ready(function() {"
                     "updateDownloadStatusPanel();"
                     "openDownloadStatusPanel();"
                     "});"]
                    [:script
                     "$(document).ready(function() {"
                     (if (= (second left-pane) :open)
                       "openDownloadStatusPanel();"
                       "closeDownloadStatusPanel();")
                     "});"])))
          ]]]

       [:div#vertical-draggable-area ""]
       [:script
        "$(document).ready(function() {"
        "$('#vertical-draggable-area').click(function () {"
        "toggleLeftPanes();"
        "}); });"]

       [:div#right-panes
        [:div#board-names
         [:div#active-board-name.board-name]
         [:div#add-board-tab-button {:onclick "addBoardTab();"} "+"]
         [:div {:style "clear: both;"}]]
        [:script
         "$(document).ready(function() {"
         "updateBoardNames();"
         "setBoardName('スレッド一覧', null);"
         "});"]
        [:div#thread-list-tools-below-board-name.right-pane-inner-body
         [:div#thread-list-tools-below-board-name-wrapper
          [:div#star-wrapper-for-favorite-board
           [:img#gray-star-for-favorite-board {:src "/img/star-24-grayscale.png"}]
           [:img#star-for-favorite-board {:src "/img/star-24.png"}]]
          [:script
           "$(document).ready(function() {"
           "$('#star-for-favorite-board').click(function () {"
           "toggleStarForFavoriteBoard();"
           "});"
           "$('#gray-star-for-favorite-board').click(function () {"
           "toggleStarForFavoriteBoard();"
           "});"
           "updateStarForFavoriteBoard();"
           "});"]
          (compact-javascript-button "新着更新"   "refreshThreadList();"    :id "refresh-thread-list-button")
          (compact-javascript-checkbox
            "自動更新"
            (if (= (db/get-user-setting "enable-automatic-updates-for-thread-list") "true") true false)
            "updateUserSetting('enable-automatic-updates-for-thread-list', ($('#automatic-updates-for-thread-list-checkbox').is(':checked') ? 'true' : 'false'));"
            :id "automatic-updates-for-thread-list-checkbox"
            :label-id "automatic-updates-for-thread-list-checkbox-label")
          (compact-javascript-button "スレ立て" "openPostWindow(true);"   :id "open-post-window-button")
          (compact-javascript-checkbox "ログ一覧" false "toggleLogListMode();" :id "log-list-mode-checkbox" :label-id "log-list-mode-checkbox-label")
          (compact-javascript-button "元板"   "openOriginalBoard();"    :id "open-original-board-button")
          (text-field {:id "board-url-text-field" :class "compact" :placeholder "板のアドレス"} "board-url")
          (text-field {:id "thread-search-text-field" :class "compact" :placeholder "スレタイ検索(正規表現)"} "board-search")]]
        [:div#thread-list.right-pane-inner-body]

        [:div#right-panes-draggable-area ""]
        [:script
         "$(document).ready(function() {"
         "$('#right-panes-draggable-area').click(function () {"
         "toggleThreadList();"
         "}); });"]

        [:div#thread-titles
         [:div#active-thread-title.thread-title]
         [:div#add-thread-tab-button {:onclick "addThreadTab();"} "+"]
         [:div {:style "clear: both;"}]]
        [:script
         "$(document).ready(function() {"
         "updateThreadTitles();"
         "setThreadTitle('スレッド', null);"
         "});"]
        [:div#thread-content-tools-below-thread-title.right-pane-inner-body
         [:div#thread-content-tools-below-thread-title-wrapper
          [:div#star-wrapper-for-favorite-thread
           [:img#gray-star-for-favorite-thread {:src "/img/star-24-grayscale.png"}]
           [:img#star-for-favorite-thread {:src "/img/star-24.png"}]]
          [:script
           "$(document).ready(function() {"
           "$('#star-for-favorite-thread').click(function () {"
           "toggleStarForFavoriteThread();"
           "});"
           "$('#gray-star-for-favorite-thread').click(function () {"
           "toggleStarForFavoriteThread();"
           "});"
           "updateStarForFavoriteThread();"
           "});"]
          ; (compact-javascript-button "戻る" "threadContentGoBackward();" :id "go-forward-button")
          ; (compact-javascript-button "進む" "threadContentGoForward();" :id "go-backward-button")
          (text-field {:id "thread-url-text-field" :class "compact" :placeholder "スレッドのアドレス"} "thread-url")
          (text-field {:id "post-search-text-field" :class "compact" :placeholder "レス検索(正規表現)"} "thread-search")]]
        [:div#thread-content.right-pane-inner-body.scrollbar]
        [:div#thread-content-tools-at-bottom.right-pane-inner-body
         [:div#thread-content-tools-at-bottom-wrapper
          (compact-javascript-button "新着更新" "reloadCurrentThread();")
          (compact-javascript-button "再読込" "if (currentThreadURL) { updateThreadContent(currentThreadTitle, currentThreadURL, '', '', false, false, true); }")
          (compact-javascript-button "書き込み" "openPostWindow(false);")
          (compact-javascript-button "最初" "$('#thread-content, #thread-content-wrapper').animate({scrollTop:0}, 'slow');")
          (compact-javascript-button "最後" "$('#thread-content, #thread-content-wrapper').animate({scrollTop:$('#thread-content-wrapper').height()}, 'slow');")
          (compact-javascript-button "板" "openBoardForCurrentThread();")
          (compact-javascript-button "元スレ" "openOriginalThread();")
          (compact-javascript-button "似スレ" "loadSimilarThreadList();")
          (compact-javascript-button "ログ削除" "deleteThreadLog();")]]]]

      [:div#temporary-script ""]
      [:div#fixed-thread-heading.thread-heading ""]
      [:script
			 "$(document).ready(function() {"
			 "$('#fixed-thread-heading').click(function () {"
       "var threadUrl = getFirstVisiblePostThreadUrl();"
       "if (threadUrl) updateThreadContent(null, threadUrl, '', '', true);"
			 "});"
			 "});"
      ]
      [:div#floating-posts ""]
      [:div#popup-menus ""]
      [:div#image-viewer ""])))

(defn shingetsu-api-ping
  []
  {:status 200
   :body (str "PONG\n" (:remote-addr noir.request/*request*))
   :headers {}})

(defroutes home-routes
  (GET "/health-check"[] {:status 200 :body "OK" :headers {}})
  (GET "/server.cgi/ping" [] (shingetsu-api-ping))

  (GET "/" [] (home-page))

  (GET "/main" [] (main-page))
  (GET "/main/ZeroClipboard.swf" [] (noir.io/get-resource "/js/ZeroClipboard.swf"))

  (GET "/api-get-bbs-menu-content"
       [menu-url]
       (api-get-bbs-menu-content (trim menu-url)))

  (GET "/api-get-server-info" [] (api-get-server-info)))

