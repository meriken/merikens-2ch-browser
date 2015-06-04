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



(ns merikens-2ch-browser.db.backup
  (:require [clojure.java.jdbc :as sql]
            [jdbc.pool.c3p0 :refer [make-datasource-spec]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [taoensso.timbre :as timbre :refer [log]]
            [clj-time.core]
            [clj-time.coerce]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.db.schema :as schema]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]))



;;;;;;;;;;;;;
; DATABASES ;
;;;;;;;;;;;;;

; without image tables
(def table-list-without-images
  (list "users"
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

(defn get-partition-size
  [table-name]
  (case table-name
    "images" 100
    "dat_files" 1000
    "threads_in_html" 1000
    "therads_in_json" 1000
    "board_info" 1000
    5000))

(defn copy-table
  [src dest table-name]
  (timbre/info "Copying" table-name "table...")
  (let [table-keyword (keyword table-name)
        ids (sql/query src [(str "SELECT id FROM " table-name)] :row-fn :id)
        id-count (count ids)
        partition-size (get-partition-size table-name)
        partitioned-ids (partition-all partition-size ids)
        partitioned-id-count (count partitioned-ids)
        start-time (java.lang.System/currentTimeMillis)
        process-partition (fn [ids]
                            (let [where (str "WHERE id IN ("
                                             (apply str (interleave ids (repeat (dec (count ids)) ",")))
                                             (last ids)
                                             ")")]
                              (sql/execute! dest [(str "DELETE FROM " table-name " " where)])
                              (apply sql/insert! (concat (list dest table-keyword)
                                                         (sql/query src [(str "SELECT * FROM " table-name " " where)])))))]
    (doall
      (map
        #(loop []
          (if-not
            (try
              (process-partition %1)
              (timbre/info (str "Copying " table-name " table...") (get-progress start-time (min (* %2 partition-size) %3) %3))
              true
              (catch java.sql.SQLNonTransientConnectionException _
                (timbre/info "Recovering from connection error...")
                (.softResetAllUsers (:datasource src))
                (.softResetAllUsers (:datasource dest))
                false))
            (recur)))
        partitioned-ids
        (range 1 (inc partitioned-id-count))
        (repeat id-count)))))



;;;;;;;;;;;;;;
; CONVERSION ;
;;;;;;;;;;;;;;

(defn copy-database
  [src dest & rest]
  (try
    (let [pooled-src (make-datasource-spec src)
          pooled-dest (make-datasource-spec dest)]

      (schema/drop-tables pooled-dest)
      (schema/create-tables pooled-dest)
      (schema/create-indexes pooled-dest)

      (timbre/info "Upgrading database...")
      (db/upgrade-tables pooled-src)

      (doall (map #(copy-table pooled-src pooled-dest %1) table-list-without-images))
      (if (not (some #{:without-images} rest))
        (doall (map #(copy-table pooled-src pooled-dest %1) (list "images" "images_extra_info"))))

      (try (sql/db-do-commands pooled-dest "SHUTDOWN") (catch Throwable _))
      (try (sql/db-do-commands pooled-src "SHUTDOWN") (catch Throwable _))

      true)

    (catch Throwable t
      (timbre/debug "Failed to copy database:" (str t))
      ; (.printStackTrace t)
      false)))

(defn convert-database
  [src dest & rest]
  (if (= src dest)
    (throw (IllegalArgumentException. "Source and destination are the same.")))
  (let [database-info [{:commandline-name "h2" :display-name "H2" :db-spec schema/h2-db-spec}
                       {:commandline-name "hypersql" :display-name "HyperSQL" :db-spec schema/hsqldb-db-spec}
                       {:commandline-name "mysql" :display-name "MySQL" :db-spec schema/mysql-db-spec}
                       {:commandline-name "postgresql" :display-name "PostgreSQL" :db-spec schema/postgresql-db-spec}]
        src-info (nth (filter #(= src (:commandline-name %1)) database-info) 0 nil)
        dest-info (nth (filter #(= dest (:commandline-name %1)) database-info) 0 nil)]
    (if (or (nil? src-info) (nil? dest-info))
      (throw (IllegalArgumentException. "Database not found.")))
    (timbre/info "Converting" (:display-name src-info) "database to" (:display-name dest-info) "database...")
    (if (if (some #{:without-images} rest)
          (copy-database (:db-spec src-info) (:db-spec dest-info) :without-images)
          (copy-database (:db-spec src-info) (:db-spec dest-info)))
      (timbre/info "Converted" (:display-name src-info) "database to" (:display-name dest-info) "database..."))))



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
