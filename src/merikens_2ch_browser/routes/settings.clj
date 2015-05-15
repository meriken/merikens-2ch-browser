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
; along with Foobar.  If not, see <http://www.gnu.org/licenses/>.



(ns merikens-2ch-browser.routes.settings
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
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
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [com.climate.claypoole :as cp]
            [clj-json.core]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.css :refer :all]))



(defn admin-settings-page
  []
  ; (timbre/debug thread-url)
  (let []
    (layout/admin-only-popup-window
      (list
        (include-js-no-cache "/js/admin-settings.js")
        (form-to
          [:post "/admin-settings"]
          [:div#popup-window-title "管理者設定"]
          [:input#allow-new-user-accounts-checkbox
           (let [attr {:type "checkbox" :name "allow-new-user-accounts" :value "1"}]
             (if (= (db/get-system-setting "allow-new-user-accounts") "true")
               (assoc attr :checked "")
               attr))]
          [:label#allow-new-user-accounts-checkbox-label
           {:for "allow-new-user-accounts-checkbox"}
           [:span]
           "新規ユーザーアカウントの作成を許可する"]
          (submit-button {:id "popup-window-submit-button"} "更新"))))))

(defn handle-admin-settings
  [allow-new-user-accounts]
  (timbre/debug "handle-admin-settings:" allow-new-user-accounts)
  (try
    (if (not (check-admin-login))
      (redirect "/login")
      (let []
        (db/update-system-setting "allow-new-user-accounts"
                                  (if (= allow-new-user-accounts "1") "true" "false"))
        (html [:script "close();"])))

    (catch Throwable t
      (redirect "/admin-settings?error=1"))))

(defn user-settings-page
  []
  ; (timbre/debug thread-url)
  (let []
    (layout/popup-window
      (list
        (include-js-no-cache "/js/user-settings.js")
        (form-to
          [:post "/user-settings"]
          [:div#popup-window-title "ユーザー設定"]

          [:input#use-p2-to-post-checkbox
           (let [attr {:type "checkbox" :name "use-p2-to-post" :value "1"}]
             (if (= (db/get-user-setting "use-p2-to-post") "true")
               (assoc attr :checked "")
               attr))]
          [:label#use-p2-to-post-checkbox-label
           {:for "use-p2-to-post-checkbox"}
           [:span]
           "公式p2経由で書込する(2ch.sc)"]
          [:br]
          (text-field     {:id "p2-email" :placeholder "メールアドレス"} 　"p2-email"    (db/get-user-setting "p2-email")) [:br]
          (password-field {:id "p2-password" :placeholder "パスワード"} "p2-password" (db/get-user-setting "p2-password")) [:br]

          [:input#use-ronin-checkbox
           (let [attr {:type "checkbox" :name "use-ronin" :value "1"}]
             (if (= (db/get-user-setting "use-ronin") "true")
               (assoc attr :checked "")
               attr))]
          [:label#use-ronin-checkbox-label
           {:for "use-ronin-checkbox"}
           [:span]
           "「浪人」を使用する(2ch.net)"]
          [:br]
          (text-field {:id "ronin-email" :placeholder "メールアドレス"}      "ronin-email"      (db/get-user-setting "ronin-email")) [:br]
          (password-field {:id "ronin-secret-key" :placeholder "秘密鍵"} "ronin-secret-key" (db/get-user-setting "ronin-secret-key")) [:br]

          (submit-button {:id "popup-window-submit-button"} "更新"))))))

(defn handle-user-settings
  [use-p2-to-post p2-email p2-password use-ronin ronin-email ronin-secret-key]
  ; (timbre/debug "handle-user-settings")
  (try
    (if (not (check-login))
      (redirect "/login")
      (let []
        (db/update-user-setting "use-p2-to-post"   (if (= use-p2-to-post "1") "true" "false"))
        (db/update-user-setting "p2-email"         p2-email)
        (db/update-user-setting "p2-password"      p2-password)
        (db/update-user-setting "use-ronin"        (if (= use-ronin "1") "true" "false"))
        (db/update-user-setting "ronin-email"      ronin-email)
        (db/update-user-setting "ronin-secret-key" ronin-secret-key)
        (html [:script "close();"])))

    (catch Throwable t
      (redirect "/user-settings?error=1"))))

(defn image-download-settings-page
  []
  ; (timbre/debug thread-url)
  (layout/admin-only-popup-window
    (list
      (include-js-no-cache "/js/image-download-settings.js")
      (form-to
        [:post "/image-download-settings"]
        [:div#popup-window-title "自動画像ダウンロードの設定"]

        [:input#save-downloaded-images-checkbox
         (let [attr {:type "checkbox" :name "save-downloaded-images" :value "1"}]
           (if (= (db/get-system-setting "save-downloaded-images") "true")
             (assoc attr :checked "")
             attr))]
        [:label#save-downloaded-images-checkbox-label
         {:for "save-downloaded-images-checkbox"}
         [:span]
         "ダウンロードした画像をファイルに保存する"]

        [:input#use-image-proxy-checkbox
         (let [attr {:type "checkbox" :name "use-image-proxy" :value "1"}]
           (if (= (db/get-system-setting "use-image-proxy") "true")
             (assoc attr :checked "")
             attr))]
        [:label#use-image-proxy-checkbox-label
         {:for "use-image-proxy-checkbox"}
         [:span]
         "画像プロキシを使用する"]

        (submit-button {:id "popup-window-submit-button"} "更新")))))

(defn handle-image-download-settings
  [save-downloaded-images use-image-proxy]
  ; (timbre/debug "handle-image-download-settings:" save-downloaded-images use-image-proxy)
  (try
    (if (not (check-admin-login))
      (redirect "/login")
      (let []
        (db/update-system-setting "save-downloaded-images"
                                  (if (= save-downloaded-images "1") "true" "false"))
        (db/update-system-setting "use-image-proxy"
                                  (if (= use-image-proxy "1") "true" "false"))
        (html [:script "close();"])))
    (catch Throwable t
      (redirect "/image-download-settings?error=1"))))



;;;;;;;;;;;;;;;;;
; ABORN FILTERS ;
;;;;;;;;;;;;;;;;;

(defn aborn-filters-page
  []
   ; (timbre/debug "aborn-filters-page")
  (let []
    (layout/popup-window
      (list
        [:style (css-aborn-filters)]
        (include-js-no-cache "/js/clojurescript.js")
        [:script "merikens_2ch_browser.aborn_filters.init();"]))))

(defn api-aborn-filters-get
  []
  (when (check-login)
    (generate-json-response (db/get-aborn-filters-for-message))))

(defn api-aborn-filters-post
  [body]
  (timbre/debug "api-aborn-filters-post")
  (when (check-login)
    (db/delete-all-aborn-filters-for-message)
    (doall (map #(let [{:keys [pattern board thread-title]} %]
                   (timbre/debug % pattern)
                   (db/add-post-filter {:user-id     (:id (session/get :user))
                                        :filter-type  "message"
                                        :pattern      pattern
                                        :board        board
                                        :thread-title thread-title
                                        :regex        true}))
                (:post-filters (clj-json.core/parse-string (slurp body) true))))
    "OK"))



;;;;;;;;;;;;;;;
; ABORN POSTS ;
;;;;;;;;;;;;;;;

(defn aborn-posts-page
  []
   ; (timbre/debug "aborn-posts-page")
  (let []
    (layout/popup-window
      (list
        [:style (css-aborn-posts)]
        (include-js-no-cache "/js/clojurescript.js")
        [:script "merikens_2ch_browser.aborn_posts.init();"]))))

(defn api-aborn-posts-get
  []
  (when (check-login)
    (timbre/debug (type (:time-last-matched (nth (db/get-aborn-posts) 0))))
    (generate-json-response (db/get-aborn-posts))))

(defn api-aborn-posts-post
  [body]
  ; (timbre/debug "api-aborn-posts-post" )
  (when (check-login)
    (db/delete-all-aborn-posts)
    (doall (map #(let [{:keys [pattern]} %
                       board (nth (clojure.string/split pattern #",") 1 nil)]
                   ; (timbre/debug board pattern)
                   (db/add-post-filter {:user-id     (:id (session/get :user))
                                        :filter-type "post"
                                        :pattern     pattern
                                        :board       board}))
                (:post-filters (clj-json.core/parse-string (slurp body) true))))
    "OK"))



;;;;;;;;;;;;;
; ABORN IDS ;
;;;;;;;;;;;;;

(defn aborn-ids-page
  []
  ; (timbre/debug "aborn-ids-page")
  (layout/popup-window
    (list
      [:style (css-aborn-ids)]
      (include-js-no-cache "/js/clojurescript.js")
      [:script "merikens_2ch_browser.aborn_ids.init();"])))

(defn api-aborn-ids-get
  []
  (when (check-login)
    (generate-json-response (db/get-aborn-ids))))

(defn api-aborn-ids-post
  [body]
  ; (timbre/debug "api-aborn-ids-post:" params)
  (when (check-login)
    (db/delete-all-aborn-ids)
    (doall (map #(let [{:keys [pattern]} %]
                   ; (timbre/debug pattern)
                   (db/add-post-filter {:user-id     (:id (session/get :user))
                                        :filter-type "id"
                                        :pattern     pattern}))
                (:post-filters (clj-json.core/parse-string (slurp body) true))))
    "OK"))



;;;;;;;;;;
; OTHERS ;
;;;;;;;;;;

(defn shutdown
  []
  (when (check-admin-login)
    (future
      (future
        (Thread/sleep 60000)
        (timbre/info "Forcibly shutting down the process...")
        (.halt (java.lang.Runtime/getRuntime) 0))
      (System/exit 0))
    (html "サーバーを停止しています。完全に停止するまで電源を切らないでください。")))



;;;;;;;;;;
; ROUTES ;
;;;;;;;;;;

(defroutes settings-routes
  (GET  "/admin-settings" [] (admin-settings-page))
  (POST "/admin-settings" [allow-new-user-accounts] (handle-admin-settings allow-new-user-accounts))

  (GET  "/user-settings" [] (user-settings-page))
  (POST "/user-settings"
        [use-p2-to-post p2-email p2-password use-ronin ronin-email ronin-secret-key]
        (handle-user-settings use-p2-to-post p2-email p2-password use-ronin ronin-email ronin-secret-key))

  (GET  "/image-download-settings" [] (image-download-settings-page))
  (POST "/image-download-settings"
        [save-downloaded-images use-image-proxy]
        (handle-image-download-settings save-downloaded-images use-image-proxy))

  (GET  "/aborn-filters" [] (aborn-filters-page))
  (GET  "/api-aborn-filters" [] (api-aborn-filters-get))
  (POST "/api-aborn-filters" {body :body} (api-aborn-filters-post body))

  (GET  "/aborn-posts" [] (aborn-posts-page))
  (GET  "/api-aborn-posts" [] (api-aborn-posts-get))
  (POST "/api-aborn-posts" {body :body} (api-aborn-posts-post body))

  (GET  "/aborn-ids" [] (aborn-ids-page))
  (GET  "/api-aborn-ids" [] (api-aborn-ids-get))
  (POST "/api-aborn-ids" {body :body} (api-aborn-ids-post body))

  (GET  "/shutdown" [] (shutdown)))
