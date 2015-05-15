(ns merikens-2ch-browser.import
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
            [taoensso.timbre :as timbre]
            [clj-http.client :as client]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.routes.image :refer [set-up-download ng-image? ng-image-url?]]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.routes.thread-content]
            [merikens-2ch-browser.routes.pc.thread-content]
            [com.climate.claypoole :as cp]
            [clojure.data.codec.base64 :as base64])
  (:import org.h2.util.Profiler
           org.jsoup.Jsoup))

(defn
  search-for-dat-files
  [file-list]
  (map
    #(try
       (let [dat-file %1
             thread-no (java.lang.Long/parseLong (second (re-find #"^([0-9]+)\.dat$" (.getName dat-file))))
             parent (.getParentFile dat-file)
             parent-name (.getName parent)
             grandparent (.getParentFile parent)
             grandparent-name (.getName grandparent)
             greatgrandparent (try (.getParentFile grandparent) (catch Throwable t nil))
             greatgrandparent-name (and greatgrandparent (.getName greatgrandparent))
             shitaraba? (shitaraba-server? greatgrandparent-name)
             board (if shitaraba? (str grandparent-name "/" parent-name) parent-name)
             server (cond
                      shitaraba? greatgrandparent-name
                      (= grandparent-name "2channel") (or (db/get-server "2ch.net" board) (db/get-server "bbspink.com" board))
                      :else grandparent-name)
             server (if (= server "jbbs.livedoor.jp") "jbbs.shitaraba.net" server)]
         (if (or (.isDirectory dat-file)
                 ; (nil? server)
                 (and server (nil? (re-find #"^[a-z0-9.-]+$" server)))
                 (nil? board)
                 (nil? (re-find #"^[a-z0-9A-Z/]+$" board)))
           (do
             (timbre/error "Error: Invalid directory structure:" (.getPath dat-file))
             nil)
           (do
             ; (println server board thread-no)
             {:file dat-file
              :server server
              :service (server-to-service server)
              :board board
              :thread-no thread-no})))
       (catch Throwable t nil))

    (remove
      #(nil? (re-find #"^[0-9]+\.dat$" (.getName %1)))
      file-list)))

(defn reconstruct-dat-file
  [server posts]
  (let [res-count (count posts)
        posts (if (or (shitaraba-server? server) (machi-bbs-server? server))
                (map #(str %2 "<>" %1) posts (range 1 (inc res-count)))
                posts)]
    (.getBytes (str (clojure.string/join "\n" posts) "\n") (get-encoding-for-get-method server))))

(defn import-rep2-dat-files
  [user-id file-list]
  (timbre/info "Importing rep2/rep2ex dat files...")
  (try
    (let [dat-files (search-for-dat-files file-list)
          dat-file-count (count dat-files)
          start-time (System/currentTimeMillis)
          results (doall (map #(try
                                 (cond
                                   (nil? %1)
                                   :error

                                   (nil? (:server %1))
                                   (do (timbre/info "Error:" (.getPath (:file %1)) "Server information not available.") :error)

                                   :else
                                   (let [dat-content (slurp (:file %1) :encoding "Windows-31J")
                                         res-count (count (clojure.string/split-lines dat-content))
                                         posts          (clojure.string/split dat-content #"\n")
                                         binary-content (if (or (shitaraba-server? (:server %1)) (machi-bbs-server? (:server %1)))
                                                          (reconstruct-dat-file (:server %1) posts)
                                                          (with-open [out (java.io.ByteArrayOutputStream.)]
                                                            (clojure.java.io/copy (clojure.java.io/input-stream (:file %1)) out)
                                                            (.toByteArray out)))
                                         old-dat-file   (db/get-dat-file-with-user-id-without-content user-id (:service %1) (:board %1) (:thread-no %1))
                                         new-dat-file   {:user_id       user-id
                                                         :service       (:service %1)
                                                         :server        (:server %1)
                                                         :board         (:board %1)
                                                         :thread_no     (:thread-no %1)
                                                         :etag          nil
                                                         :last-modified nil
                                                         :content       binary-content
                                                         :res_count     res-count
                                                         :size          (count binary-content)
                                                         :source_url    (thread-url-to-dat-url (create-thread-url (:server %1) (:board %1) (:thread-no %1)))}
                                         title          (nth (clojure.string/split (first posts) #"<>") 4 nil)
                                         title          (and title (unescape-html-entities title))
                                         valid?         (and title (valid-dat-content? dat-content))]
                                     (cond
                                       (not valid?) (do (timbre/info "Invalid DAT file:" (.getPath (:file %1)) (get-progress start-time %2 %3)) :invalid)
                                       old-dat-file (do (timbre/info "DAT file already exists in database:" (.getPath (:file %1)) (get-progress start-time %2 %3)) :duplicate)
                                       :else        (do
                                                      (db/add-dat-file new-dat-file)
                                                      (db/update-thread-title (:service %1) (:server %1)  (:board %1) (:thread-no %1) title)
                                                      (db/update-thread-res-count (:service %1) (:server %1) (:board %1) (:thread-no %1) res-count)
                                                      (db/update-bookmark-with-user-id user-id (:service %1) (:board %1) (:thread-no %1) res-count)
                                                      (db/update-board-server-if-there-is-no-info (:service %1) (:server %1)  (:board %1))
                                                      (if (re-find #"<>Over [0-9]+ (Thread|Comments)[^<>]*<>" (last posts))
                                                        (db/mark-thread-as-archived (:service %1) (:server %1) (:board %1) (:thread-no %1)))
                                                      (timbre/info "Imported DAT file:" (.getPath (:file %1)) (get-progress start-time %2 %3))
                                                     :imported))))
                                 (catch Throwable t (timbre/error "Error:" (.getPath (:file %1)) (str t) (get-progress start-time %2 %3)) :error))

                              dat-files
                              (range 1 (inc dat-file-count))
                              (repeat dat-file-count)))]
      (timbre/info "Imported rep2/rep2ex dat files:")
      (timbre/info (format "    %s %7d" "Imported:  " (count (remove #(not (= %1 :imported )) results))))
      (timbre/info (format "    %s %7d" "Duplicates:" (count (remove #(not (= %1 :duplicate)) results))))
      (timbre/info (format "    %s %7d" "Invalid:   " (count (remove #(not (= %1 :invalid  )) results))))
      (timbre/info (format "    %s %7d" "Errors:    " (count (remove #(not (= %1 :error    )) results)))))
    (catch Throwable t (clojure.stacktrace/print-stack-trace t) )))

(defn process-p2-favita-brd-item
  [user-id item]
  (let [fields (clojure.string/split item #"\t")
        shitaraba-category (nth (clojure.string/split (nth fields 1) #"/") 1 nil)
        server (first (clojure.string/split (nth fields 1) #"/"))
        server (if (= server "jbbs.livedoor.jp") "jbbs.shitaraba.net" server)
        service (server-to-service server)
        board (str (if shitaraba-category (str shitaraba-category "/")) (nth fields 2))
        board-name (nth fields 3)]
    (when (and (re-find #"^[a-z0-9.-]+$" service)
               (re-find #"^[a-z0-9.-]+$" server)
               (re-find #"^[a-zA-Z0-9/]+$" board)
               (> (count board-name) 0))
      ; (timbre/debug service server board board-name)
      (db/update-board-name service server board board-name)
      (db/add-favorite-board {:user-id   user-id
                              :service   service
                              :server    server
                              :board     board})
      nil)))

(defn import-p2-favita-brd
  [user-id file-list]
  (try
    (let [results (doall (map #(try
                                 (doall (for [item (clojure.string/split-lines (slurp %1 :encoding "Windows-31J"))]
                                          (do
                                            (process-p2-favita-brd-item user-id item))))
                                 (timbre/info "Imported:" (.getPath %1))
                                 (catch Throwable t (timbre/error "Error:" (.getPath %1) (str t)) :error))

                              (remove
                                #(nil? (re-find #"^p2_favita\.brd$" (.getName %1)))
                                file-list)))]
      nil)
    (catch Throwable t (clojure.stacktrace/print-stack-trace t) )))

(defn process-p2-favlist-idx-item
  [user-id item]
  (let [fields (clojure.string/split item #"<>")
        shitaraba-category (nth (clojure.string/split (nth fields 10) #"/") 1 nil)
        server (first (clojure.string/split (nth fields 10) #"/"))
        server (if (= server "jbbs.livedoor.jp") "jbbs.shitaraba.net" server)
        service (server-to-service server)
        board (str (if shitaraba-category (str shitaraba-category "/")) (nth fields 11))
        thread-title (unescape-html-entities (nth fields 0))
        thread-no (java.lang.Long/parseLong (second fields))]
    (when (and (re-find #"^[a-z0-9.-]+$" service)
               (re-find #"^[a-z0-9.-]+$" server)
               (re-find #"^[a-zA-Z0-9/]+$" board)
               (> (count thread-title) 0))
      ; (timbre/debug service server board thread-no thread-title)
      (db/update-thread-title service server board thread-no thread-title)
      (db/add-favorite-thread {:user-id   user-id
                               :service   service
                               :server    server
                               :board     board
                               :thread-no thread-no})
      nil)))

(defn import-p2-favlist-idx
  [user-id file-list]
  (try
    (let [results (doall (map #(try
                                 (doall (for [item (clojure.string/split-lines (slurp %1 :encoding "Windows-31J"))]
                                          (do
                                            (process-p2-favlist-idx-item user-id item))))
                                 (timbre/info "Imported:" (.getPath %1))
                                 (catch Throwable t
                                   (timbre/error "Error:" (.getPath %1) (str t))
                                   ; (clojure.stacktrace/print-stack-trace e)
                                   :error))

                              (remove
                                #(nil? (re-find #"^p2_favlist\.idx$" (.getName %1)))
                                file-list)))]
      nil)
    (catch Throwable t (clojure.stacktrace/print-stack-trace t) )))

(defn process-p2-recent-idx-item
  [user-id item]
  (let [fields (clojure.string/split item #"<>")
        shitaraba-category (nth (clojure.string/split (nth fields 10) #"/") 1 nil)
        server (first (clojure.string/split (nth fields 10) #"/"))
        server (if (= server "jbbs.livedoor.jp") "jbbs.shitaraba.net" server)
        service (server-to-service server)
        board (str (if shitaraba-category (str shitaraba-category "/")) (nth fields 11))
        thread-title (unescape-html-entities (nth fields 0))
        thread-no (java.lang.Long/parseLong (second fields))]
    (when (and (re-find #"^[a-z0-9.-]+$" service)
               (re-find #"^[a-z0-9.-]+$" server)
               (re-find #"^[a-zA-Z0-9/]+$" board)
               (> (count thread-title) 0))
      ; (timbre/debug service server board thread-no thread-title)
      (db/update-thread-title service server board thread-no thread-title)
      (db/update-time-last-viewed-with-user-id user-id service board thread-no (clj-time.coerce/to-sql-time (clj-time.core/now)))
      nil)))

(defn import-p2-recent-idx
  [user-id file-list]
  (try
    (let [results (doall (map #(try
                                 (doall (for [item (reverse (clojure.string/split-lines (slurp %1 :encoding "Windows-31J")))]
                                          (do
                                            (process-p2-recent-idx-item user-id item))))
                                 (timbre/info "Imported:" (.getPath %1))
                                 (catch Throwable t
                                   (timbre/error "Error:" (.getPath %1) (str t))
                                   (clojure.stacktrace/print-stack-trace t)
                                   :error))

                                (remove
                                  #(nil? (re-find #"^p2_recent\.idx$" (.getName %1)))
                                  file-list)))]
      nil)
    (catch Throwable t (clojure.stacktrace/print-stack-trace t) )))

(defn import-rep2-data
  [user-email path]

  (timbre/info "Scanning for files...")
  (try
    (let [user    (db/get-user-with-email user-email)
          user-id (:id user)
          file-list (file-seq (java.io.File. path))]
      (if (nil? user)
        (throw (Exception. "User not found.")))
      (import-rep2-dat-files user-id file-list)
      (import-p2-favita-brd  user-id file-list)
      (import-p2-favlist-idx  user-id file-list)
      (import-p2-recent-idx  user-id file-list))
    (catch Throwable t
      (timbre/error t)
      (clojure.stacktrace/print-stack-trace t))))
