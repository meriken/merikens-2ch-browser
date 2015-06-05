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



(ns merikens-2ch-browser.routes.mobile.home
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
            [merikens-2ch-browser.auth :refer :all]))



(defn mobile-home-page []
  (if (check-login)
    (redirect "/mobile-main")
    (layout/mobile-public
      [:div {:data-role "page" :data-dom-cache "true" :data-title "はじめに"}
       [:div {:role "main" :class "ui-content"}
        (escape-html app-name) "にようこそ! このモバイル版は現在開発中です。"
        (link-to {:data-role "button" :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left" :data-transition "fade"} "/mobile-login" "ログイン")]])))



(defn mobile-main-page []
  (layout/mobile-login-required
    ; [:div {:data-role "header" :data-theme "a"}　[:h1 "目次"]]
    ; [:script "$(function(){ $(\"[data-role='header'], [data-role='footer']\").toolbar(); });"]
    [:div {:data-role "page" :data-dom-cache "true" :data-title "目次"}
     [:div {:role "main" :class "ui-content"}
      [:ul {:data-role "listview"}
       [:li (link-to "/mobile-favorite-boards" "お気に板")]
       [:li (link-to "/mobile-favorite-threads" "お気にスレ")]
       [:li (link-to "/mobile-recently-viewed-threads" "最近読んだスレ")]
       [:li (link-to "/mobile-recently-posted-threads" "書込履歴")]
       ; [:li (link-to "/mobile-2ch-sc" "板一覧(2ch.sc)")]
       ; [:li (link-to "/mobile-2ch-net" "板一覧(2ch.net)")]
       ; [:li (link-to "/mobile-open2ch-net" "板一覧(open2ch.net)")]
       ; [:li (link-to "/mobile-server-info" "サーバー情報")]
       ; [:li (link-to "/mobile-image-downloads" "自動画像ダウンロード")]
       [:li (link-to {:data-transition "fade"} "/mobile-logout" "ログアウト")]]]]))



(defroutes mobile-home-routes
  (GET "/m" [] (redirect "/mobile"))
  (GET "/mobile" [] (mobile-home-page))
  (GET "/mobile-main" [] (mobile-main-page)))

