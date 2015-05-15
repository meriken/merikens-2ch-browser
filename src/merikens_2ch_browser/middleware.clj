(ns merikens-2ch-browser.middleware
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [noir-exception.core :refer [wrap-internal-error wrap-exceptions]]
            [ring.middleware.params :refer [wrap-params]]
            ; [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.middleware.gzip :refer [wrap-gzip]]))

(defn log-request [handler]
  (fn [req]
    (timbre/debug req)
    (handler req)))

(def development-middleware
  [; log-request
   ])

(def production-middleware
  [; wrap-json-response
   ; #(wrap-json-params % {:keywords? true})
   wrap-exceptions
   #(wrap-internal-error % :log (fn [e] (timbre/error e)))
   wrap-gzip
   ])

(defn load-middleware []
  (concat (when (env :dev) development-middleware)
          production-middleware))
