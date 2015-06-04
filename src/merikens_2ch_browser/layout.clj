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



(ns merikens-2ch-browser.layout
  (:require [clojure.string :refer [split]]
            [ring.util.request :refer [request-url]]
            [ring.util.response :refer [response charset content-type]]
            [noir.session :as session]
            [noir.response :refer [redirect]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]
            [hiccup.element :refer [link-to]]
            [hiccup.util :refer [escape-html]]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]))



(def default-title app-name)



(defn generate-page-top []
  (let [user (session/get :user)]
    [:div#page-top
     (link-to {:id "page-top-app-name"} "/" app-name)
     (if (check-admin-login)
       (list
         [:a#admin-settings.page-top-menu-item "管理者設定"]
         [:script
          "$(document).ready(function() { $('#admin-settings').click(function () {"
          "openAdminSettingsWindow();"
          "}); });"]
         (link-to {:class "page-top-menu-item"} "/shutdown" "サーバー停止")))
     (if user
       (list
         ))
     (if user
       (list
         (link-to {:class "page-top-user-item"} "/logout" "ログアウト")
         [:a#user-settings.page-top-user-item "ユーザー:" (str (:display-name (session/get :user)))]
         [:script
          "$(document).ready(function() { $('#user-settings').click(function () {"
          "openUserSettingsWindow();"
          "}); });"])
       (list
         (if (db/get-user 1)
           (link-to {:class "page-top-user-item"} "/login" "ログイン"))
         (if (or (nil? (db/get-user 1)) (= (db/get-system-setting "allow-new-user-accounts") "true"))
           (link-to {:class "page-top-user-item"} "/register" "アカウント作成"))))]))

(def css-and-js-files
  (list
    (include-css-no-cache "/css/screen.css")
    (include-css-no-cache "/jquery-ui/jquery-ui.css")
    (include-js-no-cache "/js/jquery.js")
    (include-js-no-cache "/jquery-ui/jquery-ui.js")
    (include-js-no-cache "/js/sha512.js")
    (include-js-no-cache "/js/sha512-enm178.js")
    (include-js-no-cache "/js/forms.js")))

(def head
  [:head
   [:meta {:charset "UTF-8"}]
   [:title default-title]
   [:link {:rel "shortcut icon" :href "img/favicon.png"}]
   css-and-js-files])

(defn redirect-to-login-page
  []
  (redirect (str "/login?login-required=1&return-to=" (url-encoded-current-url))))

(defn public [& body]
  (html5
    head
    [:style css-pc]
    [:body body (generate-page-top)]))

(defn main [& body]
  (if (not (check-login))
    (redirect-to-login-page)
    (html5
      head
      [:style css-pc]
      [:body body])))

(comment defn login-required [& body]
  (if (not (check-login))
    (redirect-to-login-page)
    (html5
      head
      [:style css-pc]
      [:body body (generate-page-top)])))

(defn post [body]
  (if (not (check-login))
    (html [:script "opener.open('/login', '_self'); window.close();"])
    (html5
      head
      [:style css-pc]
      [:body.simple-layout.scrollbar body])))

(defn popup-window [body]
  (if (not (check-login))
    (html [:script "opener.open('/login', '_self'); window.close();"])
    (html5
      head
      [:style css-pc]
      [:body.simple-layout.scrollbar body])))

(defn admin-only-popup-window [body]
  (if (not (check-admin-login))
    (html [:script "opener.open('/login', '_self'); window.close();"])
    (html5
      head
      [:style css-pc]
      [:body.simple-layout.scrollbar body])))

(comment defn admin-only [& body]
  (if (not (check-admin-login))
    (redirect-to-login-page)
    (html5
      head
      [:style css-pc]
      [:body body (generate-page-top)])))

(defn error [& inner-body]
  (html5
    [:head [:title default-title] (include-css "/css/screen.css")]
    [:style css-pc]
    [:body.error [:h1.error "エラー"] [:div#inner-body.error inner-body]]))




(def mobile-css-and-js-files
  (list
    (include-css-no-cache "/jquery.mobile/jquery.mobile-1.4.5.css")
    (include-css-no-cache "/css/mobile.css")
    (include-js-no-cache "/js/jquery.js")
    [:script
     "$(document).on('mobileinit', function(){"
     "  $.mobile.defaultPageTransition = 'fade';"
     " $.mobile.loader.prototype.options.text = '読み込み中…';
              $.mobile.loader.prototype.options.textVisible = true;
              $.mobile.loader.prototype.options.theme = 'b';
              $.mobile.loader.prototype.options.textonly = true;
              $.mobile.loader.prototype.options.html = \"\";"

     ; "$.mobile.page.prototype.options.domCache = true;"
     "$.ajaxSetup({ cache: false });"
     "});"
     ]
    (include-js-no-cache "/jquery.mobile/jquery.mobile-1.4.5.js")
    (include-js-no-cache "/jquery-ui/jquery-ui.js")
    (include-js-no-cache "/jquery-ui/jquery.ui.touch-punch.js")
    (include-js-no-cache "/js/sha512.js")
    (include-js-no-cache "/js/sha512-enm178.js")
    (include-js-no-cache "/js/forms.js")
    (include-js-no-cache "/js/imagesloaded.pkgd.js")
    (include-js-no-cache "/js/ZeroClipboard.js")
    (include-js-no-cache "/js/mobile-main.js")))

(def mobile-head
  [:head
   [:title (escape-html default-title)]
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
   [:link {:rel "shortcut icon" :href "img/favicon.png"}]
   mobile-css-and-js-files])

(defn mobile-header
  []
  (list [:div {:data-role "header" :data-position "fixed" :data-tap-toggle "false" :data-theme "a"}
         (link-to {:data-role "button"
                   :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-back"
                   :data-rel "back"
                   :data-ajax "true"
                   :onclick "$.mobile.defaultHomeScroll = 0;"}
                  "#"
                  "戻る")
         [:h1]
         (link-to {:data-role "button"
                   :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-bars"
                   :data-ajax "false"
                   :data-direction "reverse"}
                  "/mobile-main"
                  "目次")]
        [:script
         "function jumpToPost() {}"
         "$( document ).on(\"pagecontainershow\", function() {"
         "$(\"[data-role='header'] h1\").text($(\".ui-page-active\").jqmData(\"title\"));"
         "$(function(){ $(\"[data-role='header']\").toolbar(); });"
         "});"
         "var imageThumbnailNGSource             = '" image-thumbnail-ng-src "';"
         "var imageThumbnailFailedSource         = '" image-thumbnail-failed-src "';"
         "var imageThumbnailDownloadFailedSource = '" image-thumbnail-download-failed-src "';"
         "var imageThumbnailSpinnerSource        = '" image-thumbnail-spinner-src "';"
         "var animationDurationForImageViewer    = " animation-duration-for-image-viewer ";"]))

(defn redirect-to-mobile-login-page
  []
  (redirect (str "/mobile-login?login-required=1&return-to=" (url-encoded-current-url))))

(defn mobile-public [& body]
  (html5
    mobile-head
    [:style css-mobile]
    [:body (mobile-header) body [:div#image-viewer]]
    ;[:script "$(\":jqmData(role='page')\").attr(\"data-title\", document.title);"]
    ))

(defn mobile-login-required [& body]
  (if (not (check-login))
    (redirect-to-mobile-login-page)
    (html5
      mobile-head
      [:style css-mobile]
      [:body (mobile-header) body [:div#image-viewer]]
      ;[:script "$(\":jqmData(role='page')\").attr(\"data-title\", document.title);"]
      )))
