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



(System/setProperties
  (doto (java.util.Properties. (System/getProperties))
    (.put "com.mchange.v2.log.MLog" "com.mchange.v2.log.FallbackMLog")
    (.put "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL" "OFF")))

(ns merikens-2ch-browser.db.core
  (:use korma.core
        [korma.db :only (defdb transaction)])
  (:require [clojure.java.jdbc :as sql]
            [clojure.stacktrace :refer [print-stack-trace]]
            [noir.session :as session]
            [taoensso.timbre :as timbre]
            [taoensso.nippy :as nippy]
            [clj-time.core]
            [clj-time.coerce]
            [merikens-2ch-browser.db.schema :as schema]
            [merikens-2ch-browser.param :refer :all])
  (:import java.security.MessageDigest
           com.mchange.v2.c3p0.ComboPooledDataSource))



(defdb db schema/db-spec)

(defn convert-citext-into-string
  [row]
  (let [k (keys row) v (vals row)]
    (zipmap k
            (map #(if (and (= (Class/forName "org.postgresql.util.PGobject") (type %1))
                           (= (.getType %1) "citext"))
                    (.getValue %1)
                    %1)
                 v))))

(defentity users             (transform convert-citext-into-string))
(defentity dat_files         (transform convert-citext-into-string))
(defentity threads_in_html   (transform convert-citext-into-string))
(defentity threads_in_json   (transform convert-citext-into-string))
(defentity favorite_threads  (transform convert-citext-into-string))
(defentity thread_info       (transform convert-citext-into-string))
(defentity board_info        (transform convert-citext-into-string))
(defentity bookmarks         (transform convert-citext-into-string))
(defentity favorite_boards   (transform convert-citext-into-string))
(defentity images            (transform convert-citext-into-string))
(defentity downloads         (transform convert-citext-into-string))
(defentity user_settings     (transform convert-citext-into-string))
(defentity system_settings   (transform convert-citext-into-string))
(defentity post_filters      (transform convert-citext-into-string))
(defentity images_extra_info (transform convert-citext-into-string))



(defn underscore-to-dash-in-keys
  "Replaces underscores in keys in maps to dashes."
  [m]
  (let [f (fn [[k v]]
            (if (keyword? k)
              [(keyword (clojure.string/replace (subs (str k) 1) "_" "-")) v]
              [k v]))]
    (clojure.walk/postwalk (fn [x]
                             (if (map? x)
                               (into {} (map f x))
                               x))
                           m)))

(defn dash-to-underscore-in-keys
  "Replaces dashes in keys in maps w underscores."
  [m]
  (let [f (fn [[k v]]
            (if (keyword? k)
              [(keyword (clojure.string/replace (subs (str k) 1) "-" "_")) v]
              [k v]))]
    (clojure.walk/postwalk (fn [x]
                             (if (map? x)
                               (into {} (map f x))
                               x))
                           m)))

(defn shutdown
  []
  (try
    ; (clojure.java.jdbc/db-do-commands schema/db-spec "CHECKPOINT SYNC")
    ; (clojure.java.jdbc/db-do-commands schema/db-spec "SET TRACE_LEVEL_SYSTEM_OUT 0; SHUTDOWN")
    (clojure.java.jdbc/db-do-commands schema/db-spec "SHUTDOWN")

    (catch Throwable t
      (timbre/debug "shutdown: Unexpected exception:" (str t)))))

(def count-keyword
  (fn [result]
    (cond
      ((keyword "count(*)") result)
      ((keyword "count(*)") result)

      (:c1 result)
      (:c1 result)

      (:count result)
      (:count result)

      :else
      nil)))

(defn byte-array?
  [a]
  (= (Class/forName "[B") (type a)))

(defn prepare-thread-no
  [thread-no]
  (if (string? thread-no)
    (java.lang.Long/parseLong thread-no)
    thread-no))



;;;;;;;;;
; USERS ;
;;;;;;;;;

(defn create-user [user]
  (insert users
          (values (dash-to-underscore-in-keys user))))

(defn get-user [id]
  (underscore-to-dash-in-keys (first (select users
                                             (where {:id id})
                                             (limit 1)))))

(defn get-user-with-username [username]
  (underscore-to-dash-in-keys (first (select users
                                             (where {:username username})
                                             (limit 1)))))

(defn get-user-with-email [email]
  (underscore-to-dash-in-keys (first (select users
                                             (where {:email email})
                                             (limit 1)))))

(defn get-cookie-store
  []
  (let [user (-> (select users (where {:id (:id (session/get :user))}) (limit 1))
               (first)
               (underscore-to-dash-in-keys))]
    (if (:cookie-store user)
      (nippy/thaw (if (byte-array? (:cookie-store user))
                    (:cookie-store user)
                    (.getBytes (:cookie-store user) 1 (int (.length (:cookie-store user))))))
      (clj-http.cookies/cookie-store))))

(defn update-cookie-store
  [cookie-store]
  (update users
          (set-fields {:cookie_store (nippy/freeze cookie-store)})
          (where {:id (:id (session/get :user))})))

(defn dump-users-table
  []
  (timbre/debug (select users)))

(defn get-all-users
  []
  (map underscore-to-dash-in-keys (select users
                                          (order :id))))



;;;;;;;;;;;;;
; DAT FILES ;
;;;;;;;;;;;;;

(defn get-dat-file
  [service board thread-no]
  (let [dat-file (-> (select dat_files
                             (where {:thread_no (prepare-thread-no thread-no)
                                     :user_id   (:id (session/get :user))
                                     :service   service
                                     :board     board})
                             (limit 1))
                   (first)
                   (underscore-to-dash-in-keys))]
    ; (timbre/debug dat-file)
    (cond
      (nil? dat-file)
      nil

      (byte-array? (:content dat-file))
      dat-file

      :else
      (let [blob (:content dat-file)]
        (assoc dat-file :content (.getBytes blob 1 (int (.length blob))))))))

(defn get-dat-file-without-content
  [service board thread-no]
  (-> (select dat_files
              (fields :user_id :service :server :board :thread_no :etag :last_modified :size)
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id   (:id (session/get :user))
                      :service   service
                      :board     board})
              (limit 1))
    (first)
    (underscore-to-dash-in-keys)))

(defn get-dat-file-with-user-id-without-content
  [user-id service board thread-no]
  ; (println "get-dat-file-with-user-id-without-content:" user-id service board thread-no)
  (-> (select dat_files
              (fields :user_id :service :server :board :thread_no :etag :last_modified :size)
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id   user-id
                      :service   service
                      :board     board})
              (limit 1))
    (first)
    (underscore-to-dash-in-keys)))

(defn add-dat-file
  [dat-file]
  (insert dat_files
          (values (dash-to-underscore-in-keys dat-file))))

(defn update-dat-file
  [service board thread-no etag last-modified content size res-count]
  (update dat_files
          (set-fields {:etag          etag
                       :last_modified last-modified
                       :content       content
                       :size          size
                       :res_count     res-count})
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn delete-dat-file
  [service board thread-no]
  (delete dat_files
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn get-all-dat-files
  []
  (map underscore-to-dash-in-keys (select dat_files)))

(defn get-dat-file-list
  []
  (map underscore-to-dash-in-keys (select dat_files
                                          (fields :user_id
                                                  :service
                                                  :server
                                                  :board
                                                  :thread_no
                                                  :etag
                                                  :last_modified
                                                  ; :content
                                                  :size
                                                  :res_count
                                                  :source_url)
                                          (where {:user_id   (:id (session/get :user))}))))

(defn get-dat-file-list-for-board
  [service board]
  (map underscore-to-dash-in-keys (select dat_files
                                          (fields :user_id
                                                  :service
                                                  :server
                                                  :board
                                                  :thread_no
                                                  :etag
                                                  :last_modified
                                                  ; :content
                                                  :size
                                                  :res_count
                                                  :source_url)
                                          (where {:user_id (:id (session/get :user)),
                                                  :service service,
                                                  :board   board}))))



;;;;;;;;;;;;;;;;;;
; THREAD IN HTML ;
;;;;;;;;;;;;;;;;;;

(defn get-thread-in-html
  [service board thread-no]
  (-> (select threads_in_html
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id   (:id (session/get :user))
                      :service   service
                      :board     board})
              (limit 1))
    (first)
    (underscore-to-dash-in-keys)))

(defn add-thread-in-html
  [thread-in-html]
  (insert threads_in_html
          (values (dash-to-underscore-in-keys thread-in-html))))

(defn update-thread-in-html
  [service board thread-no content res-count]
  (update threads_in_html
          (set-fields {:content       content
                       :res_count     res-count})
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn delete-thread-in-html
  [service board thread-no]
  (delete threads_in_html
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn dump-threads-in-html-table
  []
  (timbre/debug (select threads_in_html)))

(defn get-all-threads-in-html
  []
  (map underscore-to-dash-in-keys (select threads_in_html)))

(defn get-html-file-list
  []
  (map underscore-to-dash-in-keys (select threads_in_html
                                          (fields :user_id
                                                  :service
                                                  :server
                                                  :board
                                                  :thread_no
                                                  ; :content
                                                  :res_count
                                                  :source_url)
                                          (where {:user_id   (:id (session/get :user))}))))

(defn get-html-file-list-for-board
  [service board]
  (map underscore-to-dash-in-keys (select threads_in_html
                                          (fields :user_id
                                                  :service
                                                  :server
                                                  :board
                                                  :thread_no
                                                  ; :content
                                                  :res_count
                                                  :source_url)
                                          (where {:user_id (:id (session/get :user)),
                                                  :service service,
                                                  :board   board}))))



;;;;;;;;;;;;;;;;;;
; THREAD IN JSON ;
;;;;;;;;;;;;;;;;;;

(defn get-thread-in-json
  [service board thread-no]
  (-> (select threads_in_json
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id   (:id (session/get :user))
                      :service   service
                      :board     board})
              (limit 1))
    (first)
    (underscore-to-dash-in-keys)))

(defn add-thread-in-json
  [thread-in-json]
  (insert threads_in_json
          (values (dash-to-underscore-in-keys thread-in-json))))

(defn update-thread-in-json
  [service board thread-no content res-count]
  (update threads_in_json
          (set-fields {:content       content
                       :res_count     res-count})
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn delete-thread-in-json
  [service board thread-no]
  (delete threads_in_json
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn dump-threads-in-json-table
  []
  (timbre/debug (select threads_in_json)))

(defn get-all-threads-in-json
  []
  (map underscore-to-dash-in-keys (select threads_in_json)))

(defn get-json-file-list
  []
  (map underscore-to-dash-in-keys (select threads_in_json
                                          (fields :user_id
                                                  :service
                                                  :server
                                                  :board
                                                  :thread_no
                                                  ; :content
                                                  :res_count
                                                  :source_url)
                                          (where {:user_id   (:id (session/get :user))}))))

(defn get-json-file-list-for-board
  [service board]
  (map underscore-to-dash-in-keys (select threads_in_json
                                          (fields :user_id
                                                  :service
                                                  :server
                                                  :board
                                                  :thread_no
                                                  ; :content
                                                  :res_count
                                                  :source_url)
                                          (where {:user_id (:id (session/get :user)),
                                                  :service service,
                                                  :board   board}))))



;;;;;;;;;;;;;;
; BOARD INFO ;
;;;;;;;;;;;;;;

(defn update-board-name
  [service server board board-name]
  (transaction
    (if (< 0 (count (select board_info
                            (where {:board   board
                                    :service service}))))
      (update board_info
              (set-fields {:board_name board-name})
              (where {:service   service
                      :board     board}))
      (insert board_info
              (values {:service   service
                       :server    server
                       :board     board
                       :board_name board-name})))))

(defn update-board-server
  [service server board]
  (transaction
    (if (< 0 (count (select board_info
                            (where {:board     board
                                    :service   service}))))
      (update board_info
              (set-fields {:server server})
              (where {:service   service
                      :board     board}))
      (insert board_info
              (values {:service   service
                       :server    server
                       :board     board})))))

(defn update-subject-txt
  [service server board subject-txt]
  (transaction
    (if (< 0 (count (select board_info
                            (where {:board     board
                                    :service   service}))))
      (update board_info
              (set-fields {:subject_txt subject-txt
                           :time_subject_txt_retrieved (clj-time.coerce/to-sql-time (clj-time.core/now))})
              (where {:service   service
                      :board     board}))
      (insert board_info
              (values {:service   service
                       :server    server
                       :board     board
                       :subject_txt subject-txt
                       :time_subject_txt_retrieved (clj-time.coerce/to-sql-time (clj-time.core/now))})))))

(defn delete-subject-txt
  [service board]
  (transaction
    (if (< 0 (count (select board_info
                            (where {:board     board
                                    :service   service}))))
      (update board_info
              (set-fields {:subject_txt nil
                           :time_subject_txt_retrieved nil})
              (where {:service   service
                      :board     board})))))

(defn update-board-server-if-there-is-no-info
  [service server board]
  (transaction
    (if (>= 0 (count (select board_info
                             (where {:board     board
                                     :service   service}))))
      (insert board_info
              (values {:service   service
                       :server    server
                       :board     board})))))

(defn get-board-info
  [service board]
  (underscore-to-dash-in-keys (first (select board_info
                                             (where {:board     board
                                                     :service   service})))))

(defn get-server
  [service board]
  (:server (get-board-info service board)))

(defn get-all-board-info
  []
  (map underscore-to-dash-in-keys (select board_info)))



;;;;;;;;;;;;;;;
; THREAD_INFO ;
;;;;;;;;;;;;;;;

(defn update-thread-title
  [service server board thread-no title]
  (transaction
    (if (< 0 (count (select thread_info
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :service   service
                                    :board     board}))))
      (update thread_info
              (set-fields {:title title})
              (where {:service   service
                      :board     board
                      :thread_no (prepare-thread-no thread-no)}))
      (insert thread_info
              (values {:service   service
                       :server    server
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :title     title})))))

(defn mark-thread-as-archived
  [service server board thread-no]
  (transaction
    (if (< 0 (count (select thread_info
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :service   service
                                    :board     board}))))
      (update thread_info
              (set-fields {:archived true})
              (where {:service   service
                      :board     board
                      :thread_no (prepare-thread-no thread-no)}))
      (insert thread_info
              (values {:service   service
                       :server    server
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :archived  true})))))

(defn mark-thread-as-active
  [service server board thread-no]
  (transaction
    (if (< 0 (count (select thread_info
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :service   service
                                    :board     board}))))
      (update thread_info
              (set-fields {:archived false})
              (where {:service   service
                      :board     board
                      :thread_no (prepare-thread-no thread-no)}))
      (insert thread_info
              (values {:service   service
                       :server    server
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :archived  false})))))

(defn update-thread-res-count
  "Update the number of posts in database for the specified thread
   if and only if res-count is greater than the value in database."
  [service server board thread-no res-count]
  ; (timbre/debug "update-thread-res-count" service server board thread-no res-count)
  (if (not (nil? res-count))
    (let [info (nth (select thread_info
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :service   service
                                    :board     board}))
                    0
                    nil)]
      (cond
        (nil? info)
        (insert thread_info
                (values {:service   service
                         :server    server
                         :board     board
                         :thread_no (prepare-thread-no thread-no)
                         :res_count res-count}))

        (or (nil? (:res_count info)) (> res-count (:res_count info)))
        (update thread_info
                (set-fields {:res_count res-count})
                (where {:thread_no (prepare-thread-no thread-no)
                        :service   service
                        :board     board}))))))

(defn dump-thread-info-table
  []
  (timbre/debug (select thread_info)))

(defn get-thread-info
  [service board thread-no]
  (underscore-to-dash-in-keys (first (select thread_info
                                             (where {:thread_no (prepare-thread-no thread-no)
                                                     :service   service
                                                     :board     board})))))

(defn get-bookmark-list-for-board
  [service board]
  (map underscore-to-dash-in-keys (select bookmarks
                                          (where {:board     board
                                                  :service   service})
                                          (order :thread_no :DESC)
                                          (limit 3000))))

(defn delete-thread-info
  [service board thread-no]
  (delete thread_info
          (where {:thread_no (prepare-thread-no thread-no)
                  :service   service
                  :board     board})))

(defn get-all-thread-info
  []
  (map underscore-to-dash-in-keys (select thread_info)))



;;;;;;;;;;;;;
; BOOKMARKS ;
;;;;;;;;;;;;;

(defn update-bookmark
  [service board thread-no bookmark]
  ; (timbre/debug "update-bookmark:" service board thread-no bookmark)
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id   (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:bookmark bookmark})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :bookmark  bookmark})))))

(defn update-bookmark-with-user-id
  [user-id service board thread-no bookmark]
  ; (timbre/debug "update-bookmark:" service board thread-no bookmark)
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id   user-id
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:bookmark bookmark})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id   user-id
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   user-id
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :bookmark  bookmark})))))

(defn get-bookmark
  [service board thread-no]
  ; (timbre/debug "get-bookmark:" service board thread-no)
  (:bookmark (first (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id   (:id (session/get :user))
                                    :service   service
                                    :board     board})))))

(defn update-time-last-viewed
  [service board thread-no time-last-viewed]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:time_last_viewed time-last-viewed})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :time_last_viewed time-last-viewed})))))

(defn update-time-last-viewed-with-user-id
  [user-id service board thread-no time-last-viewed]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id   user-id
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:time_last_viewed time-last-viewed})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id   user-id
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   user-id
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :time_last_viewed time-last-viewed})))))

(defn get-time-last-viewed
  [service board thread-no]
  (:time_last_viewed (first (select bookmarks
                                    (where {:thread_no (prepare-thread-no thread-no)
                                            :user_id   (:id (session/get :user))
                                            :service   service
                                            :board     board})))))

(defn get-recently-viewed-threads
  []
  (underscore-to-dash-in-keys (select bookmarks
                                      (where {:user_id (:id (session/get :user))
                                              :time_last_viewed [not= nil]})
                                      (order :time_last_viewed :DESC)
                                      (limit 30))))

(defn get-recently-posted-threads
  []
  (underscore-to-dash-in-keys (select bookmarks
                                      (where {:user_id (:id (session/get :user))
                                              :time_last_posted [not= nil]})
                                      (order :time_last_posted :DESC)
                                      (limit 30))))

(defn update-time-last-posted
  [service board thread-no time-last-posted]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:time_last_posted time-last-posted})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :time_last_posted time-last-posted})))))

(defn get-time-last-posted
  [service board thread-no]
  (:time_last_posted (first (select bookmarks
                                    (where {:thread_no (prepare-thread-no thread-no)
                                            :user_id   (:id (session/get :user))
                                            :service   service
                                            :board     board})))))

(defn update-last-handle
  [service board thread-no last-handle]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:last_handle last-handle})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :last_handle last-handle})))))

(defn get-last-handle
  [service board thread-no]
  (:last_handle (first (select bookmarks
                               (where {:thread_no (prepare-thread-no thread-no)
                                       :user_id   (:id (session/get :user))
                                       :service   service
                                       :board     board})))))

(defn update-last-email
  [service board thread-no last-email]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:last_email last-email})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :last_email last-email})))))

(defn get-last-email
  [service board thread-no]
  (:last_email (first (select bookmarks
                              (where {:thread_no (prepare-thread-no thread-no)
                                      :user_id   (:id (session/get :user))
                                      :service   service
                                      :board     board})))))

(defn update-autosaved-draft
  [service board thread-no autosaved-draft]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:autosaved_draft autosaved-draft})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :autosaved_draft autosaved-draft})))))

(defn get-autosaved-draft
  [service board thread-no]
  (:autosaved_draft (first (select bookmarks
                                   (where {:thread_no (prepare-thread-no thread-no)
                                           :user_id   (:id (session/get :user))
                                           :service   service
                                           :board     board})))))

(defn update-draft
  [service board thread-no draft]
  (transaction
    (if (< 0 (count (select bookmarks
                            (where {:thread_no (prepare-thread-no thread-no)
                                    :user_id (:id (session/get :user))
                                    :service   service
                                    :board     board}))))
      (update bookmarks
              (set-fields {:draft draft})
              (where {:thread_no (prepare-thread-no thread-no)
                      :user_id (:id (session/get :user))
                      :service   service
                      :board     board}))
      (insert bookmarks
              (values {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board
                       :thread_no (prepare-thread-no thread-no)
                       :draft draft})))))

(defn get-draft
  [service board thread-no]
  (:draft (first (select bookmarks
                         (where {:thread_no (prepare-thread-no thread-no)
                                 :user_id   (:id (session/get :user))
                                 :service   service
                                 :board     board})))))

(defn get-bookmarks-for-board
  [service board]
  (map underscore-to-dash-in-keys
       (select bookmarks
               (where {:user_id   (:id (session/get :user))
                       :service   service
                       :board     board}))))

(defn delete-bookmark
  [service board thread-no]
  (delete bookmarks
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn get-all-bookmarks
  []
  (map underscore-to-dash-in-keys (select bookmarks)))



;;;;;;;;;;;;;;;;;;
; FAVORITE BOARD ;
;;;;;;;;;;;;;;;;;;

(defn get-favorite-boards []
  (map underscore-to-dash-in-keys
       (select favorite_boards
               (where {:user_id (:id (session/get :user))}))))

(defn get-favorite-board
  [service board]
  (underscore-to-dash-in-keys (first (select favorite_boards
                                             (where {:user_id   (:id (session/get :user))
                                                     :service   service
                                                     :board     board})))))

(defn get-favorite-board-with-user-id
  [user-id service board]
  (underscore-to-dash-in-keys (first (select favorite_boards
                                             (where {:user_id   user-id
                                                     :service   service
                                                     :board     board})))))

(defn add-favorite-board
  [favorite-board]
  (transaction
    (if (nil? (get-favorite-board-with-user-id (:user-id favorite-board) (:service favorite-board) (:board favorite-board)))
      (insert favorite_boards
              (values (dash-to-underscore-in-keys favorite-board))))))

(defn delete-favorite-board
  [service board]
  (delete favorite_boards
          (where {:user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn delete-all-favorite-boards
  []
  (delete favorite_boards
          (where {:user_id (:id (session/get :user))})))

;(defn get-all-favorite-boards
;  []
;  (map underscore-to-dash-in-keys (select favorite_boards)))



;;;;;;;;;;;;;;;;;;;
; FAVORITE THREAD ;
;;;;;;;;;;;;;;;;;;;

(defn get-favorite-threads []
  (map underscore-to-dash-in-keys
       (select favorite_threads
               (where {:user_id (:id (session/get :user))}))))

(defn get-favorite-thread
  [service board thread-no]
  (underscore-to-dash-in-keys (first (select favorite_threads
                                             (where {:thread_no (prepare-thread-no thread-no)
                                                     :user_id   (:id (session/get :user))
                                                     :service   service
                                                     :board     board})))))

(defn get-favorite-thread-with-user-id
  [user-id service board thread-no]
  (underscore-to-dash-in-keys (first (select favorite_threads
                                             (where {:thread_no (prepare-thread-no thread-no)
                                                     :user_id   user-id
                                                     :service   service
                                                     :board     board})))))

(defn add-favorite-thread
  [favorite-thread]
  (transaction
    (if (nil? (get-favorite-thread-with-user-id (:user-id favorite-thread)
                                                (:service favorite-thread)
                                                (:board favorite-thread)
                                                (:thread-no favorite-thread)))
      (insert favorite_threads
              (values (dash-to-underscore-in-keys favorite-thread))))))

(defn delete-favorite-thread
  [service board thread-no]
  (delete favorite_threads
          (where {:thread_no (prepare-thread-no thread-no)
                  :user_id   (:id (session/get :user))
                  :service   service
                  :board     board})))

(defn dump-favorite-threads-table
  []
  (timbre/debug (select favorite_threads)))

(defn get-all-favorite-threads
  []
  (map underscore-to-dash-in-keys (select favorite_threads)))



;;;;;;;;;;
; IMAGES ;
;;;;;;;;;;

(defn create-standard-md5-string
  [binary-array]
  (let [hash-string (.toString (BigInteger. 1
                                                                       (let [m (MessageDigest/getInstance "MD5")]
                                                                         (.reset m)
                                                                         (.update m binary-array)
                                                                         (.digest m)
                                                                         ))
                                                 16)
        hash-string-length ( count hash-string)]
    (str (apply str (repeat (if (< hash-string-length 26) (- 26 hash-string-length) 0) "0"))
         hash-string)))

(defn nth-unsigned
  [a i]
  (let [value (int (nth a i 0))]
    (if (< value 0) (+ 256 value) value)))

(defn create-md5-string
  [binary-array]
  (let [digest (let [m (MessageDigest/getInstance "MD5")]
                                                                         (.reset m)
                                                                         (.update m binary-array)
                                                                         (.digest m)
                                                                         )
        i 1]
    (apply str (map #(.charAt
                       "0123456789ABCDEFGHIJKLMNOPQRSTUV"
                       (bit-and
                         (bit-shift-right
                           (bit-or
                             (bit-shift-left
                               (nth-unsigned digest (inc (quot (* % 5) 8))) 8)
                             (nth-unsigned digest (quot (* % 5) 8)))
                           (rem (* % 5) 8))
                         31))
                    (range 0 26)))))

(defn get-image-with-url
  [url]
  (let [image (-> (select images
                          (where {:url url
                                  ;:user_id   (:id (session/get :user))
                                  })
                          (limit 1))
                (first)
                (underscore-to-dash-in-keys))]
    (cond
      (nil? image)
      nil

      (byte-array? (:content image))
      image

      :else
      (let [blob (:content image)
            thumbnail-blob (:thumbnail image)]
        (-> image
          (assoc :content   (.getBytes blob           1 (int (.length blob))))
          (assoc :thumbnail (.getBytes thumbnail-blob 1 (int (.length thumbnail-blob)))))))))

(defn get-image-with-url-without-content
  [url]
  (let [image (-> (select images
                          (fields :id :user_id :thread_url :url :extension :size :width :height :time_retrieved :thumbnail)
                          (where {:url url
                                  :user_id   (:id (session/get :user))})
                          (limit 1))
                (first)
                (underscore-to-dash-in-keys))]
    (cond
      (nil? image)
      nil

      (byte-array? (:thumbnail image))
      image

      :else
      (let [thumbnail-blob (:thumbnail image)]
        (assoc image :thumbnail (.getBytes thumbnail-blob 1 (int (.length thumbnail-blob))))))))

(defn get-image-with-url-without-content-and-thumbnail
  [url]
  (-> (select images
              (fields :id :user_id :thread_url :url :extension :size :width :height :time_retrieved)
              (where {:url     url
                      :user_id (:id (session/get :user))})
              (limit 1))
    (first)
    (underscore-to-dash-in-keys)))

(defn get-image-with-user-id-and-url-without-content-and-thumbnail
  [user-id url]
  (-> (select images
              (fields :id :user_id :thread_url :url :extension :size :width :height :time_retrieved)
              (where {:url     url
                      :user_id user-id})
              (limit 1))
    (first)
    (underscore-to-dash-in-keys)))

(defn get-image-with-user-id-and-url
  [user-id url]
  (let [image (-> (select images
                          (where {:url url
                                  :user_id user-id})
                          (limit 1))
                (first)
                (underscore-to-dash-in-keys))]
    (cond
      (nil? image)
      nil

      (byte-array? (:content image))
      image

      :else
      (let [blob (:content image)
            thumbnail-blob (:thumbnail image)]
        (-> image
          (assoc :content   (.getBytes blob           1 (int (.length blob))))
          (assoc :thumbnail (.getBytes thumbnail-blob 1 (int (.length thumbnail-blob)))))))))

(defn get-image-with-id
  [id]
  ; (timbre/debug "get-image-with-id:" id (type id))
  (let [image (-> (select images
                          (where {:id id})
                          (limit 1))
                (first)
                (underscore-to-dash-in-keys))]
    (cond
      (nil? image)
      nil

      (byte-array? (:content image))
      image

      :else
      (let [blob (:content image)
            thumbnail-blob (:thumbnail image)
            converted-image (-> image
                              (assoc :content   (.getBytes blob           1 (int (.length blob))))
                              (assoc :thumbnail (.getBytes thumbnail-blob 1 (int (.length thumbnail-blob)))))]
        ; (timbre/debug "    converted-image")
        converted-image
        ))))

(defn get-image-with-id-without-content
  [id]
  ; (timbre/debug "get-image-with-id:" id (type id))
  (let [image (-> (select images
                          (fields :id :user_id :thread_url :url :extension :size :width :height :time_retrieved :thumbnail)
                          (where {:id id
                                  :user_id   (:id (session/get :user))})
                          (limit 1))
                (first)
                (underscore-to-dash-in-keys))]
    ; (timbre/debug image)
    (cond
      (nil? image)
      nil

      (byte-array? (:thumbnail image))
      image

      :else
      (let [thumbnail-blob (:thumbnail image)]
        (assoc image :thumbnail (.getBytes thumbnail-blob 1 (int (.length thumbnail-blob))))))))

(defn get-all-image-ids
  []
  (clojure.java.jdbc/query
    merikens-2ch-browser.db.schema/db-spec
    ["SELECT id FROM images ORDER BY id ASC"]
    :row-fn :id))

(defn add-image
  [image]
  (transaction
    (insert images
            (values (dash-to-underscore-in-keys image)))
    (let [id (-> (select images
                         (fields :id)
                         (where {:url (:url image)
                                 :user_id (:user-id image)
                                 :time_retrieved (:time-retrieved image)})
                         (limit 1))
               (first)
               (underscore-to-dash-in-keys)
               (:id))]
      (insert images_extra_info
              (values {:id id
                       :md5_string (create-md5-string (:content image))
                       :saved_as_file false})))))

(defn delete-image-with-url
  [url]
  (transaction
    (let [id (:id (get-image-with-url-without-content-and-thumbnail url))]
    (delete images
            (where {:id id}))
    (delete images_extra_info
            (where {:id id})))))

(defn delete-image-with-id
  [id]
  (transaction
    (delete images
            (where {:id id}))
    (delete images_extra_info
            (where {:id id}))))

(defn update-image
  [id thumbnail width height]
  (update images
          (set-fields {:thumbnail thumbnail
                       :width  width
                       :height height})
          (where {:id id})))

(defn dump-images-table
  []
  (timbre/debug (select images)))

(defn count-images
  []
  (count-keyword (first (clojure.java.jdbc/query
                                 merikens-2ch-browser.db.schema/db-spec
                                 ["SELECT COUNT(*) FROM images WHERE user_id=?" (:id (session/get :user))] ))))

(defn count-images-for-all-users
  []
  (count-keyword (first (clojure.java.jdbc/query
                                 merikens-2ch-browser.db.schema/db-spec
                                 ["SELECT COUNT(*) FROM images"] ))))

(defn count-unsaved-images
  []
  (count-keyword
    (first
      (clojure.java.jdbc/query
        merikens-2ch-browser.db.schema/db-spec
        ["SELECT COUNT(*) FROM images_extra_info WHERE saved_as_file=FALSE"] ))))

(defn count-saved-images
  []
  (count-keyword
    (first
      (clojure.java.jdbc/query
        merikens-2ch-browser.db.schema/db-spec
        ["SELECT COUNT(*) FROM images_extra_info WHERE saved_as_file=TRUE"] ))))

(defn images-extra-info-up-to-date?
  []
  (<= (count-images-for-all-users)
      (count-keyword
        (first
          (clojure.java.jdbc/query
            merikens-2ch-browser.db.schema/db-spec
            ["SELECT COUNT(*) FROM images_extra_info"] )))))

(defn get-image-extra-info
  [id]
  (underscore-to-dash-in-keys (first (select images_extra_info
                                             (where {:id id})))))

(defn add-image-extra-info
  [image-extra-info]
    (insert images_extra_info
            (values (dash-to-underscore-in-keys image-extra-info))))

(defn update-images-extra-info
  []
  ; (timbre/debug "update-images-extra-info")
  (try
    (if (not (images-extra-info-up-to-date?))
      (doall
        (map
          #(let [image (get-image-with-id %1)]
             (when (nil? (get-image-extra-info %1))
               (timbre/info "Adding extra info to image" (str "(ID: " %1 ")"))
               (add-image-extra-info {:id %1 :md5_string (create-md5-string (:content image)) :saved_as_file false})))
          (get-all-image-ids))))

    (catch Throwable t
      (timbre/info "update-images-extra-info: Unexpected exception:" (str t))
      (print-stack-trace t))))

(defn update-md5-string-for-image
  [id]
  (let [image (get-image-with-id id)
        md5-string (create-md5-string (:content image))]
         (timbre/info "Updating MD5 string for image:" (str "(ID: " id ")"))
         (update images_extra_info
                 (set-fields {:md5_string md5-string})
                 (where      {:id id}))
         md5-string))

(defn update-md5-strings-for-all-images
  []
  (doall
    (map
      #(update-md5-string-for-image %1)
      (get-all-image-ids))))

(defn get-next-unsaved-image
  []
  ; (timbre/debug "get-next-unsaved-image")
  (let [image-id (clojure.java.jdbc/query
                   merikens-2ch-browser.db.schema/db-spec
                   ["SELECT id FROM images_extra_info WHERE saved_as_file=FALSE LIMIT 1"]
                   :row-fn :id :result-set-fn first)]
    ; (timbre/debug "    image-id")
    (if image-id (get-image-with-id image-id) nil)))

(defn mark-image-as-saved
  [id]
  (update images_extra_info
          (set-fields {:saved_as_file true})
          (where {:id id})))

(defn mark-all-images-as-unsaved
  []
  (update images_extra_info
          (set-fields {:saved_as_file false})))


;;;;;;;;;;;;;
; DOWNLOADS ;
;;;;;;;;;;;;;

(defn add-download
  [download]
  (insert downloads
          (values (dash-to-underscore-in-keys download))))

(defn get-download
  [url]
  (underscore-to-dash-in-keys (first (select downloads
                                             (where {:url       url
                                                     :user_id   (:id (session/get :user))})))))

(defn get-download-with-user-id
  [user-id url]
  (underscore-to-dash-in-keys (first (select downloads
                                             (where {:url       url
                                                     :user_id   user-id})))))

(defn get-active-download
  [user-id url]
  (underscore-to-dash-in-keys (first (select downloads
                                             (where {:url     url
                                                     :user_id user-id
                                                     :status  "active"})))))

(defn get-pending-download
  [user-id url]
  (underscore-to-dash-in-keys (first (select downloads
                                             (where {:url     url
                                                     :user_id user-id
                                                     :status  "pending"})))))

(defn get-failed-download
  [user-id url]
  (underscore-to-dash-in-keys (first (select downloads
                                             (where {:url     url
                                                     :user_id user-id
                                                     :status  "failed"})))))

(defn delete-active-download
  [user-id url]
  (delete downloads
          (where {:url       url
                  :user_id   user-id
                  :status    "active"})
          (limit 1)))

(defn delete-pending-download
  [user-id url]
  (delete downloads
          (where {:url       url
                  :user_id   user-id
                  :status    "pending"})
          (limit 1)))

(defn delete-all-active-and-pending-downloads
  []
  (delete downloads
          (where {:user_id   (:id (session/get :user))
                  :status    "active"}))
  (delete downloads
          (where {:user_id   (:id (session/get :user))
                  :status    "pending"})))

(defn restart-all-active-downloads
  []
  (update downloads
          (set-fields {:status "pending"})
          (where      {:status "active"})))

(defn count-downloads
  [status]
  (count-keyword
    (first
      (clojure.java.jdbc/query
        merikens-2ch-browser.db.schema/db-spec
        ["SELECT COUNT(*) FROM downloads WHERE user_id = ? AND status = ?" (:id (session/get :user)) status] ))))

(defn count-downloads-all-users
  [status]
  ; (timbre/debug "count-downloads-all-users:" status)
  (count-keyword
    (first
      (clojure.java.jdbc/query
        merikens-2ch-browser.db.schema/db-spec
        ["SELECT COUNT(*) FROM downloads WHERE status = ?" status] ))))

(defn pop-next-pending-download
  []
  ; (timbre/debug "pop-next-pending-download")
  (let [download (underscore-to-dash-in-keys
                   (first
                     (select downloads
                             (where {:status "pending"})
                             (order (raw (if (= (:subprotocol schema/db-spec) "postgresql") "RANDOM()" "RAND()"))))))]
    (if (nil? download)
      nil
      (do
        (delete downloads
                (where {:url       (:url     download)
                        :user_id   (:user-id download)
                        :status    "pending"}))
        download))))

(defn dump-downloads-table
  []
  (timbre/debug (select downloads)))

(defn dump-pending-downloads
  []
  (timbre/debug (select downloads
                        (where {:status "pending"}))))

(defn get-all-downloads
  []
  (map underscore-to-dash-in-keys (select downloads)))



;;;;;;;;;;;;;;;;;
; USER SETTINGS ;
;;;;;;;;;;;;;;;;;

(defn add-user-setting
  [user-setting]
  (insert user_settings
          (values (dash-to-underscore-in-keys user-setting))))

(defn get-user-setting
  [setting-name]
  (:value (first (select user_settings
                         (where {:setting_name setting-name
                                 :user_id      (:id (session/get :user))})))))

(defn get-user-setting-with-user-id
  [user-id setting-name]
  (:value (first (select user_settings
                         (where {:setting_name setting-name
                                 :user_id      user-id})))))

(defn delete-user-setting
  [setting-name]
  (delete user_settings
          (where {:setting_name setting-name
                  :user_id      (:id (session/get :user))})))

(defn update-user-setting
  [setting-name value]
  (transaction
    (if (< 0 (count (select user_settings
                            (where {:setting_name setting-name
                                    :user_id      (:id (session/get :user))}))))
      (update user_settings
              (set-fields {:value value})
              (where {:setting_name setting-name
                      :user_id      (:id (session/get :user))}))
      (insert user_settings
              (values {:user_id      (:id (session/get :user))
                       :setting_name setting-name
                       :value value})))))

(defn dump-user-settings-table
  []
  (timbre/debug (select user_settings)))

(defn get-all-user-settings
  []
  (map underscore-to-dash-in-keys (select user_settings)))



;;;;;;;;;;;;;;;;;;;
; SYSYEM SETTINGS ;
;;;;;;;;;;;;;;;;;;;

(defn add-system-setting
  [system-setting]
  (insert system_settings
          (values (dash-to-underscore-in-keys system-setting))))

(defn get-system-setting
  [setting-name]
  (:value (first (select system_settings
                         (where {:setting_name setting-name})))))

(defn delete-system-setting
  [setting-name]
  (delete system_settings
          (where {:setting_name setting-name})))

(defn update-system-setting
  [setting-name value]
  (transaction
    (if (< 0 (count (select system_settings
                            (where {:setting_name setting-name}))))
      (update system_settings
              (set-fields {:value value})
              (where {:setting_name setting-name}))
      (insert system_settings
              (values {:setting_name setting-name
                       :value value})))))

(defn dump-system-settings-table
  []
  (timbre/debug (select system_settings)))

(defn get-all-system-settings
  []
  (map underscore-to-dash-in-keys (select system_settings)))



;;;;;;;;;;;;;;;;
; POST FILTERS ;
;;;;;;;;;;;;;;;;

(defn get-all-post-filters
  []
  (map underscore-to-dash-in-keys (select post_filters
                                          (where {:user_id (:id (session/get :user))}))))

(defn get-aborn-posts
  []
  (map underscore-to-dash-in-keys (select post_filters
                                          (where {:user_id (:id (session/get :user))
                                                  :filter_type "post"
                                                  :invisible   true}))))

(defn get-aborn-ids
  []
  (map underscore-to-dash-in-keys (select post_filters
                                          (where {:user_id (:id (session/get :user))
                                                  :filter_type "id"
                                                  :invisible   true}))))

(defn get-aborn-filters-for-message
  []
  (map underscore-to-dash-in-keys (select post_filters
                                          (where {:user_id     (:id (session/get :user))
                                                  :filter_type "message"
                                                  :invisible   true}))))

(defn add-post-filter
  [post-filter]
  (transaction
    (if true
      (insert post_filters
              (values (dash-to-underscore-in-keys post-filter))))))

(defn delete-post-filter
  [id]
  (delete post_filters
          (where {:id id})))

(defn delete-all-post-filters
  []
  (delete post_filters
          (where {:user_id (:id (session/get :user))})))

(defn get-post-filter-with-image-url
  [user-id url]
  (first (map underscore-to-dash-in-keys (select post_filters
                                                 (where {:pattern     url
                                                         :user_id     user-id
                                                         :filter_type "image-url"})))))

(defn get-post-filter-with-image-md5-string
  [user-id md5-string]
  (first (map underscore-to-dash-in-keys (select post_filters
                                                 (where {:pattern     md5-string
                                                         :user_id     user-id
                                                         :filter_type "image-md5-string"})))))

(defn delete-all-aborn-posts
  []
  (delete post_filters
          (where {:user_id     (:id (session/get :user))
                  :filter_type "post"
                  :invisible   true})))

(defn delete-all-aborn-ids
  []
  (delete post_filters
          (where {:user_id     (:id (session/get :user))
                  :filter_type "id"
                  :invisible    true})))

(defn delete-all-aborn-filters-for-message
  []
  (delete post_filters
          (where {:user_id     (:id (session/get :user))
                  :filter_type "message"
                  :invisible    true})))

(defn dump-post-filters
  []
  (timbre/debug (select post_filters)))

(defn dump-aborn-ids
  []
  (timbre/debug (select post_filters
                        (where {:filter_type "id"}))))

(defn display-row-counts
  []
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM USERS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM DAT_FILES"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM THREADS_IN_HTML"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM THREADS_IN_JSON"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM BOARD_INFO"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM FAVORITE_BOARDS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM THREAD_INFO"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM THREAD_INFO WHERE title IS NULL"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM BOOKMARKS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM FAVORITE_THREADS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM IMAGES"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM IMAGES_EXTRA_INFO"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM DOWNLOADS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM USER_SETTINGS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM SYSTEM_SETTINGS"]))))
  (timbre/info (count-keyword (first (clojure.java.jdbc/query merikens-2ch-browser.db.schema/db-spec ["SELECT COUNT(*) FROM POST_FILTERS"])))))

(defn upgrade-tables
  [db-spec]
  (let [{:keys [id blob varchar varchar-ignorecase varchar-ignorecase-unique]} (schema/db-types db-spec)]
    ; prior to 0.1.25
    (schema/create-post-filters-table db-spec)
    (schema/create-images-extra-info-table db-spec)
    (schema/create-threads-in-json-table db-spec)
    (schema/create-indexes db-spec)
    (update-images-extra-info)

    ; 0.1.25
    (doseq [table-name (list "board_info"
                             "favorite_boards"
                             "bookmarks"
                             "favorite_threads"
                             "downloads"
                             "user_settings"
                             "system_settings"
                             "thread_info"
                             "dat_files"
                             "threads_in_html"
                             "threads_in_json")]
      (try
        (clojure.java.jdbc/execute! schema/db-spec [(str "ALTER TABLE " table-name " ADD COLUMN id " (clojure.string/replace id #"PRIMARY KEY" ""))])
        (catch Throwable t)))))
