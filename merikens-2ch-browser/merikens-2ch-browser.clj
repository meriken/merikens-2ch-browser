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



(ns merikens-2ch-browser.param
  (:require [garden.core :refer [css]])
  (:import java.lang.Thread))

(def default-port 50000)

(def number-of-threads-for-web-server 512)
(def immutant-io-threads 32)
(def immutant-worker-threads number-of-threads-for-web-server)

(def number-of-threads-for-thread-list 16)
(def number-of-threads-for-thread-content 16)
(def number-of-threads-for-new-posts 8)

(def web-sever-thread-priority Thread/MAX_PRIORITY)

(def user-agent "Monazilla/1.00")

(def wait-time-for-downloading-subject-txt 120000)

(def default-maximum-count-for-mobile-board  50)
(def default-maximum-count-for-mobile-thread 20)

(def thumbnail-width 80)
(def thumbnail-height 80)

(def polling-interval-for-download-manager 100)
(def default-maximum-number-of-image-downloads 16)
(def maxinum-number-of-retries-for-image-downloads 40)

(def threshold-for-updating-new-post-counts 3000)
(def maximum-number-of-image-downloads-for-web-browser 4)
(def animation-duration-for-image-viewer 400)

(def left-panes
  [[:account-menu        :open]
   [:server-info         :open]
   [:favorite-boards     :open]
   [:special-menu        :open]
   [:2ch-net             :closed]
   [:2ch-sc              :closed]
   [:open2ch-net         :closed]
   ; [:machi-bbs          :closed]
   [:image-download-info :open]])



(def download-dat-files-from-2ch-net-and-bbspink-com false)
(def download-threads-in-html-from-2ch-net-and-bbspink-com true)
(def download-threads-in-json-from-2ch-net-and-bbspink-com true)



; プロキシの設定の例です。使用する際には"comment"を削除してください。
(comment defn proxy-server
  [url method]
  {:proxy-host "192.168.0.2" :proxy-port 8080})

(comment defn proxy-server
  [url method]
  {:proxy-host "192.168.0.2" :proxy-port 8080　 :proxy-user "USERNAME" :proxy-pass "PASSWORD"})

(comment defn proxy-server
  [url method]
  (if (= method :get)
    {:proxy-host "192.168.0.2" :proxy-port 8080} ; 読み込み用
    {:proxy-host "192.168.0.3" :proxy-port 8080} ; 書き込み用
    ))

(comment defn proxy-server
  [url method]
  (if (re-find #"^http://([a-z0-9-_]+\.(open2ch\.net)|mattari\.plusvip\.jp|bbs\.shingetsu\.info)/" url)
    {:proxy-host "192.168.0.2" :proxy-port 8080}
    nil))



; フォントを「MS Pゴシック」に変えるための設定です。使用する際には"comment"を削除してください。
; やっつけで作ったので色々ずれています。そのうちちゃんとつくり直す予定です。
(comment def css-pc
  (css [:body :input :button :textarea
        :div.thread-heading
        :div.message-error-right-pane :div.message-info-right-pane
        :div.id-in-heading :div.id-in-heading-label :div.id-in-heading-count
        :div.id-in-message :div.id-in-message-label :div.id-in-message-count
        :div.be-in-heading
        :div.anchor-in-message
        :div.reverse-anchors
        :div#thread-content
        :textarea#post-page-message
        { :font-family "'ＭＳ Ｐゴシック'"
         ; ここからはアンチエイリアスの設定。Chromeだとちらつきます。
         :-webkit-transform-origin "0 0"
         :-webkit-transform        "scale(1, 0.9995)"
         :-moz-transform-origin    "0 0"
         :-moz-transform           "scale(1, 0.9995)"
         :-ms-transform-origin     "0 0"
         :-ms-transform            "scale(1, 0.9995)"
         :-o-transform-origin      "0 0"
         :-o-transform             "scale(1, 0.9995)" }]
       [(keyword "input[type='checkbox'] + label.compact") { :height "18px" :padding-top "3px" }]
       [(keyword "input[type='checkbox'] + label > span") { :margin-top "3px" }]))

(def css-mobile nil)

(def use-net-api        false)
(def net-api-app-key    "")
(def net-api-hm-key     "")
(def net-api-x-2ch-ua   "")
(def net-api-user-agent "")

; 置換パターン
(def replacement-patterns-for-message-in-post
  [[#"<hr><b>Think different\? by 2ch.net/bbspink.com</b>" ""]])



; データベース
(ns merikens-2ch-browser.db.schema
  (:use korma.core [korma.db :only (create-db default-connection)]))

(when (or (hypersql-database-initialized?)
          (not (h2-database-initialized?)))
  (def db-spec hsqldb-db-spec)
  (default-connection (create-db hsqldb-db-spec))
  (def backup-db-spec hsqldb-backup-db-spec))

; H2
; (def db-spec h2-db-spec)
; (default-connection (create-db h2-db-spec))
; (def backup-db-spec h2-backup-db-spec)

; HyperSQL
; (def db-spec hsqldb-db-spec)
; (default-connection (create-db hsqldb-db-spec))
; (def backup-db-spec hsqldb-backup-db-spec)

; MySQL
; Add the following to my.ini.
;     [mysqld]
;     collation-server = utf8_unicode_ci
;     init-connect='SET NAMES utf8'
;     character-set-server = utf8
;     max_allowed_packet = 128M
(def mysql-db-spec (-> mysql-db-spec
                     (assoc :subname  "//127.0.0.1:3306/merikens_2ch_browser")
                     (assoc :user     "merikens_2ch_browser")
                     (assoc :password "")))
; (def db-spec mysql-db-spec)
; (default-connection (create-db mysql-db-spec))

; PostgreSQL
(def postgresql-db-spec (-> postgresql-db-spec
                          (assoc :subname  "//127.0.0.1:5432/merikens_2ch_browser")
                          (assoc :user     "merikens_2ch_browser")
                          (assoc :password "")))
; (def db-spec postgresql-db-spec)
; (default-connection (create-db postgresql-db-spec))
