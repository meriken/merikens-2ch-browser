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



(ns merikens-2ch-browser.db.backup
  (:require [clojure.java.jdbc :as sql]
            [jdbc.pool.c3p0    :refer [make-datasource-spec]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [noir.session :as session]
            [taoensso.timbre :as timbre]
            [taoensso.nippy :as nippy]
            [clj-time.core]
            [clj-time.coerce]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.db.schema :as schema]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource))



;;;;;;;;;;;;;
; DATABASES ;
;;;;;;;;;;;;;

; without image tables
(def table-list-without-images
  '("users"
     "board_info"
     "favorite_boards"
     "bookmarks"
     "favorite_threads"
     "downloads"
     "user_settings"
     "system_settings"
     "post_filters"
     "thread_info"
     "dat_files"
     "threads_in_html"
     "threads_in_json"))

(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(defn copy-table
  [src dest table-name]
  (timbre/info "Loading" table-name "table...")
  (let [table-keyword (keyword table-name)
        rows (sql/query src [(str "SELECT * FROM " table-name)])
        row-count (count rows)
        partition-size (case table-name
                         "images"          1000
                         "dat_files"       1000
                         "threads_in_html" 1000
                         "therads_in_json" 1000
                         "board_info"      1000
                                           10000)
        partitioned-rows (partition-all partition-size rows)
        partitioned-row-count (count partitioned-rows)
        start-time (java.lang.System/currentTimeMillis)]
    (timbre/info "Saving" table-name "table...")
    (doall (map #(do
                   ; (timbre/debug (concat (list dest table-keyword) %1))
                   (apply sql/insert! (concat (list dest table-keyword) %1))
                   (if true ; (some #{table-keyword} '(:thread_info :dat_files :threads_in_html :threads_in_json))
                     (timbre/info "Saving" table-name "table..." (get-progress start-time (min (* %2 partition-size) %3) %3))))
                partitioned-rows
                (range 1 (inc partitioned-row-count))
                (repeat row-count)))))

(defn copy-images-table
  [src dest]
  (timbre/info "Copying images table...")
  (let [ids (sql/query src ["SELECT id FROM images"] :row-fn :id)
        id-count (count ids)
        start-time (java.lang.System/currentTimeMillis)]
    (doall
      (map
        #(let [image                 (first (sql/query src ["SELECT * FROM images            WHERE id = ?" %1]))
               image-extra-info (try (first (sql/query src ["SELECT * FROM images_extra_info WHERE id = ?" %1])) (catch Throwable t nil))]
           (sql/insert! dest :images image)
           (if image-extra-info
             (sql/insert! dest :images_extra_info image-extra-info))
           (if (= (mod %2 100) 0)
             (timbre/info "Saved images" (get-progress start-time %2 %3))))
        ids
        (range 1 (inc id-count))
        (repeat id-count)))))



;;;;;;;;;;;;;;
; CONVERSION ;
;;;;;;;;;;;;;;

(defn copy-database
  [src dest & rest]
  (try
    (let [pooled-src  (make-datasource-spec src)
          pooled-dest (make-datasource-spec dest)]

      (schema/drop-tables    pooled-dest)
      (schema/create-tables  pooled-dest)
      (schema/create-indexes pooled-dest)

      (db/upgrade-tables pooled-src)

      (doall (map #(copy-table pooled-src pooled-dest %1) table-list-without-images))
      (if (not (some #{:without-images} rest))
        (doall (map #(copy-table pooled-src pooled-dest %1) (list "images_extra_info" "images"))))

      (try (sql/db-do-commands pooled-dest "SHUTDOWN") (catch Throwable t))
      (try (sql/db-do-commands pooled-src  "SHUTDOWN") (catch Throwable t))

      true)

    (catch Throwable t
      (timbre/debug "Failed to convert database:" (str t))
      (.printStackTrace t)
      false)))

(defn convert-h2-database-to-hypersql-database
  []
  (timbre/info "Converting H2 database to HyperSQL database...")
  (if (copy-database schema/h2-db-spec schema/hsqldb-db-spec)
    (timbre/info "Converted H2 database to HyperSQL database.")))

(defn convert-hypersql-database-to-h2-database
  []
  (timbre/info "Converting HyperSQL database to H2 database...")
  (if (copy-database schema/hsqldb-db-spec schema/h2-db-spec)
    (timbre/info "Converted HyperSQL database to H2 database.")))

(defn convert-mysql-database-to-hypersql-database
  []
  (timbre/info "Converting MySQL database to HyperSQL database...")
  (if (copy-database schema/mysql-db-spec schema/hsqldb-db-spec)
    (timbre/info "Converted MySQL database to HyperSQL database.")))

(defn convert-hypersql-database-to-mysql-database
  []
  (timbre/info "Converting HyperSQL database to MySQL database...")
  (if (copy-database schema/hsqldb-db-spec schema/mysql-db-spec)
    (timbre/info "Converted HyperSQL database to MySQL database.")))



;;;;;;;;;;;
; BACKUPS ;
;;;;;;;;;;;

(defn create-backup
  [& args]
  (timbre/info "Creating backup database...")
  (when (if (some #{:without-images} args)
          (copy-database schema/db-spec schema/backup-db-spec :without-images)
          (copy-database schema/db-spec schema/backup-db-spec))
    (timbre/info "Created backup database.")))
