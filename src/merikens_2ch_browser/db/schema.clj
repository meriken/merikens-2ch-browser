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



(ns merikens-2ch-browser.db.schema
  (:require [clojure.java.jdbc :as sql]
            [clojure.stacktrace :refer [print-stack-trace]]
            [taoensso.timbre :refer [log]]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.cursive :refer :all]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;
; DATABASE SPECIFICATIONS ;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def db-name "merikens-2ch-browser")
(def backup-db-name "merikens-2ch-browser-backup")

; H2 embedded mode
(def h2-db-spec {:classname   "org.h2.Driver"
                 :subprotocol "h2"
                 :subname     (str "./" db-name
                                   ";MV_STORE=FALSE"
                                   ";MVCC=TRUE"
                                   ";MULTI_THREADED=FALSE"
                                   ";DEFRAG_ALWAYS=FALSE"
                                   ";RECOVER=TRUE"
                                   ";CACHE_SIZE=262144"
                                   ";LOCK_TIMEOUT=10000"
                                   ";TRACE_LEVEL_FILE=0"
                                   ";TRACE_LEVEL_SYSTEM_OUT=0")
                 :user        "sa"
                 :password    ""
                 :make-pool?  true
                 :naming      {:keys   clojure.string/lower-case
                               :fields clojure.string/upper-case}})

(def h2-backup-db-spec {:classname   "org.h2.Driver"
                        :subprotocol "h2"
                        :subname     (str "./" backup-db-name
                                          ";MV_STORE=FALSE"
                                          ";MVCC=TRUE"
                                          ";MULTI_THREADED=FALSE"
                                          ";DEFRAG_ALWAYS=FALSE"
                                          ";RECOVER=TRUE"
                                          ";CACHE_SIZE=262144"
                                          ";LOCK_TIMEOUT=10000"
                                          ";TRACE_LEVEL_FILE=0"
                                          ";TRACE_LEVEL_SYSTEM_OUT=0")
                        :user        "sa"
                        :password    ""
                        :make-pool?  true
                        :naming      {:keys   clojure.string/lower-case
                                      :fields clojure.string/upper-case}})

; H2 server mode
(comment def h2-server-db-spec {:classname   "org.h2.Driver"
                        :subprotocol "h2"
                        :subname     (str "tcp://localhost/" db-name)
                        :user        "sa"
                        :password    ""
                        :make-pool?  true
                        :naming      {:keys   clojure.string/lower-case
                                      :fields clojure.string/upper-case}})

; HyperSQL embedded mode
(def hsqldb-db-spec {:classname   "org.hsqldb.jdbc.JDBCDriver"
                     :subprotocol "hsqldb"
                     :subname     (str "file:" db-name ".hsqldb"
                                       ";hsqldb.tx=mvcc")
                     :user        "sa"
                     :password    ""
                     :make-pool?  true
                     :naming      {:keys   clojure.string/lower-case
                                   :fields clojure.string/upper-case}})

(def hsqldb-backup-db-spec {:classname   "org.hsqldb.jdbc.JDBCDriver"
                            :subprotocol "hsqldb"
                            :subname     (str "file:" backup-db-name ".hsqldb"
                                              ";hsqldb.tx=mvcc")
                            :user        "sa"
                            :password    ""
                            :make-pool?  true
                            :naming      {:keys   clojure.string/lower-case
                                          :fields clojure.string/upper-case}})

; MySQL
(def mysql-db-spec {:classname   "com.mysql.jdbc.Driver"
                    :subprotocol "mysql"
                    :subname     "//127.0.0.1:3306/merikens_2ch_browser" ; ?zeroDateTimeBehavior=convertToNull"
                    :delimiters  "`"
                    :user        "merikens_2ch_browser"
                    :password    ""
                    :make-pool?  true
                    :naming      {:keys   clojure.string/lower-case
                                  :fields clojure.string/lower-case}})

; PostgreSQL
(def postgresql-db-spec {:classname   "org.postgresql.Driver"
                         :subprotocol "postgresql"
                         :subname     "//127.0.0.1:5432/merikens_2ch_browser" ; ?zeroDateTimeBehavior=convertToNull"
                         :delimiters  ""
                         :user        "merikens_2ch_browser"
                         :password    ""
                         :make-pool?  true
                         :naming      {:keys   clojure.string/lower-case
                                       :fields clojure.string/lower-case}})

; default
(def db-spec        h2-db-spec)
(def backup-db-spec h2-backup-db-spec)



;;;;;;;;;;;;;;;;;;;;;
; UTILITY FUNCTIONS ;
;;;;;;;;;;;;;;;;;;;;;

(defn db-types
  [db-spec]
  (cond
    (or (= (:subprotocol db-spec) "h2")
        (and (:datasource db-spec)
             (re-find #"^jdbc:h2:" (java-get-jdbc-url (:datasource db-spec)))))
    {:id                                 "INTEGER PRIMARY KEY AUTO_INCREMENT"
     :varchar                            "VARCHAR"
     :varchar-ignorecase                 "VARCHAR_IGNORECASE"
     :varchar-ignorecase-unique          "VARCHAR_IGNORECASE UNIQUE"
     :blob                               "BLOB"}

    (or (= (:subprotocol db-spec) "hsqldb")
        (and (:datasource db-spec)
             (re-find #"^jdbc:hsqldb:" (java-get-jdbc-url (:datasource db-spec)))))
    {:id                                 "INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1)"
     :varchar                            "VARCHAR(16777216)"
     :varchar-ignorecase                 "VARCHAR_IGNORECASE(16777216)"
     :varchar-ignorecase-unique          "VARCHAR_IGNORECASE(16777216) UNIQUE"
     :blob                               "BLOB"}

    (or (= (:subprotocol db-spec) "mysql")
        (and (:datasource db-spec)
             (re-find #"^jdbc:mysql:" (java-get-jdbc-url (:datasource db-spec)))))
    {:id                                 "SERIAL PRIMARY KEY"
     :varchar                            "LONGTEXT CHARACTER SET UTF8 COLLATE 'utf8_bin'"
     :varchar-ignorecase                 "LONGTEXT CHARACTER SET UTF8 COLLATE 'utf8_general_ci'"
     :varchar-ignorecase-unique          "LONGTEXT CHARACTER SET UTF8 COLLATE 'utf8_general_ci'" ; UNIQUE is not supported.
     :blob                               "LONGBLOB"}

    (or (= (:subprotocol db-spec) "postgresql")
        (and (:datasource db-spec)
             (re-find #"^jdbc:postgresql:" (java-get-jdbc-url (:datasource db-spec)))))
    {:id                                 "SERIAL PRIMARY KEY"
     :varchar                            "CITEXT"
     :varchar-ignorecase                 "CITEXT"
     :varchar-ignorecase-unique          "CITEXT UNIQUE"
     :blob                               "BYTEA"}

    :else
    nil))

(defn initialized?
  "Checks to see if the database schema is present."
  []
  (try
    (sql/query db-spec "SELECT * FROM users")
    true

    (catch Throwable t
      (log :info "Database is not initialized:" (str t))
      ; (print-stack-trace t)
      false)))

(defn h2-database-initialized?
  []
  (java-file-exists (new java.io.File (str "./" db-name ".h2.db"))))

(defn hypersql-database-initialized?
  []
  (java-file-exists (new java.io.File (str "./" db-name ".hsqldb.properties"))))



(defn create-users-table
   [db-spec]
   (let [{:keys [id blob varchar varchar-ignorecase-unique]} (db-types db-spec)]
     (sql/db-do-commands
       db-spec
       (sql/create-table-ddl
         :users
         [:id           id]
         [:username     varchar-ignorecase-unique "NOT NULL"]
         [:email        varchar-ignorecase-unique "NOT NULL"]
         [:display_name varchar "NOT NULL"]
         [:pass         varchar "NOT NULL"]
         [:salt         varchar "NOT NULL"]
         [:cookie_store blob "DEFAULT NULL"]))))

(defn create-dat-files-table
  [db-spec]
  (let [{:keys [id blob varchar varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :dat_files
        [:id            id]
        [:user_id       "INTEGER NOT NULL"]
        [:service       varchar-ignorecase "NOT NULL"]
        [:server        varchar-ignorecase "NOT NULL"]
        [:board         varchar-ignorecase "NOT NULL"]
        [:thread_no     "BIGINT NOT NULL"]
        [:etag          varchar]
        [:last_modified varchar]
        [:content       blob "NOT NULL"]
        [:size          "INTEGER NOT NULL"]
        [:res_count     "INTEGER NOT NULL"]
        [:source_url    varchar]))))

(defn create-threads-in-html-table
  [db-spec]
  (let [{:keys [id varchar varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :threads_in_html
        [:id            id]
        [:user_id       "INTEGER NOT NULL"]
        [:service       varchar-ignorecase "NOT NULL"]
        [:server        varchar-ignorecase "NOT NULL"]
        [:board         varchar-ignorecase "NOT NULL"]
        [:thread_no     "BIGINT NOT NULL"]
        [:content       varchar "NOT NULL"]
        [:res_count     "INTEGER NOT NULL"]
        [:source_url    varchar]))))

(defn create-board-info-table
  [db-spec]
  (let [{:keys [id varchar varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :board_info
        [:id            id]
        [:service       varchar-ignorecase "NOT NULL"]
        [:server        varchar-ignorecase "NOT NULL"]
        [:board         varchar-ignorecase "NOT NULL"]
        [:board_name    varchar]
        [:subject_txt   varchar]
        [:time_subject_txt_retrieved "TIMESTAMP NULL"]
        [:setting_txt   varchar]))))

(defn create-thread-info-table
   [db-spec]
   (let [{:keys [id varchar varchar-ignorecase]} (db-types db-spec)]
     (sql/db-do-commands
       db-spec
       (sql/create-table-ddl
         :thread_info
         [:id            id]
         [:service       varchar-ignorecase "NOT NULL"]
         [:server        varchar-ignorecase "NOT NULL"]
         [:board         varchar-ignorecase "NOT NULL"]
         [:thread_no     "BIGINT NOT NULL"]
         [:title         varchar]
         [:res_count     "INTEGER"]
         [:archived      "BOOLEAN DEFAULT FALSE NOT NULL"]))))

(defn create-favorite-boards-table
  [db-spec]
  (let [{:keys [id varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :favorite_boards
        [:id            id]
        [:user_id       "INTEGER NOT NULL"]
        [:service       varchar-ignorecase "NOT NULL"]
        [:server        varchar-ignorecase "NOT NULL"]
        [:board         varchar-ignorecase "NOT NULL"]))))

(defn create-bookmarks-table
  [db-spec]
  (let [{:keys [id varchar varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :bookmarks
        [:id               id]
        [:user_id          "INTEGER NOT NULL"]
        [:service          varchar-ignorecase "NOT NULL"]
        [:board            varchar-ignorecase "NOT NULL"]
        [:thread_no        "BIGINT NOT NULL"]
        [:bookmark         "INTEGER"]
        [:time_last_viewed "TIMESTAMP NULL"]
        [:time_last_posted "TIMESTAMP NULL"]
        [:last_handle      varchar]
        [:last_email       varchar]
        [:draft            varchar]
        [:autosaved_draft  varchar]))))

(defn create-favorite-threads-table
  [db-spec]
  (let [{:keys [id varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :favorite_threads
        [:id            id]
        [:user_id       "INTEGER NOT NULL"]
        [:service       varchar-ignorecase "NOT NULL"]
        [:server        varchar-ignorecase "NOT NULL"]
        [:board         varchar-ignorecase "NOT NULL"]
        [:thread_no     "BIGINT NOT NULL"]))))

(defn create-images-table
  [db-spec]
  (let [{:keys [id blob varchar]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :images
        [:id             id]
        [:user_id        "INTEGER NOT NULL"]
        [:thread_url     varchar "NOT NULL"]
        [:url            varchar "NOT NULL"]
        [:content        blob "NOT NULL"]
        [:thumbnail      blob "NOT NULL"]
        [:extension      varchar "NOT NULL"]
        [:size           "INT NOT NULL"]
        [:width          "INT NOT NULL"]
        [:height         "INT NOT NULL"]
        [:time_retrieved "TIMESTAMP NOT NULL"]))))

(defn create-downloads-table
  [db-spec]
  (let [{:keys [id varchar]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :downloads
        [:id             id]
        [:user_id        "INTEGER NOT NULL"]
        [:url            varchar "NOT NULL"]
        [:thread_url     varchar "NOT NULL"]
        [:status         varchar "NOT NULL"]
        [:time_updated   "TIMESTAMP NOT NULL"]
        [:retry_count    "INT DEFAULT 0 NOT NULL"]))))

(defn create-user-settings-table
  [db-spec]
  (let [{:keys [id varchar]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :user_settings
        [:id             id]
        [:user_id        "INTEGER NOT NULL"]
        [:setting_name   varchar "NOT NULL"]
        [:value          varchar "NOT NULL"]))))

(defn create-system-settings-table
  [db-spec]
  (let [{:keys [id varchar]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (sql/create-table-ddl
        :system_settings
        [:id             id]
        [:setting_name   varchar "NOT NULL"]
        [:value          varchar "NOT NULL"]))))

(defn create-post-filters-table
  [db-spec]
  (let [{:keys [id varchar varchar-ignorecase ]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (str "CREATE TABLE IF NOT EXISTS "
           "post_filters("
           "id              " id ","
           "user_id           INTEGER NOT NULL,"
           "invisible         BOOLEAN DEFAULT TRUE NOT NULL,"
           "filter_type     " varchar " NOT NULL," ; "post" "id" "name" "mail" "message" "id" "be" "thread" "image-url" "image-md5-string"
           "pattern         " varchar " NOT NULL," ; regex pattern, image url, or image MD5 string
           "ignore_case       BOOLEAN DEFAULT FALSE NOT NULL,"
           "regex             BOOLEAN DEFAULT FALSE NOT NULL,"
           "board           " varchar-ignorecase ","
           "thread_title    " varchar "," ; regex
           "time_last_matched TIMESTAMP NULL,"
           "match_count       INTEGER DEFAULT 0)"))))

(defn create-images-extra-info-table
  [db-spec]
  (let [{:keys [varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (str "CREATE TABLE IF NOT EXISTS "
           "images_extra_info("
           "id                INTEGER PRIMARY KEY,"
           "saved_as_file     BOOLEAN DEFAULT TRUE NOT NULL,"
           "md5_string      " varchar-ignorecase " DEFAULT NULL)"))))

(defn create-threads-in-json-table
  [db-spec]
  (let [{:keys [id varchar varchar-ignorecase]} (db-types db-spec)]
    (sql/db-do-commands
      db-spec
      (str "CREATE TABLE IF NOT EXISTS "
           "threads_in_json("
           "id            " id ","
           "user_id         INTEGER NOT NULL,"
           "service       " varchar-ignorecase " NOT NULL,"
           "server        " varchar-ignorecase " NOT NULL,"
           "board         " varchar-ignorecase " NOT NULL,"
           "thread_no       BIGINT NOT NULL,"
           "content       " varchar " NOT NULL,"
           "res_count       INTEGER NOT NULL,"
           "source_url    " varchar ")"))))

(defn create-indexes
  [db-spec]
  (try (sql/db-do-commands db-spec "CREATE INDEX board_info_index                   ON board_info        ( board(128)        );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX thread_info_index                  ON thread_info       ( thread_no         );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX bookmarks_index                    ON bookmarks         ( thread_no         );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX bookmarks_board_index              ON bookmarks         ( board(128)        );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX dat_files_index                    ON dat_files         ( thread_no         );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX threads_in_html_index              ON threads_in_html   ( thread_no         );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX threads_in_json_index              ON threads_in_json   ( thread_no         );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX favorite_threads_index             ON favorite_threads  ( thread_no         );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX images_index                       ON images            ( url(128)          );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX downloads_index                    ON downloads         ( url(128)          );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX user_settings_index                ON user_settings     ( setting_name(128) );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX system_settings_index              ON system_settings   ( setting_name(128) );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX post_filters_patern_index          ON post_filters      ( pattern(128)      );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX post_filters_board_index           ON post_filters      ( board(128)        );") (catch Throwable _))
  (try (sql/db-do-commands db-spec "CREATE INDEX images_extra_info_md5_string_index ON images_extra_info ( md5_string(128)   );") (catch Throwable _)))

; This function needs to be updated.
(comment defn drop-indexes
  [db-spec]
  (sql/db-do-commands
    db-spec
    "DROP INDEX IF EXISTS bookmarks_index;"
    "DROP INDEX IF EXISTS dat_files_index;"
    "DROP INDEX IF EXISTS threads_in_html_index;"
    "DROP INDEX IF EXISTS threads_in_json_index;"
    "DROP INDEX IF EXISTS thread_info_index;"
    "DROP INDEX IF EXISTS favorite_threads_index;"
    "DROP INDEX IF EXISTS images_index;"
    "DROP INDEX IF EXISTS downloads_index;"
    "DROP INDEX IF EXISTS user_settings_index;"
    "DROP INDEX IF EXISTS system_settings_index;"
    "DROP INDEX IF EXISTS post_filters_board_index;"
    "DROP INDEX IF EXISTS post_filters_pattern_index;"
    "DROP INDEX IF EXISTS images_extra_info_md5_string_index;"))

(defn create-tables
  "creates the database tables used by the application"
  [db-spec]
  (create-users-table db-spec)
  (create-dat-files-table db-spec)
  (create-threads-in-html-table db-spec)
  (create-threads-in-json-table db-spec)
  (create-board-info-table db-spec)
  (create-favorite-boards-table db-spec)
  (create-thread-info-table db-spec)
  (create-bookmarks-table db-spec)
  (create-favorite-threads-table db-spec)
  (create-images-table db-spec)
  (create-images-extra-info-table db-spec)
  (create-downloads-table db-spec)
  (create-user-settings-table db-spec)
  (create-system-settings-table db-spec)
  (create-post-filters-table db-spec))

(defn drop-tables
  [db-spec]
  (sql/db-do-commands
    db-spec
    "DROP TABLE IF EXISTS users;"
    "DROP TABLE IF EXISTS dat_files;"
    "DROP TABLE IF EXISTS threads_in_html;"
    "DROP TABLE IF EXISTS threads_in_json;"
    "DROP TABLE IF EXISTS board_info;"
    "DROP TABLE IF EXISTS favorite_boards;"
    "DROP TABLE IF EXISTS thread_info;"
    "DROP TABLE IF EXISTS bookmarks;"
    "DROP TABLE IF EXISTS favorite_threads;"
    "DROP TABLE IF EXISTS images;"
    "DROP TABLE IF EXISTS images_extra_info;"
    "DROP TABLE IF EXISTS downloads;"
    "DROP TABLE IF EXISTS user_settings;"
    "DROP TABLE IF EXISTS system_settings;"
    "DROP TABLE IF EXISTS post_filters;"))
