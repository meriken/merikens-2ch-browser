(ns merikens-2ch-browser.core
  (:use [ring.middleware file-info file])
  (:require [org.httpkit.server :as http-kit]
            [immutant.web :as immutant]
            [immutant.web.undertow :refer (options)]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes]]
            [compojure.route :as route]
            [noir.response :refer [redirect]]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [com.climate.claypoole]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.db.schema :as schema]
            [merikens-2ch-browser.handler :as handler]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.db.backup :as db-backup]
            [merikens-2ch-browser.routes.image :refer [start-download-manager load-default-image-ng-filters update-thumbnails]]
            [merikens-2ch-browser.import :refer [import-rep2-data]])
  (:gen-class))



(defonce ^:dynamic *config-file-loaded?* (atom false))



(defn destroy
  "destroy will be called when your application
   shuts down. Put any clean up code here."
  []
  (when (or (= (:subprotocol schema/db-spec) "h2")
            (= (:subprotocol schema/db-spec) "hsqldb"))
    (timbre/info "Shutting down database...")
    (Thread/sleep 5000)
    (db/shutdown)
    (timbre/info "Database was shut down successfully."))
  (timbre/info (str app-name " was shut down successfully.")))

(defn stop-app []
  (timbre/info (str "Shutting down " app-name "..."))
  (stop-web-server)
  (destroy))

(defn load-config-file-if-necessary
  []
  (when (not @*config-file-loaded?*)
    (configure-timbre)
    (timbre/info (str "Starting " app-name "..."))
    (try
      (timbre/info "Loading configuration file...")
      (load-file config-file-path)
      (catch Throwable t
        (timbre/info "Failed to load configuration file:" t)))
    (reset! *config-file-loaded?* (atom true))))

(defn init
  "init will be called once when
   app is deployed as a servlet on
   an app server such as Tomcat.
   Put any initialization code here."
  []

  (load-config-file-if-necessary)

  ; Initialize the database if needed
  (if-not (schema/initialized?)
    (do
      (timbre/info "Initializing the database...")
      (timbre/info "Tables are being created...")
      (schema/create-tables schema/db-spec)))
  (timbre/info "Tables are being upgraded...")
  (db/upgrade-tables schema/db-spec)

  ; Set default system settings.
  (if (nil? (db/get-system-setting "use-image-proxy"))
    (db/update-system-setting "use-image-proxy" "true"))

  (start-download-manager)

  (load-default-image-ng-filters)

  (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop-app))))

  (timbre/info app-name "started successfully."))

(defn start-web-server
  [& [port]]
  (cond
    (= @server-software :jetty)
    (do
      (timbre/info "Starting web server with Jetty...")
      (reset! server (jetty/run-jetty handler/app {:port (if port (Integer. port) default-port) :max-threads number-of-threads-for-web-server :join? false}))
      (timbre/info "Web server stated successfully."))

    (= @server-software :immutant)
    (do
      (timbre/info "Starting web server with Immutant 2...")
      (reset! server (immutant/run handler/app (options :port (if port (Integer. port) default-port) :io-threads immutant-io-threads :worker-threads immutant-worker-threads :host "0.0.0.0")))
      (timbre/info "Web server stated successfully."))

    (= @server-software :http-kit)
    (do
      (timbre/info "Starting web server with HTTP Kit...")
      (reset! server (http-kit/run-server handler/app {:port (if port (Integer. port) default-port) :thread number-of-threads-for-web-server}))
      (timbre/info "Web server stated successfully."))))

(defn restart-web-server
  []
  (stop-web-server true)
  ; TODO: Use the same port.
  (start-web-server))

(defn start-app [& [port]]
  (init)
  (start-web-server port))

(defn -main [& args]
  (load-config-file-if-necessary)

  (cond
    (nil? args)
    (start-app default-port)

    (try (Integer. (first args)) (catch Throwable t nil))
    (start-app (first args))

    (= (first args) "-jetty")
    (do
      (reset! server-software :jetty)
      (start-app (try (Integer. (second args)) (catch Throwable t default-port))))

    (= (first args) "-http-kit")
    (do
      (reset! server-software :http-kit)
      (start-app (try (Integer. (second args)) (catch Throwable t default-port))))

    (= (first args) "-immutant")
    (do
      (reset! server-software :immutant)
      (start-app (try (Integer. (second args)) (catch Throwable t default-port))))

    (= (first args) "-back-up-database")
    (do
      (db-backup/create-backup)
      (System/exit 0))

    (= (first args) "-back-up-database-without-images")
    (do
      (db-backup/create-backup :without-images)
      (System/exit 0))

    (and (= (first args) "-convert-h2-database-to-hypersql-database") (= (count args) 1))
    (do
      (db-backup/convert-h2-database-to-hypersql-database)
      (System/exit 0))

    (and (= (first args) "-convert-hypersql-database-to-h2-database") (= (count args) 1))
    (do
      (db-backup/convert-hypersql-database-to-h2-database)
      (System/exit 0))

    (and (= (first args) "-convert-mysql-database-to-hypersql-database") (= (count args) 1))
    (do
      (db-backup/convert-mysql-database-to-hypersql-database)
      (System/exit 0))

    (and (= (first args) "-convert-hypersql-database-to-mysql-database") (= (count args) 1))
    (do
      (db-backup/convert-hypersql-database-to-mysql-database)
      (System/exit 0))

    (and (= (first args) "-import-rep2-data") (= (count args) 3))
    (do
      (import-rep2-data (nth args 1) (nth args 2))
      (System/exit 0))

    (and (= (first args) "-update-thumbnails") (= (count args) 1))
    (do
      (update-thumbnails)
      (System/exit 0))

    :else
    (do
      (configure-timbre)
      (timbre/info "Invalid command line argument.")
      (System/exit 0))))