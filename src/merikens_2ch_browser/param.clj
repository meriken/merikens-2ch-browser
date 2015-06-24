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



(ns merikens-2ch-browser.param
  (:import java.lang.Thread))



(def app-name "Meriken's 2ch Browser 0.1.26")

(def config-file-path "./merikens-2ch-browser.clj")

(def password-salt-length 128)
(def username-min-length 6)
(def username-max-length 20)
(def email-max-length 128)
(def display-name-min-length 1)
(def display-name-max-length 20)

(def table-sorter-threshold 3000)

(def image-thumbnail-ng-src "/img/thumbnail-ng.png")
(def image-thumbnail-failed-src "/img/thumbnail-failed.png")
(def image-thumbnail-download-failed-src "/img/thumbnail-download-failed.png")
(def image-thumbnail-spinner-src "/img/thumbnail-spinner.gif")

(def youtube-thumbnail-width 85)
(def youtube-thumbnail-height 64)

(def number-of-threads-to-archive 5)



; The following parameters must not be renamed as they are exposed in merikens-2ch-browser.clj.

(def default-port 50000)

(def number-of-threads-for-web-server 512)
(def immutant-io-threads 32)
(def immutant-worker-threads number-of-threads-for-web-server)

(def number-of-threads-for-thread-list 16)
(def number-of-threads-for-thread-content 48)
(def number-of-threads-for-new-posts 8)

(def web-sever-thread-priority Thread/MAX_PRIORITY)

(def user-agent "Monazilla/1.00")

(def wait-time-for-downloading-subject-txt 120000)

(def default-maximum-count-for-mobile-board  50)
(def default-maximum-count-for-mobile-thread 20)

(def thumbnail-width 80)
(def thumbnail-height 80)

(def polling-interval-for-download-manager 100)
(def default-maximum-number-of-image-downloads 16)
(def maxinum-number-of-retries-for-image-downloads 40)

(def threshold-for-updating-new-post-counts 3000)
(def maximum-number-of-image-downloads-for-web-browser 4)
(def animation-duration-for-image-viewer 400)

(def left-panes
  [[:account-menu        :open]
   [:server-info         :open]
   [:favorite-boards     :open]
   [:special-menu        :open]
   [:2ch-net             :closed]
   [:2ch-sc              :closed]
   [:open2ch-net         :closed]
   ; [:machi-bbs          :open]
   [:image-download-info :open]])

(def download-dat-files-from-2ch-net-and-bbspink-com false)
(def download-threads-in-html-from-2ch-net-and-bbspink-com true)
(def download-threads-in-json-from-2ch-net-and-bbspink-com true)

(defn proxy-server
  ; [url method]
  [_ _]
  nil)

(def css-pc nil)

(def css-mobile nil)

(def use-net-api        false)
(def net-api-app-key    "")
(def net-api-hm-key     "")
(def net-api-x-2ch-ua   "")
(def net-api-user-agent "")

(def replacement-patterns-for-message-in-post [])
