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



(ns merikens-2ch-browser.routes.mobile.post
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.data.json :as json]
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
            [taoensso.timbre :as timbre :refer [log]]
            [clj-http.client :as client]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.routes.post :refer [post-to-2ch post-to-2ch-through-p2]]
            [com.climate.claypoole :as cp])
  (:import [org.apache.http.impl.cookie BasicClientCookie]))



(defn post-page
  [thread-url thread-title handle email message board-url board-name]
  ; (timbre/debug "post-page:" message)
  (let [new-thread? (> (count board-url) 0)
        parts (if new-thread? (split-board-url board-url) (split-thread-url thread-url))
        {:keys [service server board thread]} parts
        thread-title (if (> (count thread-title) 0)
                       thread-title
                       (and (not new-thread?) (remove-ng-words-from-thread-title (:title (db/get-thread-info service board thread)))))
        board-name   (if (> (count board-name) 0)
                       board-name
                       (:board-name (db/get-board-info service board)))]
    (layout/mobile-login-required
      (list
        (include-js-no-cache "/js/mobile-post.js")
        [:script "threadURL = " (if new-thread? "null" (str "'" thread-url "'")) ";"]
        [:div.thread {:data-role "page" :data-dom-cache "false" :data-title (if new-thread? board-name thread-title)}
         [:div {:role "main" :class "ui-content" :style "padding: 0 8px;"}
          (form-to
            [:post "/mobile-post"]
            (hidden-field "board-url" board-url)
            (hidden-field "board-name" board-name)
            (hidden-field "thread-url" thread-url)
            (if new-thread?
              (text-field {:id "post-page-thread-title" :placeholder "スレタイ"} "thread-title" thread-title)
              (hidden-field "thread-title" thread-title))
            (text-field {:id "post-page-handle" :placeholder "名前"}
                        "handle"
                        (if (or new-thread? (< 0 (count handle)))
                          handle
                          (db/get-last-handle service board thread)))
            (text-field {:id "post-page-email" :placeholder "メール"}
                        "email"
                        (if (or new-thread? (< 0 (count email)))
                          email
                          (db/get-last-email service board thread)))
            (text-area {:id "post-page-message" :placeholder "本文"}
                       "message"
                       (if (or new-thread? (< 0 (count message)))
                         message
                         (db/get-autosaved-draft service board thread)))
            (submit-button {:id "popup-window-submit-button"} (if new-thread? "新規スレッド作成" "書き込む")))]]))))

(defn handle-post
  [thread-title thread-url handle email message secret-name secret-value board-url board-name]
  (let [new-thread? (> (count board-url) 0)
        parts (if new-thread? (split-board-url board-url) (split-thread-url thread-url))]
    ; (timbre/debug parts)
    ; (timbre/debug "'" handle "', '" email "', '" message "'")
    (if (not parts)
      nil
      (let [{:keys [service server board thread]} parts
            {result :result, body :body} (if (and (re-find #"\.2ch\.sc$" server)
                                                  (= (db/get-user-setting "use-p2-to-post") "true"))
                                           (post-to-2ch-through-p2
                                             :server  server
                                             :bbs     board
                                             :key     (if new-thread? nil thread)
                                             :subject (if new-thread? thread-title nil)
                                             :from    handle
                                             :mail    email
                                             :message message)
                                           (post-to-2ch
                                             :server  server
                                             :bbs     board
                                             :key     (if new-thread? nil thread)
                                             :subject (if new-thread? thread-title nil)
                                             :from    handle
                                             :mail    email
                                             :message message
                                             :secret-name secret-name
                                             :secret-value secret-value))
            message-from-server (-> body
                                  (clojure.string/replace #"\n" "")
                                  (clojure.string/replace #"<ul[^>]*>" "<br>")
                                  (clojure.string/replace #"<br>" "\n")
                                  (clojure.string/replace #"(?s)^.*<body[^>]*>" "")
                                  (clojure.string/replace #"(?s)<h4.*$" "")
                                  (clojure.string/replace #"[0-9]+秒後に自動的に掲示板に飛びます。" "") ; sc
                                  (clojure.string/replace #"画面を切り替えるまでしばらくお待ち下さい。" "") ; net
                                  (clojure.string/replace #"変更する場合は戻るボタンで戻って書き直して下さい。" "") ; net
                                  (clojure.string/replace #"現在、荒らし対策でクッキーを設定していないと書きこみできないようにしています。" "") ; net
                                  (clojure.string/replace #"\(cookieを設定するとこの画面はでなくなります。\)" "") ; net
                                  (clojure.string/replace #"エラーの原因が分からない？[^《]*([ 　]*《[^》]*》)+" "") ; net
                                  (clojure.string/replace #"============" "") ; open
                                  (clojure.string/replace #"(?s)こちらでリロードしてください。.*GO!" "") ; open
                                  (clojure.string/replace #"(?s)<script[^>]*>[^/]+</script>" "") ; open
                                  (clojure.string/replace #"掲示板に戻る" "") ; したらば
                                  (clojure.string/replace #"したらば掲示板 \(無料レンタル\)" "") ; したらば
                                  (clojure.string/replace #"p2 info: 書き込み結果ページを判別できませんでした。" "")
                                  (remove-html-tags)
                                  (clojure.string/replace #"\n" "<br>")
                                  (clojure.string/replace #"^( *<br> *)" "")
                                  (clojure.string/replace #"( *<br> *)( *<br> *)+" "<br><br>")
                                  (clojure.string/replace #"( *<br> *)+$" ""))
            result                (if (and (re-find #"\.machi\.to$" server) (re-find #"<title>302 Found</title>" body)) :success result)
            message-from-server   (if (and (re-find #"\.machi\.to$" server) (= result :success)) "書き込み処理が完了しました。" message-from-server)]
        (cond
          (= result :confirmation)
          (let [secret-element (re-find #"<input +type=hidden +name=\"([a-z]+)\" +value=\"([a-z]+)\">" body)
                secret-name (nth secret-element 1 nil)
                secret-value (nth secret-element 2 nil)]
            (if (re-find #"\.open2ch\.net$" server)
              (let [new-cookie (BasicClientCookie. "IS_COOKIE" "1")
                    cookie-store (db/get-cookie-store)]
                (.setDomain new-cookie ".open2ch.net")
                (.addCookie cookie-store new-cookie)
                (db/update-cookie-store cookie-store)))
            (layout/mobile-login-required
              (list
                (include-js-no-cache "/js/mobile-post.js")
                [:script "threadURL = " (if new-thread? "null" (str "'" thread-url "'")) ";"]
                [:div.thread {:data-role "page" :data-dom-cache "false" :data-title thread-title}
                 [:div {:role "main" :class "ui-content" :style "padding: 8px 8px;"}
                  [:div
                   {:class (if (>= 4 (count (re-seq #"<br>" message-from-server)))
                             "message-info"
                             "message-info-no-icon")}
                   message-from-server]
                  (form-to
                    {:style "width: 100%; text-align: center; margin-top: 12px;"}
                    [:post "/post"]
                    (hidden-field "board-name"  board-name)
                    (hidden-field "board-url"    board-url)
                    (hidden-field "thread-title" thread-title)
                    (hidden-field "thread-url"   thread-url)
                    (hidden-field "handle"       handle)
                    (hidden-field "email"        email)
                    (hidden-field "message"      message)
                    (if secret-element
                      (list (hidden-field "secret-name"  secret-name)
                            (hidden-field "secret-value" secret-value)))
                    (submit-button "書き込む"))
                  ; [:br] [:br] (escape-html body)
                  ]])))

          (= result :success)
          (do
            (db/delete-subject-txt service board)
            (if new-thread?
              (do)
              (do
                ; TODO: Do the following with a new thread, too.
                (db/update-time-last-posted service board thread (clj-time.coerce/to-sql-time (clj-time.core/now)))
                (db/update-last-handle      service board thread handle)
                (db/update-last-email       service board thread email)
                (db/update-autosaved-draft  service board thread nil)))
            (layout/mobile-login-required
              [:div.thread {:data-role "page" :data-dom-cache "false" :data-title thread-title}
               [:div {:role "main" :class "ui-content" :style "padding: 8px 8px;"}
                [:div.message-success message-from-server]
                (link-to {:data-role "button"
                          :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-back"
                          :onclick "window.location.replace($(this).attr('href')); return false;"
                          :rel "external"}
                         (if new-thread?
                           (str "./mobile-board?board-url=" (to-url-encoded-string board-url))
                           (str "./mobile-thread?thread-url=" (to-url-encoded-string thread-url)))
                         "戻る")
                ]]))

          ; capcha (2ch.sc)
          ; TODO: Fix this.
          (re-find #"http://[a-z0-9]+.2ch.sc/test/jail.cgi" body)
          (let [new-page (re-find #"http://[a-z0-9]+.2ch.sc/test/jail.cgi\?hash=[0-9a-f]+" body)]
            (html [:script "open(decodeURIComponent('" (ring.util.codec/url-encode new-page) "'), '_self');"]))

          :else
          (layout/mobile-login-required
            [:div.thread {:data-role "page" :data-dom-cache "false" :data-title thread-title}
             [:div {:role "main" :class "ui-content" :style "padding: 8px 8px;"}
              (let [bbn-result (bbn-check)]
                (cond
                  (and (or (re-find #"\.2ch\.net$" server)
                           (re-find #"\.bbspink\.com$" server))
                       bbn-result)
                  [:div {:class "message-error"} "書き込みに失敗しました。" [:br] bbn-result]

                  (or (re-find #"http://www2.2ch.net/live.html" body)
                      (re-find #"http://www.bbspink.com/404.html" body))
                  [:div {:class "message-error"} "書き込みに失敗しました。" [:br] "あなたは規制されている可能性があります。"]

                  :else
                  [:div
                   {:class (if (>= 4 (count (re-seq #"<br>" message-from-server)))
                             "message-error"
                             "message-error-no-icon")}
                   message-from-server]))


              [:div {:style "width: 100%; text-align: center; margin-top: 12px;"}
               (let [parameters (str "board-name="  (ring.util.codec/url-encode board-name)
                                     "&board-url="   (ring.util.codec/url-encode board-url)
                                     "&thread-title="  (ring.util.codec/url-encode thread-title)
                                     "&thread-url="   (ring.util.codec/url-encode thread-url)
                                     "&handle="       (ring.util.codec/url-encode handle)
                                     "&email="        (ring.util.codec/url-encode email)
                                     "&message="      (ring.util.codec/url-encode message))]
                 (javascript-button
                   "戻る"
                   "window.history.go(-1);"))]
              ; [:br] [:br] (escape-html body)
              ]]))))))

(defroutes mobile-post-routes
  (GET "/mobile-post"
       [thread-url thread-title handle email message board-url board-name]
       (post-page thread-url thread-title handle email message board-url board-name))
  (POST "/mobile-post"
        [thread-title thread-url handle email message secret-name secret-value board-url board-name]
        (handle-post thread-title (trim thread-url) handle email message secret-name secret-value (trim board-url) board-name)))

