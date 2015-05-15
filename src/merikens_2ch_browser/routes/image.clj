(ns merikens-2ch-browser.routes.image
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
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
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.db.core :as db]
            [com.climate.claypoole :as cp])
  (:import [org.apache.http.impl.cookie BasicClientCookie]
           [java.io File ByteArrayInputStream ByteArrayOutputStream FileOutputStream FileInputStream]
           java.awt.image.BufferedImage
           java.awt.Image
           java.awt.Toolkit
           javax.imageio.ImageIO
           org.apache.commons.io.IOUtils))



(defonce ^:dynamic *default-image-ng-filters* (atom {}))

(defn get-default-image-ng-filters []
  @*default-image-ng-filters*)

(defn set-default-image-ng-filters
  [new-filters]
  (swap! *default-image-ng-filters* #(do %2) new-filters))

(defonce ^:dynamic *default-image-url-ng-filters* (atom {}))

(defn get-default-image-url-ng-filters []
  @*default-image-url-ng-filters*)

(defn set-default-image-url-ng-filters
  [new-filters]
  (swap! *default-image-url-ng-filters* #(do %2) new-filters))

(defn load-default-image-ng-filters
  []
  (try
    (set-default-image-ng-filters
      (apply merge
             (map #(hash-map (clojure.string/replace %1 #"=.*$" "") true)
                  (remove #(not (re-find #"^[0-9A-V]{26}=" %1))
                          (clojure.string/split-lines (slurp (io/resource "NGFiles.txt") :encoding "Windows-31J"))))))
    (set-default-image-url-ng-filters
      (apply merge
             (map #(hash-map (clojure.string/replace %1 #"=.*$" "") true)
                  (remove #(not (re-find #"^https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()]*[-a-zA-Z0-9+&@#/%=~_|]$" %1))
                          (clojure.string/split-lines (slurp (io/resource "NGURLs.txt" ) :encoding "Windows-31J"))))))
    (catch Throwable t
      (timbre/error "Failed to load default image NG filters:" (str t))
      (print-stack-trace t))))

(defn get-first-element-with-key
  [k l]
  (some #(if (re-find (re-pattern (str "^" k)) %1) %1 nil)
        l))

(defn save-ng-files-txt
  []
  (try
    (let [encoding    "Windows-31J"
          unsorted-list (remove #(not (re-find #"^[0-9A-V]{26}=" %1))
                                (clojure.string/split-lines (slurp (io/resource "NGFiles.txt") :encoding encoding)))
          unique-hash-list (distinct (map #(clojure.string/replace %1 #"^([0-9A-V]{26})=.*$" "$1") unsorted-list))
          sorted-list (sort
                        #(compare (clojure.string/replace %1 #"^[0-9A-V]{26}=" "")
                                  (clojure.string/replace %2 #"^[0-9A-V]{26}=" ""))
                        (map #(get-first-element-with-key %1 unsorted-list) unique-hash-list))
          output-file "./NGFiles.txt"]
      ; (timbre/info "unsorted-list:" (count unsorted-list))
      ; (timbre/info "sorted-list:  " (count sorted-list))
      (spit output-file "" :append false :encoding encoding)
      (doall
        (map #(spit output-file (str %1 "\r\n") :append true :encoding encoding)
             sorted-list)))
    (catch Throwable t
      (print-stack-trace t))))

(defn ng-image?
  [image]
  (let [extra-info (db/get-image-extra-info (:id image))]
    (or ((get-default-image-ng-filters) (:md5-string extra-info))
        ((get-default-image-url-ng-filters) (:url image))
        (db/get-post-filter-with-image-md5-string (:user-id image) (:md5-string extra-info))
        (db/get-post-filter-with-image-url (:user-id image) (:url image)))))

(defn ng-image-md5-string?
  [user-id md5-string]
  (or ((get-default-image-ng-filters) md5-string)
      (db/get-post-filter-with-image-md5-string user-id md5-string)))

(defn ng-image-url?
  [user-id url]
  (or ((get-default-image-url-ng-filters) url)
      (db/get-post-filter-with-image-url user-id url)))



(defn create-thumbnail
  [^java.awt.Image awt-image]
  ; (timbre/debug "create-thumbnail")
  (try
    (let [output    (new ByteArrayOutputStream 1000)
          thumbnail (new BufferedImage thumbnail-width thumbnail-height BufferedImage/TYPE_INT_RGB)

          width     (.getWidth awt-image)
          height    (.getHeight awt-image)

          thumbnail-ratio (/ thumbnail-width thumbnail-height)
          image-ratio     (/ width height)
          iw (if (> thumbnail-ratio image-ratio) (* thumbnail-height image-ratio) thumbnail-width)
          ih (if (> thumbnail-ratio image-ratio) thumbnail-height (/ thumbnail-width image-ratio))
          ix (if (> thumbnail-ratio image-ratio) (/ (- thumbnail-width iw) 2) 0)
          iy (if (> thumbnail-ratio image-ratio) 0 (/ (- thumbnail-height ih) 2))
          ]

      (-> thumbnail
        (.createGraphics)
        (.drawImage (.getScaledInstance
                      awt-image
                      (int iw)
                      (int ih)
                      Image/SCALE_SMOOTH)
          (int ix)
          (int iy)
          nil))
      (ImageIO/write thumbnail "png" output)
      (.flush output)
      (let [byte-array (.toByteArray output)]
        (.close output)
        byte-array))

    (catch Throwable t
      (try
        ; (timbre/debug "create-thumbnail: Creating a blank thumbnail.")
        ; (print-stack-trace t)
        (let [output    (new ByteArrayOutputStream 1000)
              thumbnail (new BufferedImage thumbnail-width thumbnail-height BufferedImage/TYPE_INT_RGB)]
          (ImageIO/write thumbnail "png" output)
          (.flush output)
          (let [byte-array (.toByteArray output)]
            (.close output)
            byte-array))
        (catch Throwable t
          (timbre/debug "create-thumbnail: Unexpected exception:" (str t))
          ; (print-stack-trace t)
          nil)))))

(defn update-thumbnails
  []
  (let [start-time  (java.lang.System/currentTimeMillis)
        image-ids   (db/get-all-image-ids)
        image-count (count image-ids)]
    (doall
      (pmap
        #(let [image (db/get-image-with-id %1)
               awt-image (ImageIO/read (new ByteArrayInputStream (:content image)))
               width     (.getWidth awt-image)
               height    (.getHeight awt-image)
               thumbnail (create-thumbnail awt-image)]
           (if (= 0 (mod %1 100))
             (timbre/info "Updated thumbnails" (get-progress start-time (min %1 image-count) image-count)))
           (db/update-image (:id image) (create-thumbnail awt-image) width height))
        image-ids))))

(defn find-ng-images
  []
 (let [ng-filters (get-default-image-ng-filters)]
    (doall
      (map
        #(let [extra-info (db/get-image-extra-info %1)
              md5-string (:md5-string extra-info)]
          ; (timbre/info ng-filters md5-string)
          (if (ng-filters md5-string)
            (let [image (db/get-image-with-id %1)]
               ; (println "NG Image:" (:id image) (:extension image) (:url image) (:thread-url image)))))
              (println (:url image)))))
        (db/get-all-image-ids)))))

(defn set-up-download
  [url thread-url]
  ; (timbre/info "set-up-download:" url thread-url)
  (try
    (cond
      ; (ng-image-url? url)
      ; (timbre/info "set-up-download: The image (" url ") was blocked by an NG filter.")

      (db/get-image-with-url-without-content-and-thumbnail url)
      (timbre/info (str "Image is already in the database:\n"
                        "    " url))

      (or (db/get-active-download (:id (session/get :user)) url)
          (db/get-pending-download (:id (session/get :user)) url))
      (timbre/info (str "Image is already being downloaded:\n"
                        "    " url))

      :else
      (db/add-download {:user_id    (:id (session/get :user))
                        :url        url
                        :thread_url thread-url
                        :status     "pending"
                        :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))}))
    (catch Throwable t
      (timbre/error "set-up-download: Unexpected exception:" (str t))
      (print-stack-trace t))))

(defn invalid-image?
  [url size width height]
  (or
    (and (re-find #"^http://([^./]+\.)?sankakustatic.com/" url)
         (= size 12922)
         (= width 392)
         (= height 150))
    (and (re-find #"^http://(i\.)?imgur\.com/" url)
         (= size 503)
         (= width 161)
         (= height 81)
         )))

(defn download-image
  [user-id url thread-url retries]
  ; (timbre/debug "download-image:" user-id url thread-url retries)
  (try
    (cond
      (nil? @server)
      nil

      (re-find #"^http://(www.amaga.me|www.uproda.net)/" url)
      (do
        (timbre/info (str "Image is no longer available:\n"
                          "    " url))
        (db/add-download {:user_id user-id
                          :url url
                          :thread_url     thread-url
                          :status "failed"
                          :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))})
        nil)

      (db/get-image-with-user-id-and-url-without-content-and-thumbnail user-id url)
      (timbre/info (str "Image is already in the database:\n"
                        "    " url))

      ; (ng-image-url? url)
      ; (timbre/debug "download-image: The image (" url ") was blocked by an NG filter.")

      (or (db/get-active-download user-id url)
          (db/get-pending-download user-id url))
      (timbre/info (str "Image is already being downloaded:\n"
                        "    " url))

      (not (= (db/get-user-setting-with-user-id user-id "download-images") "true"))
      (timbre/info (str "Image downloading is disabled:\n"
                        "    " url))

      :else
      (do
        (timbre/info (str "Started image download (retries: " retries "):\n"
                          "    " url))
        (db/add-download {:user_id user-id
                          :url url
                          :thread-url thread-url
                          :status "active"
                          :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))
                          :retry_count retries})
        (wait-for-http-requests-to-be-processed)
        (if (not (db/get-active-download user-id url))
          (throw (Exception.)))
        (let [response (clj-http.client/get
                         url
                         {:as      :byte-array
                          :headers {"User-Agent"    user-agent
                                    "Cache-Control" "no-cache"}
                          :socket-timeout 60000
                          :conn-timeout   60000})
              headers  (into {} (for [[k v] (:headers response)] [(keyword k) v]))]
          (wait-for-http-requests-to-be-processed)
          (if (or (nil? @server)
                  (not (db/get-active-download user-id url)))
            (throw (Exception. "Image download was aborted.")))
          ; (timbre/debug "Content-Type:" (:Content-Type headers))
          (if (not (= (:status response) 200))
            (throw (Exception. (str "status " (:status response)))))
          (when (or (nil? (:Content-Type headers))
                    (not (re-find #"^image(/|%2F)" (:Content-Type headers)))) ; "%2F" for http://i.minus.com/
            (throw (Exception. (str "Wrong content type: " (:Content-Type headers)))))

          (let [headers (into {} (for [[k v] (:headers response)] [(keyword k) v]))
                awt-image  (ImageIO/read (new ByteArrayInputStream (:body response)))
                width      (.getWidth awt-image)
                height     (.getHeight awt-image)
                thumbnail  (create-thumbnail awt-image)
                size       (count (:body response))
                md5-string (db/create-md5-string (:body response))]
            (if (or (nil? thumbnail)
                    (invalid-image? url size width height))
              (throw (Exception. "Not a valid image.")))
            (wait-for-http-requests-to-be-processed)
            (timbre/info (str "Downloaded image:\n"
                              "    " url "\n"
                              "    " (format "dimentions: %dx%d\n" width height)
                              "    " "retries: " retries))
            (when (and (ng-image-url? user-id url) (not (ng-image-md5-string? user-id md5-string)))
              ; Add an NG filter for the MD5 string.
              (db/add-post-filter {:user-id     user-id
                                   :filter-type "image-md5-string"
                                   :pattern     md5-string
                                   :invisible   false}))
            (db/delete-active-download user-id url)
            (if (not (db/get-image-with-user-id-and-url-without-content-and-thumbnail user-id url))
              (do
                (db/add-image {:user-id        user-id
                               :thread-url     thread-url
                               :url            url
                               :extension      (second (re-find #"(\.[a-zA-Z]+)$" url))
                               :time-retrieved (clj-time.coerce/to-sql-time (clj-time.core/now))
                               :content        (:body response)
                               :size           size
                               :width          width
                               :height         height
                               :thumbnail      thumbnail})
                (db/add-download {:user_id      user-id
                                  :url url
                                  :thread_url     thread-url
                                  :status "successful"
                                  :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))
                                  :retry_count retries})))
            nil))))
    (catch Throwable t
      (try
        (wait-for-http-requests-to-be-processed)
        (let [download (and @server
                            (db/get-active-download user-id url))]
          ; (timbre/debug "download-image:" "Exception caught:" (str t))
          ; (print-stack-trace t)
          (cond
            (not download)
            (do
              (timbre/info (str "Image download was aborted:\n"
                                "     " url))
              nil)

            (or (>= retries maxinum-number-of-retries-for-image-downloads)
                (re-find #"status 403" (str t))
                (re-find #"status 404" (str t))
                (re-find #"^java.lang.Exception: Not a valid image." (str t))
                (re-find #"^java.lang.ArrayIndexOutOfBoundsException" (str t)) ; cannot handle the image.
                (re-find #"^java.net.UnknownHostException" (str t))
                (re-find #"^java.lang.Exception: Wrong content type:" (str t)))
            (do
              (timbre/info (str "Image download failed (retries: " retries "):\n"
                          "    " (clojure.string/replace (str t) #"\{.*\}$" "")))
              (db/delete-active-download user-id url)
              (db/add-download {:user_id user-id
                                :url url
                                :thread_url     thread-url
                                :status "failed"
                                :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))})
              nil)

            :else
            (do
              (timbre/info (str "Retrying image download (retries: " retries "):\n"
                                "    " (clojure.string/replace (str t) #"\{.*\}$" "") "\n"
                                "    " url))
              (db/delete-active-download user-id url)
              (db/add-download {:user_id user-id
                                :url url
                                :thread_url     thread-url
                                :status "pending"
                                :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))
                                :retry_count (inc retries)})
              nil)))

        (catch Throwable t
          (timbre/error "download-image: Unexpected exception:" (str t))
          ; (print-stack-trace t)
          (db/delete-active-download user-id url)
          (db/add-download {:user_id user-id
                            :url url
                            :thread_url     thread-url
                            :status "failed"
                            :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))}))))))

(defn create-directory
  [path]
  (let [dir (File. path)]
    (if (not (.exists dir))
      (.mkdir dir))))

(defn update-file-path
  [path count]
  (let [extension (second (re-find #"(\.[a-zA-Z]+)$" path))
        path-without-extension (clojure.string/replace path #"(\.[a-zA-Z]+)$" "")
        new-path (if (= count 0)
                   path
                   (str path-without-extension " (" count ")" extension))]
    (if (not (.exists (File. new-path)))
      new-path
      (update-file-path path (inc count)))))

(defn save-image-as-file
  [image-content url thread-url]
  ; (timbre/debug "save-image-as-file")
  (let [sep (File/separator)
        {:keys [service server board thread-no]} (split-thread-url thread-url)
        board-dir (clojure.string/replace board #"/" "-")
        file-name (clojure.string/replace (second (re-find #"/([^/]+)$" url)) #"[?%*:|\"<>#]" "_")
        new-file-path (update-file-path (str "." sep "images" sep service sep board-dir sep thread-no sep file-name) 0)]
    (create-directory (str "." sep "images"))
    (create-directory (str "." sep "images" sep service))
    (create-directory (str "." sep "images" sep service sep board-dir))
    (create-directory (str "." sep "images" sep service sep board-dir sep thread-no))
    ; (-> ...) does not work.
    (let [new-file (FileOutputStream. new-file-path)]
      (.write new-file image-content)
      (.close new-file))))

(defn process-one-unsaved-image
  []
  ;(timbre/debug "process-one-unsaved-image")
  (try
    (let [image (db/get-next-unsaved-image)]
      (when image
        (if (ng-image? image)
          (timbre/info (str "Image was not saved because it was blocked by an NG filter:\n"
                            "    " (:url image)))
          (do
            (save-image-as-file (:content image) (:url image) (:thread-url image))
            (timbre/info (str "Saved image as file:\n"
                              "    " (:url image)))))
        (db/mark-image-as-saved (:id image))
        ))
    (catch Throwable t
      (timbre/error "Failed to save image as file:" (str t))
      ; (print-stack-trace t)
      )))

(defn start-download-manager
  []
  ; (timbre/debug "start-download-manager")
  (do
    (future
      (do
        (loop []
          (Thread/sleep polling-interval-for-download-manager)
          (if (nil? @server) (recur)))
        (timbre/info "Download manager started.")
        (db/restart-all-active-downloads)
        (loop []
          (try
            ;(timbre/debug "wait")
            (wait-for-http-requests-to-be-processed)

            ; Set up image downloads.
            ;(timbre/debug "download")
            (let [maximum-number-of-downloads (and @server
                                                   (or (db/get-system-setting "maximum-number-of-image-downloads")
                                                       default-maximum-number-of-image-downloads))
                  download (and @server
                                (< (db/count-downloads-all-users "active") default-maximum-number-of-image-downloads)
                                (db/pop-next-pending-download))]
              (if download
                (do
                  ; (cp/future
                  ;     thread-pool-for-downloads
                  (future
                    (download-image (:user-id download) (:url download) (:thread-url  download) (:retry-count download))))))

            ; Process an unsaved image.
            ;(timbre/debug "unsaved image")
            (if (and @server (= (db/get-system-setting "save-downloaded-images") "true"))
              (process-one-unsaved-image))

            ;(timbre/debug "unsaved interval")
            (Thread/sleep polling-interval-for-download-manager)

            (catch Throwable t
              (timbre/error "download-manager: Unexpected exception:" (str t))
              ; (print-stack-trace t)
              ))
          (if @server (recur)))
          (timbre/info "Download manager stopped.")))))

(defn api-get-image
  [id extension]
  (when (check-login)
    (try
      (timbre/info "Preparing image...")
      (increment-http-request-count)
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
      (let [start-time (System/nanoTime)
            image      (db/get-image-with-id (Integer/parseInt id))
            body       (and image (new java.io.ByteArrayInputStream (:content image)))]
        ; (println image extension (:extension image))
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (if (or (nil? image) (not (= extension (:extension image))))
          (ring.util.response/not-found "404 Not Found")
          (do
            (timbre/info (str "    Size:       " (count (:content image)) "bytes"))
            (timbre/info (str "    Dimensions: " (:width image) "x" (:height image)))
            (timbre/info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
            {:status  200
             :headers {"Content-Type" (str "image/" (-> (second (re-find #"^\.(.*)$" (:extension image)))
                                                      (clojure.string/replace #"(?i)^jpg$" "jpeg")
                                                      (clojure.string/replace #"(?i)^png$" "png")
                                                      (clojure.string/replace #"(?i)^gif$" "gif")))}
             :body body})))

      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (timbre/error "api-get-image:" (str t))
        (.printStackTrace t)
        (internal-server-error)))))

(defn api-get-thumbnail
  [id]
  ; (timbre/debug "get-image:" id extension)
  (when (check-login)
    (try
      (increment-http-request-count)
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
      (let [result (let [image (db/get-image-with-id-without-content (Integer/parseInt id))]
                     ; (println image extension (:extension image))
                     (if (nil? image)
                       (ring.util.response/not-found "404 Not Found")
                       {:status  200
                        :headers {"Content-Type" "image/jpeg"}
                        :body (new java.io.ByteArrayInputStream (:thumbnail image))}))]
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        result)

      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (timbre/error "api-get-thumbnail:" (str t))
        ; (print-stack-trace t)
        {:status  500}))))

(defn api-configure-image-downloading
  [enable]
  ; (timbre/debug "api-configure-image-downloading:" enable)
  (when (check-login)
    (db/update-user-setting "download-images" (if (= enable "1") "true" "false"))
    (if (not (= enable "1")) (db/delete-all-active-and-pending-downloads))
    "OK"))

(defn api-stop-current-downloads
  []
  (when (check-login)
    (db/delete-all-active-and-pending-downloads)
    "OK"))

(defn api-get-download-status
  []
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (try
      (html
        (if (check-admin-login)
          (list [:div [:div {:style "float:left;"} "画像数:"] [:div {:style "float:right;"} (str (db/count-images) "枚")]] [:div {:style "clear: both;"}]
                [:div [:div {:style "float:left;"} "ダウンロード中:"] [:div {:style "float:right;"} (str (db/count-downloads "active") "枚")]] [:div {:style "clear: both;"}]
                [:div [:div {:style "float:left;"} "ダウンロード待機中:"] [:div {:style "float:right;"} (str (db/count-downloads "pending") "枚")]] [:div {:style "clear: both;"}]
                ; [:div [:div {:style "float:left;"} "ダウンロード成功:"] [:div {:style "float:right;"} (str (db/count-downloads "successful") "回")]] [:div {:style "clear: both;"}]
                ; [:div [:div {:style "float:left;"} "ダウンロード失敗:"] [:div {:style "float:right;"} (str (db/count-downloads "failed") "回")]] [:div {:style "clear: both;"}]
                [:div [:div {:style "float:left;"} "画像数(全ユーザー):"] [:div {:style "float:right;"} (str (db/count-images-for-all-users) "枚")]] [:div {:style "clear: both;"}]
                ; [:div [:div {:style "float:left;"} "ファイル出力済:"] [:div {:style "float:right;"} (str (db/count-saved-images) "枚")]] [:div {:style "clear: both;"}]
                [:div [:div {:style "float:left;"} "ファイル未出力:"] [:div {:style "float:right;"} (str (db/count-unsaved-images) "枚")]] [:div {:style "clear: both;"}]
                )

          (list [:div [:div {:style "float:left;"} "画像数:"] [:div {:style "float:right;"} (str (db/count-images) "枚")]] [:div {:style "clear: both;"}]
                [:div [:div {:style "float:left;"} "ダウンロード中:"] [:div {:style "float:right;"} (str (db/count-downloads "active") "枚")]] [:div {:style "clear: both;"}]
                [:div [:div {:style "float:left;"} "ダウンロード待機中:"] [:div {:style "float:right;"} (str (db/count-downloads "pending") "枚")]] [:div {:style "clear: both;"}]
                ;[:div [:div {:style "float:left;"} "ダウンロード成功:"] [:div {:style "float:right;"} (str (db/count-downloads "successful") "回")]] [:div {:style "clear: both;"}]
                ;[:div [:div {:style "float:left;"} "ダウンロード失敗:"] [:div {:style "float:right;"} (str (db/count-downloads "failed") "回")]] [:div {:style "clear: both;"}]
                )))

      (catch Throwable t
        (timbre/error "download-manager: Unexpected exception:" (str t))
        ; (print-stack-trace t)
        ))))

(defn api-image-proxy
  [thread-url url thumbnail?]
  ; (timbre/debug "api-image-proxy:" thread-url url)
  (let [url (clojure.string/replace url #"\?[0-9]+$" "")]
    (cond
      (or (not (check-login))
          (not (split-thread-url thread-url)))
      nil

      ; The image is already in the database.
      (db/get-image-with-url-without-content-and-thumbnail url)
      (try
        (let [image (db/get-image-with-url url)]
          ; Serve the image to the client.
          (if (ng-image? image)
            (do
              (timbre/info (str "Image was blocked by proxy:\n"
                                "    " url))
              (noir.io/get-resource image-thumbnail-ng-src))
            (do
              (timbre/info (str "Served image in database through proxy:\n"
                                "    " url))
              {:status  200
               :headers {"Content-Type" (str "image/"
                                             (cond (or thumbnail? (re-find #"^(?i)\.jpe?g$" (:extension image))) "jpeg"
                                                   (re-find #"^(?i)\.bmp$"   (:extension image)) "bmp"
                                                   (re-find #"^(?i)\.png$"   (:extension image)) "png"
                                                   (re-find #"^(?i)\.gif$"   (:extension image)) "gif"
                                                   :else (throw (Exception. "Unknown extension."))))
                         "Cache-Control" "private"}
               :body    (ByteArrayInputStream. (if thumbnail? (:thumbnail image) (:content image)))})))
        (catch Throwable t
          (timbre/error (str "Failed to download image through proxy:\n"
                             "    " (clojure.string/replace (str t) #"(clj-http: status [0-9]+) .*$" "$1") "\n"
                             "    " url))
          ; (print-stack-trace t)
          (decrement-http-request-count)
          nil))

      ; The image is not in the database.
      :else
      (try
        (increment-http-request-count)
        (let [user-id (:id (session/get :user))
              options  {:as      :byte-array
                        :headers {"User-Agent"    user-agent
                                  "Cache-Control" "no-cache"}
                        :socket-timeout 60000
                        :conn-timeout   60000}
              options  (merge options (proxy-server url :get))
              response (clj-http.client/get url options)
              headers  (into {} (for [[k v] (:headers response)] [(keyword k) v]))]
          (if (not (= (:status response) 200))
            (throw (Exception. (str "status " (:status response)))))
          (when (or (nil? (:Content-Type headers))
                    (not (re-find #"^image(/|%2F)" (:Content-Type headers)))) ; "%2F" for http://i.minus.com/
            ; (timbre/debug "Wrong content type: " (:Content-Type headers))
            (throw (Exception. (str "Wrong content type: " (:Content-Type headers)))))

          (let [awt-image  (ImageIO/read (new ByteArrayInputStream (:body response)))
                width      (.getWidth awt-image)
                height     (.getHeight awt-image)
                thumbnail  (create-thumbnail awt-image)
                size       (count (:body response))
                md5-string (db/create-md5-string (:body response))]
            (if (or (nil? thumbnail)
                    (invalid-image? url size width height))
              (throw (Exception. "Not a valid image.")))
            (timbre/info (str "Downloaded image through proxy:\n"
                              "    " url))

            ; Save the image to the database.
            (db/delete-active-download user-id url)
            (db/delete-pending-download user-id url)
            (when (and
                    (= (db/get-user-setting "download-images") "true")
                    (not (db/get-image-with-user-id-and-url-without-content-and-thumbnail user-id url)))
              (do
                (db/add-image {:user-id        user-id
                               :thread-url     thread-url
                               :url            url
                               :extension      (second (re-find #"(\.[a-zA-Z]+)$" url))
                               :time-retrieved (clj-time.coerce/to-sql-time (clj-time.core/now))
                               :content        (:body response)
                               :size           size
                               :width          width
                               :height         height
                               :thumbnail      thumbnail})
                (db/add-download {:user_id      user-id
                                  :url url
                                  :thread_url     thread-url
                                  :status "successful"
                                  :time_updated (clj-time.coerce/to-sql-time (clj-time.core/now))
                                  :retry_count 0})))

            ; Serve the image to the client.
            (decrement-http-request-count)
            (cond
              ; The downloaded image is NG and there is already a MD5 string filter for it.
              (ng-image-md5-string? user-id md5-string)
              (do
                (timbre/info (str "Image was blocked by proxy:\n"
                                  "    " url))
                (noir.io/get-resource image-thumbnail-ng-src))

              ; The downloaded image is NG and there is no MD5 string filter for it.
              (ng-image-url? user-id url)
              (do
                (timbre/info (str "Image was blocked by proxy:\n"
                                  "    " url))
                (db/add-post-filter {:user-id     (:id (session/get :user))
                                     :filter-type "image-md5-string"
                                     :pattern     md5-string
                                     :invisible   false})
                (noir.io/get-resource image-thumbnail-ng-src))

              ; The downloaded image is not NG.
              :else
              {:status  200
               :headers {"Content-Type"  (if thumbnail? "image/jpeg" (:Content-Type headers))
                         "Cache-Control" "private"}
               :body    (ByteArrayInputStream. (if thumbnail? thumbnail (:body response)))})))
        (catch Throwable t
          (timbre/error (str "Failed to download image through proxy:\n"
                             "    " (clojure.string/replace (str t) #"(clj-http: status [0-9]+) .*$" "$1") "\n"
                             "    " url))
          ; (print-stack-trace t)
          (decrement-http-request-count)
          nil)))))

(defn api-add-ng-image
  "Add an NG filter either with an URL or an MD5 string.
   md5-string can be empty."
  [url md5-string]
  ; (timbre/debug "api-add-ng-image:" url md5-string)
  (try
    (if (or
          (not (check-login))
          (nil? (re-find #"^https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()]*[-a-zA-Z0-9+&@#/%=~_|]$" url))
          (nil? (re-find #"^(|[0-9A-V]{26})$" md5-string)))
      (do
        ; (timbre/debug "api-add-ng-image: Invalid request:" url md5-string)
        (ring.util.response/not-found "404 Not Found"))
      (let [image (db/get-image-with-url-without-content-and-thumbnail url)
            image-extra-info (and image
                                  (db/get-image-extra-info (:id image)))]
        (cond
          ; Add a filter with a new MD5 string.
          (and (> (count md5-string) 0)
               (not (ng-image-md5-string? (:id (session/get :user)) md5-string)))
          (db/add-post-filter {:user-id     (:id (session/get :user))
                               :filter-type "image-md5-string"
                               :pattern     md5-string
                               :invisible   false})

          ; Add a filter with an existing MD5 string.
          (and image
               (:md5-string image)
               (not (ng-image-md5-string? (:id (session/get :user)) (:md5-string image))))
          (db/add-post-filter {:user-id     (:id (session/get :user))
                               :filter-type "image-md5-string"
                               :pattern     (:md5-string image)
                               :invisible   false})

          ; Add a filter with an URL.
          (not (ng-image-url? (:id (session/get :user)) url))
          (db/add-post-filter {:user-id     (:id (session/get :user))
                               :filter-type "image-url"
                               :pattern     url
                               :invisible   false}))
        "OK"))

    (catch Throwable t
      (timbre/error "Failed to add NG Image:\n"
                    "    " (str t))
      ; (print-stack-trace t)
      (ring.util.response/not-found "404 Not Found"))))

(defroutes image-routes
  (GET ["/images/:id:extension" :id #"[0-9]+" :extension #"\.[a-zA-Z]+"]
       [id extension]
       (api-get-image id extension))
  (GET ["/thumbnails/:id.png"   :id #"[0-9]+"]
       [id]
       (api-get-thumbnail id))
  (GET ["/image-proxy"]
       [thread-url url thumbnail]
       (api-image-proxy thread-url url (= thumbnail "1")))

  (GET "/api-get-download-status"
       []
       (api-get-download-status))
  (GET "/api-stop-current-downloads"
       []
       (api-stop-current-downloads))
  (GET "/api-configure-image-downloading"
       [enable]
       (api-configure-image-downloading enable))

  (GET "/api-add-ng-image"
       [url md5-string]
       (api-add-ng-image url md5-string)))