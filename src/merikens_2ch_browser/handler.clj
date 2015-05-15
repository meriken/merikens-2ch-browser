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
; along with Foobar.  If not, see <http://www.gnu.org/licenses/>.



(ns merikens-2ch-browser.handler
  (:use [ring.middleware file-info file])
  (:require [compojure.core :refer [defroutes]]
            [compojure.route :as route]
            ; [ring.middleware.session.cookie]
            [noir.response :refer [redirect]]
            [noir.util.middleware :refer [app-handler]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.rotor :as rotor]
            ; [selmer.parser :as parser]
            [environ.core :refer [env]]
            [com.climate.claypoole]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.middleware :refer [load-middleware]]
            [merikens-2ch-browser.db.schema :as schema]
            [merikens-2ch-browser.db.core :as db]
            [merikens-2ch-browser.routes.home :refer [home-routes]]
            [merikens-2ch-browser.routes.image :refer [image-routes start-download-manager]]
            [merikens-2ch-browser.routes.post :refer [post-routes]]
            [merikens-2ch-browser.routes.thread-list :refer [thread-list-routes]]
            [merikens-2ch-browser.routes.favorite-board :refer [favorite-board-routes]]
            [merikens-2ch-browser.routes.auth :refer [auth-routes]]
            [merikens-2ch-browser.routes.settings :refer [settings-routes]]
            [merikens-2ch-browser.routes.pc.thread-content :refer [thread-content-routes]]
            [merikens-2ch-browser.routes.mobile.home :refer [mobile-home-routes]]
            [merikens-2ch-browser.routes.mobile.auth :refer [mobile-auth-routes]]
            [merikens-2ch-browser.routes.mobile.board :refer [mobile-board-routes]]
            [merikens-2ch-browser.routes.mobile.thread :refer [mobile-thread-routes]]
            [merikens-2ch-browser.routes.mobile.special-thread-list :refer [mobile-special-thread-list-routes]]
            [merikens-2ch-browser.routes.mobile.post :refer [mobile-post-routes]]))



(defroutes
  app-routes
  (route/resources "/")
  (route/not-found "指定されたページは見つかりませんでした。"))

(def app
  (app-handler
    [home-routes
     thread-content-routes
     image-routes
     post-routes
     thread-list-routes
     favorite-board-routes
     auth-routes
     settings-routes

     mobile-home-routes
     mobile-auth-routes
     mobile-board-routes
     mobile-thread-routes
     mobile-special-thread-list-routes
     mobile-post-routes

     app-routes]
    :middleware
    (load-middleware)
    :session-options
    {:timeout nil,
     :timeout-response (redirect "/")
     ; :cookie-name "merikens-2ch-browser-session"
     :store (ring.middleware.session.memory/memory-store)}
    :access-rules
    []
    :formats
    [:json-kw]
    ; [:json-kw :edn]
    ))
