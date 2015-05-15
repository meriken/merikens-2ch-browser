(ns merikens-2ch-browser.auth
  (require [noir.session :as session]
           [noir.request :as request]
           [ring.util.response :as response]
           [taoensso.timbre :as timbre]
           [merikens-2ch-browser.param :refer :all]
           [merikens-2ch-browser.util :refer :all]))



(defn check-login []
  (let [user   (session/get :user)
        result (if user
                 (= (session/get :login-string)
                    (response/get-header request/*request* "user-agent"))
                 false)]
    ; (timbre/debug "check-login:" result user (= (session/get :login-string)
    ;                                             (response/get-header request/*request* "user-agent")))
    (if (not result) (session/clear!))
    result))

(defn check-admin-login []
  (let [result (and (check-login)
                    (= (:id (session/get :user)) 1))]
    ; (timbre/debug "admin-check-login:" result)
    ; (if (not result) (session/clear!))
    result))
