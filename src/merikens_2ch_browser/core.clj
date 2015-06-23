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


(ns merikens-2ch-browser.core
  (:use [ring.middleware file-info file])
  (:require [org.httpkit.server :as http-kit]
            [immutant.web :as immutant]
            [immutant.web.undertow :refer [options]]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes]]
            [noir.response :refer [redirect]]
            [taoensso.timbre :refer [log]]
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
    (log :info "Shutting down database...")
    (Thread/sleep 5000)
    (db/shutdown)
    (log :info "Database was shut down successfully."))
  (log :info (str app-name " was shut down successfully.")))

(defn stop-app []
  (log :info (str "Shutting down " app-name "..."))
  (stop-web-server)
  (destroy))

(defn load-config-file-if-necessary
  []
  (when (not @*config-file-loaded?*)
    (configure-timbre)
    (log :info (str "Starting " app-name "..."))
    (try
      (log :info "Loading configuration file...")
      (load-file config-file-path)
      (catch Throwable t
        (log :info "Failed to load configuration file:" t)))
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
      (log :info "Initializing the database...")
      (log :info "Tables are being created...")
      (schema/create-tables schema/db-spec)))
  (log :info "Tables are being upgraded...")
  (db/upgrade-tables schema/db-spec)

  ; Set default system settings.
  (if (nil? (db/get-system-setting "use-image-proxy"))
    (db/update-system-setting "use-image-proxy" "true"))
  (if (nil? (db/get-system-setting "automatic-updates-for-favorite-board-list"))
    (db/update-system-setting "automatic-updates-for-favorite-board-list" "true"))
  (if (nil? (db/get-system-setting "automatic-updates-for-special-menu-checkbox"))
    (db/update-system-setting "automatic-updates-for-special-menu-checkbox" "true"))
  ; automatic-updates-for-favorite-board-list

  (start-download-manager)

  (load-default-image-ng-filters)

  (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (stop-app))))

  (log :info app-name "started successfully."))

(defn start-web-server
  [& [port]]
  (cond
    (= @server-software :jetty)
    (do
      (log :info "Starting web server with Jetty...")
      (reset! server (jetty/run-jetty handler/app {:port (if port (Integer. port) default-port) :max-threads number-of-threads-for-web-server :join? false}))
      (log :info "Web server stated successfully."))

    (= @server-software :immutant)
    (do
      (log :info "Starting web server with Immutant 2...")
      (reset! server (immutant/run handler/app (options :port (if port (Integer. port) default-port) :io-threads immutant-io-threads :worker-threads immutant-worker-threads :host "0.0.0.0")))
      (log :info "Web server stated successfully."))

    (= @server-software :http-kit)
    (do
      (log :info "Starting web server with HTTP Kit...")
      (reset! server (http-kit/run-server handler/app {:port (if port (Integer. port) default-port) :thread number-of-threads-for-web-server}))
      (log :info "Web server stated successfully."))))

(comment defn restart-web-server
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

    (try (Integer. (first args)) (catch Throwable _ nil))
    (start-app (first args))

    (= (first args) "-jetty")
    (do
      (reset! server-software :jetty)
      (start-app (try (Integer. (second args)) (catch Throwable _ default-port))))

    (= (first args) "-http-kit")
    (do
      (reset! server-software :http-kit)
      (start-app (try (Integer. (second args)) (catch Throwable _ default-port))))

    (= (first args) "-immutant")
    (do
      (reset! server-software :immutant)
      (start-app (try (Integer. (second args)) (catch Throwable _ default-port))))

    (= (first args) "-back-up-database")
    (do
      (db-backup/create-backup)
      (System/exit 0))

    (= (first args) "-back-up-database-without-images")
    (do
      (db-backup/create-backup :without-images)
      (System/exit 0))

    (and (re-find #"^-convert-([a-z0-9]+)-database-to-([a-z0-9]+)-database$" (first args)) (= (count args) 1))
    (do
      (let [result (re-find #"^-convert-([a-z0-9]+)-database-to-([a-z0-9]+)-database$" (first args))
            src    (nth result 1)
            dest   (nth result 2)]
        (db-backup/convert-database src dest))
      (System/exit 0))

    (and (re-find #"^-convert-([a-z0-9]+)-database-to-([a-z0-9]+)-database-without-images$" (first args)) (= (count args) 1))
    (do
      (let [result (re-find #"^-convert-([a-z0-9]+)-database-to-([a-z0-9]+)-database-without-images$" (first args))
            src    (nth result 1)
            dest   (nth result 2)]
        (db-backup/convert-database src dest :without-images))
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
      (log :info "Invalid command line argument.")
      (System/exit 0))))