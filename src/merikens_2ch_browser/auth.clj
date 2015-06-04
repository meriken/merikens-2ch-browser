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



(ns merikens-2ch-browser.auth
  (require [noir.session :as session]
           [noir.request :as request]
           [ring.util.response :as response]
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
