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



(ns merikens-2ch-browser.routes.post
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [ring.handler.dump]
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
            [clj-http.client :as client]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db])
  (:import [org.apache.http.impl.cookie BasicClientCookie]))



(defn post-page
  [thread-url thread-title handle email message board-url board-name new-thread-tab]
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
    (layout/post
      (list
        (include-js-no-cache "/js/post.js")
        [:script "threadURL = " (if new-thread? "null" (str "'" thread-url "'")) ";"]
        (form-to
          [:post "/post"]
          [:div#popup-window-title (if new-thread? board-name thread-title)]
          (hidden-field "board-url" board-url)
          (hidden-field "board-name" board-name)
          (hidden-field "thread-url" thread-url)
          (hidden-field "new-thread-tab" (if new-thread-tab "1" "0"))
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
          (submit-button {:id "popup-window-submit-button"} (if new-thread? "新規スレッド作成" "書き込む")))))))

(defn post-to-2ch [& {:keys [server bbs key from mail message secret-name secret-value subject]}]
  (let [encoding (get-encoding-for-post-method server)
        ronin-session-id (if (and (or (re-find #"\.2ch\.net$" server)
                                      (re-find #"\.bbspink\.com$" server))
                                  (= (db/get-user-setting "use-ronin") "true"))
                           (get-ronin-session-id)
                           nil)
        request-body (if (or (re-find #"\.machi\.to$" server) (= server "jbbs.shitaraba.net"))
                       (str "BBS=" (if (= server "jbbs.shitaraba.net") (second (clojure.string/split bbs #"/")) bbs) "&"
                            (if (nil? key)
                              (str "SUBJECT=" (to-url-encoded-string subject encoding) "&")
                              (str "KEY=" key "&"))
                            "DIR=" (first (clojure.string/split bbs #"/")) "&"
                            "TIME=" (- (quot (clj-time.coerce/to-long (clj-time.core/now)) 1000) 300) "&"
                            "NAME=" (to-url-encoded-string from encoding) "&"
                            "MAIL=" (to-url-encoded-string mail encoding) "&"
                            "MESSAGE=" (to-url-encoded-string message encoding) "&"
                            "submit=" (to-url-encoded-string "書き込む" encoding)
                            (if (> (count secret-name) 0)
                              (str "&" secret-name "=" secret-value)))
                       (str "bbs=" bbs "&"
                            (if (nil? key)
                              (str "subject=" (to-url-encoded-string subject encoding) "&")
                              (str "key=" key "&"))
                            "time=" (- (quot (clj-time.coerce/to-long (clj-time.core/now)) 1000) 300) "&"
                            "FROM=" (to-url-encoded-string from encoding) "&"
                            "mail=" (to-url-encoded-string mail encoding) "&"
                            "MESSAGE=" (to-url-encoded-string message encoding) "&"
                            "submit=" (to-url-encoded-string "書き込む" encoding)
                            (if (> (count secret-name) 0)
                              (str "&" secret-name "=" secret-value))
                            (if ronin-session-id
                              (str "&sid=" ronin-session-id))))
        url      (cond
                   (= server "jbbs.shitaraba.net")
                   (str "http://" server "/bbs/write.cgi/" bbs "/" key "/")

                   (re-find #"\.machi\.to$" server)
                   (str "http://" server "/bbs/write.cgi?guid=ON")

                   :else
                   (str "http://" server "/test/bbs.cgi?guid=ON"))
        params   {:cookie-store (db/get-cookie-store)
                  :decode-body-headers true :as encoding
                  :headers {"Referer"      (str "http://" server "/test/read.cgi/" bbs "/" key "/")
                            "Content-Type" "application/x-www-form-urlencoded"
                            "User-Agent"   user-agent}
                  :body request-body
                  :body-encoding encoding}
        params   (merge params (proxy-server url :post))
        response (client/post url params)
        body     (:body response)
        title    (nth (re-find #"<title>(.*)</title>" body) 1 "")]
    (db/update-cookie-store (:cookie-store params))
    {:result (cond
               (or (re-find #"書きこみました" title) (re-find #"<!-- 2ch_X:true -->" body))           :success
               (or (re-find #"ＥＲＲＯＲ"    title) (re-find #"<!-- 2ch_X:error -->" body))          :error
               (or (re-find #"お茶でも"     title))                                                 :busy
               (or (re-find #"<!-- 2ch_X:false -->" body) (re-find #"<!-- 2ch_X:check -->" body)) :warning ; What is this?
               (or (re-find #"書き込み確認" title) (re-find #"<!-- 2ch_X:cookie -->" body))         :confirmation
               :else :unknown)
     :body (:body response)}))

(defn post-to-2ch-through-p2 [& {:keys [server bbs key from mail message subject]}]
  (try

    ; login
    (let [encoding (get-encoding-for-post-method server)
          p2-email    (db/get-user-setting "p2-email")
          p2-password (db/get-user-setting "p2-password")
          request-body (str "form_login_id=" p2-email  "&"
                            "form_login_pass=" p2-password "&"
                            "ctl_register_cookie=1&"
                            "register_cookie=1&"
                            "check_cip=0&"
                            "submit_userlogin=" (to-url-encoded-string "ログイン" encoding))
          params {:cookie-store (db/get-cookie-store)
                  :decode-body-headers true :as encoding
                  :headers {"Content-Type" "application/x-www-form-urlencoded"
                            "User-Agent"   user-agent
                            "Referer"      (if (sc-server? server) "http://p2.2ch.sc/p2/index.php" "http://p2.open2ch.net/")}
                  :body request-body
                  :body-encoding "Shift_JIS"}
          url     (if (sc-server? server) "http://p2.2ch.sc/p2/" "http://p2.open2ch.net/")
          params   (merge params (proxy-server url :post))
          login-response (client/post url params)]

      (db/update-cookie-store (:cookie-store params))
      (if (or (not (= (:status login-response) 200))
              (re-find #"p2 error: 認証できませんでした。" (:body login-response))
              (not (re-find #"<title>p2</title>" (:body login-response))))
        {:result :error :body (str (escape-html (:body login-response)) "p2の認証に失敗しました。")}

        ; post form
        (let [params {:cookie-store (db/get-cookie-store)
                      :decode-body-headers true :as encoding
                      :headers {"Content-Type" "application/x-www-form-urlencoded"
                                "User-Agent"   user-agent
                                "Referer"      "http://p2.2ch.sc/p2/read.php"}}
              url    (str  (if (sc-server? server) "http://p2.2ch.sc/p2/post_form.php?" "http://p2.open2ch.net/post_form.php?")
                           "host=" server
                           "&bbs=" bbs
                           "&key=" key)
              params   (merge params (proxy-server url :post))
              post-form-response (client/post url params)]

          (db/update-cookie-store (:cookie-store params))
          (if (or (not (= (:status post-form-response) 200))
                  (re-find #"p2 error" (:body post-form-response)))
            {:result :error :body (:body post-form-response)}

            ; post
            (let [csrfid       (nth (re-find #"<input type=\"hidden\" name=\"csrfid\" value=\"([0-9a-f]+)\">" (:body post-form-response)) 1 "")
                  request-body (str "detect_hint=" (to-url-encoded-string "◎◇" encoding) "&"
                                    "FROM=" (to-url-encoded-string from encoding) "&"
                                    "mail=" (to-url-encoded-string mail encoding) "&"
                                    "MESSAGE=" (to-url-encoded-string message encoding) "&"
                                    "submit=" (to-url-encoded-string "書き込む" encoding) "&"

                                    "bbs=" bbs "&"
                                    (if (nil? key)
                                      (str "subject=" (to-url-encoded-string subject encoding) "&"
                                           "newthread=1&"
                                           "key=&")
                                      (str "key=" key "&"))
                                    "time=" (- (quot (clj-time.coerce/to-long (clj-time.core/now)) 1000) 300) "&"
                                    "host=" server "&"
                                    "csrfid=" csrfid)
                  params {:cookie-store (db/get-cookie-store)
                          :decode-body-headers true :as encoding
                          :headers {"Referer"      (str "http://" server "/test/read.cgi/" bbs "/" key "/")
                                    "Content-Type" "application/x-www-form-urlencoded"
                                    "User-Agent"   user-agent}
                          :body request-body
                          :body-encoding encoding}
                  url      (if (sc-server? server) "http://p2.2ch.sc/p2/post.php?guid=ON" "http://p2.open2ch.net/post.php?guid=ON")
                  params   (merge params (proxy-server url :post))
                  post-response (client/post url params)]

              (db/update-cookie-store (:cookie-store params))
              (if (and (= (:status post-response) 200)
                       (re-find #"<title>p2 - 書きこみました。</title>" (:body post-response)))
                {:result :success :body (:body post-response)}
                {:result :error   :body (:body post-response)}))))))

    (catch Throwable t
      (timbre/debug "Unexpected exception:" (str t))
      {:result :error :body ""})))

(defn handle-post
  [thread-title thread-url handle email message secret-name secret-value board-url board-name new-thread-tab]
  (let [new-thread? (> (count board-url) 0)
        parts (if new-thread? (split-board-url board-url) (split-thread-url thread-url))]
    ; (timbre/debug parts)
    ; (timbre/debug "'" handle "', '" email "', '" message "'")
    (if (not parts)
      (ring.util.response/not-found "404 Not Found")
      (let [message (clojure.string/replace message #"\u301C" "\uFF5E") ; for Safari for OS X
            {:keys [service server board thread]} parts
            {result :result, body :body} (if (and (or (sc-server? server)
                                                      ; (open-server? server)
                                                      )
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
            (layout/post
              (list [:div
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
                    )))

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
            (layout/post
              (list [:div.message-success message-from-server]
                    [:div {:style "width: 100%; text-align: center; margin-top: 12px;"}
                     (javascript-button "閉じる" "close();")]
                    [:script
                     "window.resizeBy(0, $('html').height() - $(window).innerHeight());"
                     "setTimeout(function() { close(); }, 4000);"]
                    ; [:br] [:br] (escape-html body)
                    (if new-thread?
                      [:script
                       "setTimeout(function() {"
                       "opener.refreshThreadList();"
                       "}, 3000);"]
                      [:script
                       "setTimeout(function() {"
                       "opener.updateThreadContent(null, '" thread-url "', '', '', " (if new-thread-tab "true" "false") ");"
                       "}, 1000);"]))))

          ; capcha (2ch.sc)
          (re-find #"http://[a-z0-9]+.2ch.sc/test/jail.cgi" body)
          (let [new-page (re-find #"http://[a-z0-9]+.2ch.sc/test/jail.cgi\?hash=[0-9a-f]+" body)]
            (html [:script "open(decodeURIComponent('" (ring.util.codec/url-encode new-page) "'), '_self');"]))

          :else
          (layout/post
            (list
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
                   ; (str "open('/post?" parameters "', '_self');") ; window.opener does not work
                   "window.history.go(-(window.history.length - 1));"))]
              ; [:br] [:br] (escape-html body)
              )))))))

(defn api-autosave-draft
  [thread-url handle email draft]
  (let [parts (split-thread-url thread-url)]
    (if (or (not (check-login)) (nil? parts))
      (ring.util.response/not-found "404 Not Found")
      (let
        [{:keys [service server board thread]} (split-thread-url thread-url)]
        (db/update-last-handle service board thread handle)
        (db/update-last-email service board thread email)
        (db/update-autosaved-draft service board thread draft)
        "OK"))))

(defroutes post-routes
  (GET "/post"
       [thread-url thread-title handle email message board-url board-name new-thread-tab]
       (post-page thread-url thread-title handle email message board-url board-name (= new-thread-tab "1")))
  (POST "/post"
        [thread-title thread-url handle email message secret-name secret-value board-url board-name new-thread-tab]
        (handle-post thread-title (trim thread-url) handle email message secret-name secret-value (trim board-url) board-name (= new-thread-tab "1")))

  (GET "/api-autosave-draft"
       [thread-url handle email draft]
       (api-autosave-draft thread-url handle email draft)))
