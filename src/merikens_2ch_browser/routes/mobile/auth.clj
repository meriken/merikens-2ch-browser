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



(ns merikens-2ch-browser.routes.mobile.auth
  (:require [clojure.string :refer [split]]
            [compojure.core :refer [defroutes GET POST]]
            [noir.request]
            [noir.response :refer [redirect]]
            [noir.session :as session]
            [noir.validation :refer [rule errors? has-value? on-error]]
            [noir.util.crypt :as crypt]
            [ring.util.codec :refer [url-encode]]
            [hiccup.element :refer [link-to]]
            [hiccup.form :refer
             [form-to label text-field password-field hidden-field submit-button]]
            [pandect.core :refer [sha512]]
            [taoensso.timbre :as timbre]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.db.schema :as schema]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.util :refer :all]))



(defn mobile-registration-page
  []
  ; (timbre/debug "registration-page:" (session/get :user))
  (cond
    (session/get :user)
    (redirect "/main")

    (and (db/get-user 1)
         (not (= (db/get-system-setting "allow-new-user-accounts") "true")))
    (redirect "/login")

    :else
    (layout/public
      [:h1.registration "アカウント作成"]
      [:div#inner-body.registration
	      (if (errors? :email :pass :display-name :username)
	        [:div.message-validation
	         [:ul.message
	          (on-error :username     format-validation-message)
	          (on-error :pass         format-validation-message)
	          (on-error :email        format-validation-message)
	          (on-error :display-name format-validation-message)]])
       (if (nil? (db/get-user 1))
         [:div.message-info "作成されるアカウントは管理者アカウントになります。個人情報はMeriken's 2ch Browserが実行されているPCに保存され、ユーザーの許可がない限り外部には一切送信されません。"])
	      (form-to {:onsubmit "return regformhash(this, this.pass, this.pass1);"}
                [:post "/register"]
	               (text-field     {:id "registration-username"　
                                 :placeholder (str "ユーザー名(英数字" username-min-length "～" username-max-length "文字)")
                                 :maxlength username-max-length}
                                :username)
	               (password-field {:id "registration-pass"　:placeholder "パスワード"} :pass)
	               (password-field {:id "registration-pass1"　:placeholder "パスワード (確認)"} :pass1)
	               (text-field     {:id "registration-email"　:placeholder "メールアドレス" :maxlength email-max-length} :email)
	               (text-field     {:id "registration-display-name"　
                                 :placeholder (str "表示名(" display-name-min-length "～" display-name-max-length "文字)")
                                 :maxlength display-name-max-length}　
                                :display-name)
	               (submit-button
	                 {:id       "registration-button"}
                  "アカウント作成"))])))

(defn mobile-handle-registration [username p pp email display-name t]
  (cond
    (session/get :user)
    (redirect "/main")

    (and (db/get-user 1)
         (not (= (db/get-system-setting "allow-new-user-accounts") "true")))
    (redirect "/login")

    :else
    (do
      ; username
      (rule (has-value? username)
            [:username "ユーザー名を入力してください。"])
      (rule (not (db/get-user-with-username username))
            [:username "このユーザー名はすでに使用されています。"])
      (rule (re-find (re-pattern (str "^[a-zA-Z0-9_-]{" username-min-length "," username-max-length "}$")) username)
            [:username (str "ユーザー名は" username-min-length "文字から" username-max-length "文字までで、半角英字(a-zA-Z)、半角数字(0-9)、ダッシュ(-)、アンダースコア(_)のみが使用できます。")])
      ; pass
      (rule (and p
                 (re-find (re-pattern "^[0-9a-f]{128}$") p)
                 (= t (sha512 "test")))
            [:pass "このブラウザには対応していません。"])
      (rule (and (not (= p  (sha512 "")))
                 (not (= pp (sha512 ""))))
            [:pass "パスワードを入力してください。"])
      (if (and (not (= p  (sha512 "")))
               (not (= pp (sha512 ""))))
        (rule (= p pp) [:pass "パスワードが一致しません。"]))
      ; email
      (rule (has-value? email) [:email "メールアドレスを入力してください。"])
      (rule (<= (count email) email-max-length)
            [:email (str "メールアドレスは" email-max-length "文字までにしてください。")])
      (rule (not (db/get-user-with-email email))
            [:email "このメールアドレスはすでに使用されています。"])
      (if (has-value? email)
        (rule (re-find (re-pattern "^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,4}$") email)
              [:email "不正なメールアドレスです。"]))
      ; display-name
      (rule (has-value? display-name) [:display-name "表示名を入力してください。"])
      (if (has-value? display-name)
        (rule (re-find (re-pattern (str "^[^ 　]{" display-name-min-length "," display-name-max-length "}$")) display-name)
              [:display-name (str "表示名は" display-name-min-length "文字から" display-name-max-length "文字までにしてください。")]))

      (if (errors? :username :pass :email :display-name)
        (mobile-registration-page)
        (let [salt (random-string password-salt-length)]
          (db/create-user
            {:username     username
             :pass         (sha512 (str p salt))
             :salt         salt
             :email        email
             :display-name display-name})
          (redirect "/login?account-created=1"))))))

(defn mobile-login-page [return-to login-required admin-only account-created]
  ; (timbre/debug "login-page:" (session/get :user))
  (cond
    (session/get :user)
    (redirect (if return-to return-to "/mobile-main"))

    (nil? (db/get-user 1))
    (redirect "/mobile-register")

    :else
    (layout/mobile-public
      [:div {:data-role "page" :data-title "ログイン"}
       [:div {:role "main" :class "ui-content"}
        (if (= login-required "1")
          [:div.message-error "このページにアクセスするには、ログインする必要があります。"])
        (if (= admin-only "1")
          [:div.message-error "このページにアクセスするには、管理者としてログインする必要があります。"])
        (if (= account-created "1")
          [:div.message-info "新しいアカウントが作成されました。"])
        (if (errors? :email :pass)
          [:div.message-validation
           [:ul.message
            (on-error :email format-validation-message)
            (on-error :pass  format-validation-message)]])
        (form-to {:onsubmit "return formhash(this, this.pass);"}
                 [:post "/mobile-login"]
                 (text-field     {:id "login-email"    :placeholder "メールアドレス"} :email)
                 (password-field {:id "login-password" :placeholder "パスワード"}   :pass)
                 (hidden-field :return-to return-to)
                 (submit-button {:class "ui-shadow ui-btn ui-corner-all" :id "login-button"} "ログイン"))]])))

(defn mobile-handle-login
  [email p pp t tt return-to]
  (let [user (db/get-user-with-email email)
        p (if (vector? p) (last p) p)
        pp (if (vector? pp) (last pp) pp)
        t (if (vector? t) (last t) t)
        tt (if (vector? tt) (last tt) tt)]
    ; (timbre/debug "mobile-handle-login:" email p t return-to)
    (rule
      (or (and
            (re-find #"^[0-9a-f]{128}$" p)
            (= t (sha512 "test")))
          (and
            (re-find #"^[0-9a-f]{128}$" pp)
            (= tt (sha512 "test"))))
      [:pass "このブラウザには対応していません。"])
    (rule (has-value? email) [:email "メールアドレスを入力してください。"])
    (rule (not (and (= p (sha512 "")) (= pp (sha512 "")))) [:pass "パスワードを入力してください。"])
    (if (and (has-value? email)
             (not (and (= p (sha512 "")) (= pp (sha512 "")))))
      (rule (and user
                 (or (= (sha512 (str p (:salt user))) (:pass user))
                     (= (sha512 (str pp (:salt user))) (:pass user))))
            [:pass "メールアドレスかパスワードが間違っています。"]))
    (if (errors? :email :pass)
      (mobile-login-page return-to nil nil nil)
      (do
        (session/put! :user user)
        (session/put! :login-string (ring.util.response/get-header noir.request/*request* "user-agent"))
        (redirect (if (> (count return-to) 0) return-to "/mobile"))))))

(defn mobile-handle-logout [return-to admin-only]
  (session/clear!)
  (redirect (if return-to
              (str "/mobile-login"
                   "?return-to=" (url-encode return-to)
                   (if (= admin-only "1") "&admin-only=1" ""))
              "/mobile-login")))

(defroutes mobile-auth-routes
  (GET  "/mobile-register"
        []
        (mobile-registration-page))
  (POST "/mobile-register"
        [username p pp email display-name t]
        (mobile-handle-registration username p pp email display-name t))

  (GET  "/mobile-login"
        [return-to login-required admin-only account-created]
        (mobile-login-page return-to login-required admin-only account-created))
  (POST "/mobile-login"
        [email p pp t tt return-to]
        (mobile-handle-login email p pp t tt return-to))

  (GET "/mobile-logout"
       [return-to admin-only]
       (mobile-handle-logout return-to admin-only)))
