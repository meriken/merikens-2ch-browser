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



(ns merikens-2ch-browser.routes.mobile.thread
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [clojure.data.json :as json]
            [ring.handler.dump]
            [ring.util.response :as response]
            [ring.util.codec :refer [url-encode url-decode]]
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
            [taoensso.timbre :as timbre :refer [log]]
            [clj-http.client :as client]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.layout :as layout]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.routes.thread-content :refer [get-posts-from-database
                                                                get-posts-in-json
                                                                get-posts-through-read-cgi
                                                                get-posts-in-archived-dat-file
                                                                get-posts-in-current-dat-file]]
            [merikens-2ch-browser.routes.image :refer [set-up-download ng-image? ng-image-url?]]
            [merikens-2ch-browser.db.core :as db]
            [com.climate.claypoole :as cp]
            [clojure.data.codec.base64 :as base64]))



(def create-thumbnails true)
(def embed-thumbnails false)

(defn to-real-url
  [url]
  (-> url
    (clojure.string/replace #"^[htp]+" "http")
    (clojure.string/replace #" +" "")
    (clojure.string/replace #"●" ".")))

(defn create-img-tag-for-thumbnail
  [url context]
  ; (timbre/debug "create-img-tag-for-thumbnail:" url)
  (let [real-url                     (to-real-url url)
        thumbnail-element-id         (random-element-id)
        thumbnail-wrapper-element-id (random-element-id)
        failed-download              (db/get-failed-download (:id (session/get :user)) real-url)
        ; _ (timbre/info "    failed-download:" failed-download)
        image      (cond
                     ; failed-download
                     ; nil
                     embed-thumbnails
                     (db/get-image-with-url-without-content real-url)
                     :else
                     (db/get-image-with-url-without-content-and-thumbnail real-url))
        ; _ (timbre/info "    image:" image)
        ng?        (or (ng-image? image) (ng-image-url? (:id (session/get :user)) real-url))
        thumbnail  (cond
                     ng?             image-thumbnail-ng-src
                     image           (if embed-thumbnails
                                       (str "data:image/png;base64," (String. (base64/encode (:thumbnail image)) "ASCII"))
                                       (str "/thumbnails/" (:id image) ".png"))

                     failed-download image-thumbnail-download-failed-src
                     :else           image-thumbnail-spinner-src)
        real-url-with-proxy (str "/image-proxy?thread-url=" (to-url-encoded-string (:thread-url context)) "&url=" (to-url-encoded-string real-url))
        src        (cond
                     image (str "/images/" (:id image) (:extension image))
                     (:use-image-proxy context) real-url-with-proxy
                     :else real-url)
        left-click   (str "if (" (if (not failed-download) "true" "false")
                          "    && $('#" thumbnail-element-id "').attr('src') != imageThumbnailNGSource"
                          "    && $('#" thumbnail-element-id "').attr('src') != imageThumbnailFailedSource"
                          "    && $('#" thumbnail-element-id "').attr('src') != imageThumbnailDownloadFailedSource"
                          "    && document.getElementById('" thumbnail-element-id "').complete"
                          "    && document.getElementById('" thumbnail-element-id "').naturalWidth) {"
                          "    openImageViewer('" src "', " (:res-no context) ");"
                          "} else if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailFailedSource) {"
                          "    loadThumbnail('" thumbnail-element-id "', '" src "', false);"
                          "} else if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailDownloadFailedSource) {"
                          "    openURLInNewWindowWithReferrerDisabled('" real-url "');"
                          "} else if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailNGSource) {"
                          "} else {"
                          (if (:use-image-proxy context)
                            (str "window.open('" src "');")
                            (str "openURLInNewWindowWithReferrerDisabled('" src "');"))
                          "}")
        right-click  (str "displayImageMenu("
                          "event,"
                          "'" src "',"
                          "'" real-url "',"
                          "'" thumbnail-element-id "',"
                          (if image
                            (str "'" (:md5-string (db/get-image-extra-info (:id image))) "',")
                            "null,")
                          (:res-no context)
                          ");")
        middle-click (cond
                       (:use-image-proxy context)
                       (str "if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailFailedSource) {"
                            "    openURLInNewWindowWithReferrerDisabled('" real-url "');"
                            "} else if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailDownloadFailedSource) {"
                            "    openURLInNewWindowWithReferrerDisabled('" real-url "');"
                            "} else if ($('#" thumbnail-element-id "').attr('src') != imageThumbnailNGSource) {"
                            "    window.open('" src "');"
                            "}")

                       :else
                       (str "if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailFailedSource) {"
                            "    openURLInNewWindowWithReferrerDisabled('" real-url "');"
                            "} else if ($('#" thumbnail-element-id "').attr('src') == imageThumbnailDownloadFailedSource) {"
                            "    openURLInNewWindowWithReferrerDisabled('" real-url "');"
                            "} else if ($('#" thumbnail-element-id "').attr('src') != imageThumbnailNGSource) {"
                            "    openURLInNewWindowWithReferrerDisabled('" src "');"
                            "}"))]

    ; (timbre/info "    (:download-images context):" (:download-images context))
    (if (and (not image) (not failed-download) (:download-images context))
      (set-up-download real-url (:thread-url context)))
    (html
      (str "<!-- image-url: " url " -->")
      [:div {:id    thumbnail-wrapper-element-id
             :class (str "thumbnail-wrapper " thumbnail-wrapper-element-id)
             :style (str "width: " thumbnail-width "px; height: " thumbnail-height "px; " (if image "border: 2px black solid;" ""))}
       [:img {:id     thumbnail-element-id
              :class  (str "thumbnail " thumbnail-element-id)
              :style (str "width: " thumbnail-width "px; height: " thumbnail-height "px; ")
              :rel    "noreferrer"
              ; :width  thumbnail-width
              ; :height thumbnail-height
              :src    thumbnail}]]

      (set-click-event-handler (str "." thumbnail-element-id)
                               (str "(arguments[0] || window.event).stopPropagation();" left-click)
                               right-click
                               (str "(arguments[0] || window.event).stopPropagation();" middle-click))

      (set-click-event-handler (str "." thumbnail-wrapper-element-id)
                               left-click
                               right-click
                               middle-click)
      ; addImage()
      (if (not ng?)
        [:script
         "addImage("
         "'" src "',"
         "'" (:service context) "',"
         "'" (:board context) "',"
         "'" (:thread-no context) "',"
         (:res-no context) ","
         (if image "true" "false") ","
         (if (and (not image) (not failed-download))
           (str "'" thumbnail-element-id "',")
           "null,")
         "'" real-url "',"
         (if image
           (str "'" (:md5-string (db/get-image-extra-info (:id image))) "'")
           "null")
         ");"])

      ; addThumbnail()
      (if (and (not ng?) (not image) (not failed-download))
        [:script "addThumbnail('" thumbnail-element-id "', '" (cond
                                                                image (str "/thumbnails/" (:id image) ".png")
                                                                (:use-image-proxy context) (str real-url-with-proxy "&thumbnail=1")
                                                                :else real-url) "');"])
      )))

(defn process-external-link
  [url context]
  (let [real-url (to-real-url url)
        element-id (random-element-id)]
    (str "<a id='" element-id "' class='" element-id "'>"
         (cond
           (re-find #"^https?://www.youtube.com/watch\?v=([^&]+)" url)
           (str "<img class=youtube-thumbnail src='http://img.youtube.com/vi/"
                (second (re-find #"^https?://www.youtube.com/watch\?v=([^&#]+)" url))
                "/0.jpg' width=" youtube-thumbnail-width " height=" youtube-thumbnail-height ">")

           (re-find #"^https?://youtu.be/" url)
           (str "<img class=youtube-thumbnail src='http://img.youtube.com/vi/"
                (second (re-find #"^https?://youtu.be/([^?]+)" url))
                "/0.jpg' width=" youtube-thumbnail-width " height=" youtube-thumbnail-height ">"))
         (escape-html url)
         "</a>"
         (html (set-mousedown-event-handler (str "." element-id)
                                            (str "openURLInNewWindowWithReferrerDisabled('" real-url "');")
                                            nil)))))

(defn process-succesive-image-links-in-plain-text
  [s context]
  ; (timbre/debug "process-succesive-image-links-in-plain-text:" s)
  (let [s (clojure.string/replace s #"[ \t\n]+" "\n")]
    (if create-thumbnails
      (-> s
        (clojure.string/replace
          #"(?m)^[ \t]*([htp]+s?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()]*\.(jpg|JPG|jpeg|JPEG|gif|GIF|png|PNG|bmp|BMP))[ \t]*$"
          #(create-img-tag-for-thumbnail (second %1) context))
        (clojure.string/replace #"\n" "<!-- br -->"))
      (-> s
        (clojure.string/replace
          #"(?m)^[ \t]*([htp]+s?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()]*\.(jpg|JPG|jpeg|JPEG|gif|GIF|png|PNG|bmp|BMP))[ \t]*$"
          #(process-external-link (second %1) context))))))

(defn process-url-in-plain-text
  [url context]
  ; (timbre/debug "process-url-in-text:" url)
  (cond
    ; internal links
    (split-thread-url (to-real-url url))
    (let [element-id (random-element-id)
          thread-url (to-real-url url)
          {:keys [service server board thread-no]} (split-thread-url thread-url)]
      (str "<a id='" element-id "' class='" element-id "' href='./mobile-thread?thread-url=" thread-url "'>" url "</a>"))

    ; external images
    (re-find #"(?i)\.(jpg|JPG|jpeg|JPEG|gif|GIF|png|PNG|bmp|BMP)$" url)
    (if create-thumbnails
      (create-img-tag-for-thumbnail url context)
      (process-external-link url context))

    ; external links
    :else
    (process-external-link url context)))

(defn process-anchor-in-plain-text
  [anchor context]
  ; (timbre/debug "process-anchor:" anchor context)
  (try
    (cond
      (re-find #"^(>>?|＞＞?|≫)((([0-9]+)(-[0-9]+)?,)*([0-9]+)(-[0-9]+)?)$" anchor)
      (let [parts (re-find #"^(>>?|＞＞?|≫)((([0-9]+)(-[0-9]+)?,)*([0-9]+)(-[0-9]+)?)$" anchor)
            anchor-res-no (nth parts 2)
            element-id (random-element-id)]
        (str "<span id=" element-id " class=anchor onmouseover=\"displayFloatingPost(event, '" anchor-res-no "', " (:res-count context) ");\">" anchor "</span>"
             "<script>"
             ; "addReverseAnchors(" (:res-no context) ", '" anchor-res-no "', " (:res-count context) ");"
             "</script>"))

      :else
      anchor)
    (catch Throwable t
      (timbre/debug "process-anchor: Exception caught:" (str t))
      anchor)))

(defn process-id-in-plain-text
  [id context]
  ; (timbre/debug "process-anchor:" anchor context)
  (try
    (let [converted-id (-> id
                         (clojure.string/replace #"^ID:" "id-")
                         (clojure.string/replace #"[+.]" "-")
                         (clojure.string/replace #"/"    "_"))]
      (str "<span class=\"id-in-text id-in-text-" converted-id "\" onmouseover=\"displayFloatingPostWithID(event, '" converted-id "')\">" id "<span class=" converted-id "-count></span></span>"))
    (catch Throwable t
      (timbre/debug "process-anchor: Exception caught:" (str t))
      id)))

(defn add-highlights-to-plain-text
  [s context]
  (str "<span class='highlighted'>"
       (escape-html s)
       "</span>"))

(def regex-match-processors
  [; Process URLs.
   {:pattern   #"sssp://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    :processor #(str (do %2 nil) "<img src=\"" (clojure.string/replace %1 #"^sssp" "http") "\">")}
   {:pattern   #"(?m)^([ \t]*[htp]+s?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()]*\.(jpg|JPG|jpeg|JPEG|gif|GIF|png|PNG|bmp|BMP)[ \t\n]+)*[htp]+s?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()]*\.(jpg|JPG|jpeg|JPEG|gif|GIF|png|PNG|bmp|BMP)[ \t]*$"
    :processor process-succesive-image-links-in-plain-text}
   {:pattern   #"([htp]+s?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;*()●]*[-a-zA-Z0-9+&@#/%=~_|]"
    :processor process-url-in-plain-text}

   ; Misc.
   ;{:pattern   #"(>>?|＞＞?|≫)(([0-9]{1,4})(-[0-9]{1,4})?,)*([0-9]{1,4})(-[0-9]{1,4})?"
   ; :processor process-anchor-in-plain-text}
   ;{:pattern   #"ID:[a-zA-Z0-9+/.]{8,}+"
   ; :processor process-id-in-plain-text}
   {:pattern   #"<hr><var .*</var>$" ; tasukeruyo (2ch.sc)
    :processor #(str (do %2 nil) %1)} ; TODO: escape the string
   {:pattern   #"<hr><font color=\"blue\">.*</font>[ ]*$" ; tasukeruyo (2ch.net)
    :processor #(str (do %2 nil) %1)} ; TODO: escape the string
   {:pattern   :regex-search-pattern
    :processor add-highlights-to-plain-text}
   ])

(defn process-regex-matches-in-plain-text
  [s context depth]
  (if (>= depth (count regex-match-processors))
    ; (do
    ;   ; (timbre/debug (str "\"" s "\""))
    ;   (try
    ;     (.text (Jsoup/parse s))
    ;     (catch Throwable t
    ;       (escape-html s))))
    (escape-html s)
    (let [pattern   (if (= (:pattern (nth regex-match-processors depth)) :regex-search-pattern)
                      (:regex-search-pattern context)
                      (:pattern (nth regex-match-processors depth)))
          processor (:processor (nth regex-match-processors depth))
          matcher   (if (nil? pattern) nil (re-matcher pattern s))]
      (if (or (nil? pattern)
              (not (.find matcher)))
        (process-regex-matches-in-plain-text s context (inc depth))
        (str
          (process-regex-matches-in-plain-text (subs s 0 (.start matcher)) context (inc depth))
          (processor (subs s (.start matcher) (.end matcher)) context)
          (process-regex-matches-in-plain-text (subs s (.end matcher) (count s)) context depth))))))

(defn convert-message-in-post-into-html
  [text context]
  ; (timbre/debug text)
  (-> text
    (process-regex-matches-in-plain-text context 0)
    (clojure.string/replace "\n" "<br>")
    ))

(defn convert-raw-post-to-html
  [index handle-or-cap tripcode cap email etc message special-color context]
  (let [{:keys [service server board thread]} context
        bookmark      (:bookmark context)
        res-count     (:res-count context)
        hidden?       (or (and (:regex-search-pattern context)
                               (not (re-find (:regex-search-pattern context) message)))
                          (not ((:display-post-fn context) index)))
        id            (re-find #"ID:[a-zA-Z0-9/+.]{8,}" etc)
        converted-id  (and id
                           (-> id
                             (clojure.string/replace #"^ID:" "id-")
                             (clojure.string/replace #"[+.]" "-")
                             (clojure.string/replace #"/"   "_")))
        post-signature (str service "," board "," thread "," index "," etc)
        message        (-> message  ; for pattern matching
                         (clojure.string/replace #"^ *" "")
                         (clojure.string/replace #" *$" ""))
        aborn?         (pos? (count (remove not (map #(and (:invisible %1)
                                                           (or (= 0 (count (:board %1))) (= board (:board %1)))
                                                           (or (= 0 (count (:thread-title %1)))
                                                               (try
                                                                 (re-find (re-pattern (:thread-title %1)) (:thread-title context))
                                                                 (catch Throwable t false)))
                                                           (or (and (= (:filter-type %1) "post") (= (:pattern %1) post-signature))
                                                               (and (= (:filter-type %1) "id") (= (:pattern %1) id))
                                                               (and (= (:filter-type %1) "message")
                                                                    (try
                                                                      (re-find (re-pattern (:pattern %1)) message)
                                                                      (catch Throwable t false)))))
                                                     (:post-filters context)))))
        new? (not (and bookmark
                       (not (= bookmark 0))
                       (<= index bookmark)))]
    ; (if (or (and (not (sc-url? (:thread-url context))) (re-find #"Over [0-9]+ Thread" etc))
    ;         (and      (sc-url? (:thread-url context))  (re-find #"Over [0-9]+ Comments" etc)))
    ;   (db/mark-thread-as-archived service server board thread))
    (if (>= index (:res-count context))
      (db/update-thread-res-count service server board thread index))
    (if (or aborn? hidden?)
      ""
      (html
        [:div
         {:id    (str "res-" index "-heading")
          :class (str "thread-content-post-heading"
                      (if aborn? " aborn" "")
                      (if hidden? " hidden" "")
                      (if id      (str " " converted-id) "")
                      (if new?    " new" ""))}
         (str "<!-- index: " index " -->")
         (if id (str "<!-- id: " id " -->"))
         (str "<!-- post-signature: " post-signature " -->")
         [:span ; {:onmouseover (str "displayPostMenu(event, '" server "', '" service "', '" board "', '" thread "', " index ");")}
          [:span {:class (if new? "post-index new" "post-index")}
           index]
          "："]
         [:span ; {:style (if special-color (str "color: #" special-color ";") "color: green;")}
          (escape-html handle-or-cap)
          (if (and (and handle-or-cap (> (count handle-or-cap) 0))
                   (or (and tripcode (> (count tripcode) 0))
                       (and cap (> (count cap) 0))))
            " ")
          [:span.tripcode (escape-html tripcode)]
          (if (and (and tripcode (> (count tripcode) 0))
                   (and cap (> (count cap) 0)))
            " ")
          (escape-html cap)]
         "："
         (escape-html email)
         "："
         (-> etc
           (clojure.string/replace #"ID:[a-zA-Z0-9/+.]{8,9}([a-zA-Z0-9/+.]?)" "ID:*$1")
           (clojure.string/replace
             #"[0-9][0-9][0-9][0-9]/0?(1?[0-9]/[0-9][0-9])\(.\) ([0-9][0-9]:[0-9][0-9]):[0-9][0-9](\.[0-9][0-9])?"
             "$1 $2")
           (escape-html)
           (clojure.string/replace
             #"ID:[a-zA-Z0-9/+.]{8,}"
             #(str "<span class=\"id-in-heading id-in-heading-" converted-id "\" onmouseover=\"displayFloatingPostWithID(event, '" converted-id "')\">"
                   %
                   "<span class=\"id-count " converted-id "-count\"></span></span>"))
           )]
        [:div
         {:id    (str "res-" index "-text")
          :class (str "thread-content-post-text"
                      (if aborn? " aborn" "")
                      (if hidden? " hidden" "")
                      (if id      (str " " converted-id) "")
                      (if new?    " new" ""))}
         (convert-message-in-post-into-html message (assoc context :res-no index))]))))



(defn jump-to-end-of-thread
  []
  [:script
   "try {"
   "    $.mobile.defaultHomeScroll = $('#thread-content-wrapper').height();"
   "    function jumpToPost() { $.mobile.silentScroll($('#thread-content-wrapper').height()); }"
   "} catch (e) {}"])

(defn do-not-jump
  []
  [:script "function jumpToPost() {}"])

(defn update-bookmark-without-jumping
  [context]
  (let [{:keys [service server board thread bookmark]} context
        {:keys [res-count archived]} (db/get-thread-info service board thread)
        new-bookmark (min res-count (+ (:start context) (:max-count context) -1))
        new-bookmark (or (and bookmark (max bookmark new-bookmark)) new-bookmark)]
    (timbre/debug "new-bookmark:" new-bookmark)
    (db/update-bookmark service board thread new-bookmark)
    [:script "function jumpToPost() {}"]))

(defn update-bookmark-and-jump-to-first-new-post
  [context]
  ; (timbre/debug "update-bookmark-and-jump-to-first-new-post:" context)
  (let [{:keys [service server board thread bookmark]} context
        {:keys [res-count archived]} (db/get-thread-info service board thread)
        new-bookmark (min res-count (+ (:start context) (:max-count context) -1))
        new-bookmark (or (and bookmark (max bookmark new-bookmark)) new-bookmark)]
    (db/update-bookmark service board thread new-bookmark)
    ; (timbre/debug bookmark res-count)
    (cond
      ; There are new posts.
      (and bookmark (> bookmark 0) (> res-count bookmark))
      [:script
       ; "function jumpToPost() {"
       ; "try {"
       ; "    $.mobile.defaultHomeScroll = $('#res-' + '" (inc bookmark) "' + '-heading').offset().top - $('#res-' + '" (:start context) "' + '-heading').offset().top;"
       ; "    $.mobile.silentScroll($('#res-' + '" (inc bookmark) "' + '-heading').offset().top - $('#res-' + '" (:start context) "' + '-heading').offset().top);"
       ; "} catch (e) {}"
       ; "}"

       "function jumpToPost() {\n"
       "try {"
       "    var firstPostIndex = " (:start context) " - 1;\n"
       "    var firstPostSelector;\n"
       "    do {\n"
       "        ++firstPostIndex;\n"
       "        firstPostSelector = '#' + createElementIdForPost('" service "', '" board "', '" thread "', firstPostIndex.toString(), 'heading');\n"
       "    } while ($(firstPostSelector).length > 0 && ($(firstPostSelector).hasClass('aborn') || $(firstPostSelector).hasClass('hidden')));\n"

       "    var firstNewPostIndex = " bookmark ";\n"
       "    var firstNewPostSelector;\n"
       "    do {\n"
       "        ++firstNewPostIndex;\n"
       "        firstNewPostSelector = '#' + createElementIdForPost('" service "', '" board "', '" thread "', firstNewPostIndex.toString(), 'heading');\n"
       "    } while ($(firstNewPostSelector).length > 0 && ($(firstNewPostSelector).hasClass('aborn') || $(firstNewPostSelector).hasClass('hidden')));\n"

       "    if ($(firstPostSelector).length > 0 && $(firstNewPostSelector).length > 0) {\n"
       "        $.mobile.defaultHomeScroll = $(firstNewPostSelector).offset().top - $(firstPostSelector).offset().top;"
       "        $.mobile.silentScroll($(firstNewPostSelector).offset().top - $(firstPostSelector).offset().top);"
       "    }\n"
       "} catch (e) {}\n"
       "}\n"]

      (or (:start-specified? context)
          (nil? bookmark)
          (= bookmark 0))
      (do-not-jump)

      :else
      (jump-to-end-of-thread))))

(defn get-posts-in-thread
  [context]
  (let [{:keys [service server board thread]} context
        thread-info         (db/get-thread-info service board thread)
        posts-from-database (if (:archived thread-info)
                              (get-posts-from-database (assoc context :archived true)))
        jump?               (and (not (:regex-search-pattern context))
                                 (>= 0 (count (:thread-options context))))]
    (if (and (:archived thread-info) posts-from-database)
      (list
        [:div.message-info
           "このスレッドには書き込めません。"
           "保存されている過去ログを表示します。"]
        posts-from-database
        (update-bookmark-without-jumping context))

      (try
        ; (throw (Exception.))
        (check-dat-file-availability (:thread-url context))
        (list
          (get-posts-in-current-dat-file context)
          (if jump?
            (update-bookmark-and-jump-to-first-new-post context)
            [:script "function jumpToPost() {}"]))
        (catch Throwable t
          (timbre/info "    Failed to download current DAT file:" (str t))
          ; (print-stack-trace t)

          (try
            (check-thread-in-html-availability (:thread-url context))
            (list
              (get-posts-through-read-cgi context)
              (if jump?
                (update-bookmark-and-jump-to-first-new-post context)
                (do-not-jump)))
            (catch Throwable t
              (timbre/info "    Failed to download HTML file:" (str t))
              ; (print-stack-trace t)

              (try
                (check-thread-in-json-availability (:thread-url context))
                (list
                  (get-posts-in-json context)
                  (if jump?
                    (update-bookmark-and-jump-to-first-new-post context)
                    (do-not-jump)))
                (catch Throwable t
                  (timbre/info "    Failed to download thread in JSON:" (str t))
                  ; (print-stack-trace t)

                  (try
                    (list
                      (get-posts-in-archived-dat-file context)
                      (if jump?
                        (update-bookmark-and-jump-to-first-new-post context)
                        (do-not-jump)))
                    (catch Throwable t
                      (timbre/info "    Failed to download archived DAT file:" (str t))
                      ; (print-stack-trace t)

                      (try
                        (let [posts (get-posts-from-database (assoc context :download-failed true))]
                          (if (nil? posts)
                            (throw (Exception.)))
                          (list
                            posts
                            [:div.message-info
                             "スレッドの読み込みに失敗しました。" [:br]
                             "保存されている過去ログを表示します。"]
                            (if jump?
                              (jump-to-end-of-thread)
                              (do-not-jump))))
                        (catch Throwable t
                          ; (timbre/debug "get-thread-content: get-posts-through-read-cgi failed.")
                          ; (timbre/debug (str t))
                          ; (print-stack-trace t)
                          (throw t))))))))))))))

(defn mobile-thread
  [original-thread-url search-text search-type start max-count]
  ; (timbre/debug "api-get-thread-content")
  (let [parts (split-thread-url original-thread-url)
        {:keys [service server original-server current-server board thread-no options]} parts
        thread-url (and parts (create-thread-url server board thread-no))
        bookmark   (db/get-bookmark service board thread-no)
        max-count (if (< 0 (count max-count)) (Integer/parseInt max-count) default-maximum-count-for-mobile-thread)
        start-specified? (< 0 (count start))
        start (cond
                start-specified?              (Integer/parseInt start)
                (and bookmark (> bookmark 0)) (inc (* (int (clojure.math.numeric-tower/floor (/ (dec bookmark) max-count))) max-count))
                :else                         1)]
    (timbre/info "bookmark:" bookmark)
    (timbre/info "start:" start)

    (cond
      (not (check-login))
      (html [:script "open('/mobile-login', '_self');"])

      (nil? parts)
      (html [:div.message-error
             "スレッドの読み込みに失敗しました。" [:br]
             "スレッドのアドレスが不正です。"])

      :else
      (try
        (timbre/info "Preparing thread content...")
        (increment-http-request-count)
        ;  Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
        ; (timbre/debug "api-get-thread-content: options:" options)

        (let [start-time (System/nanoTime)
              active?         (is-thread-active? server board thread-no)
              _               (if active?
                                (db/mark-thread-as-active service server board thread-no)
                                (db/mark-thread-as-archived service server board thread-no))
              post-signature-head (str service "," board "," thread-no ",")
              post-filters (remove nil? (map #(if (and (or (= 0 (count (:board %1))) (= board (:board %1)))
                                                       (or (not (= (:filter-type %1) "post"))
                                                           (re-find (re-pattern (str "^" post-signature-head)) (:pattern %1))))
                                                %1
                                                nil)
                                             (db/get-all-post-filters)))
              context         {:thread-url           thread-url
                               :service              service
                               :server               server
                               :original-server      original-server
                               :current-server       current-server
                               :board                board
                               :thread               thread-no
                               :thread-no            thread-no
                               :thread-options       options
                               :shitaraba?           (shitaraba-url? thread-url)
                               :machi-bbs?           (machi-bbs-url? thread-url)
                               :regex-search-pattern (if (> (count search-text) 0) (re-pattern search-text) nil)
                               :bookmark             (db/get-bookmark service board thread-no)
                               :download-images      (= (db/get-user-setting "download-images") "true")
                               :use-image-proxy      (= (db/get-system-setting "use-image-proxy") "true")
                               :post-filters         post-filters
                               :convert-raw-post-to-html convert-raw-post-to-html
                               :start                start
                               :max-count            max-count
                               :start-specified?     start-specified?
                               :active?              active?}
              posts-in-thread [:div#thread-content-wrapper (get-posts-in-thread context)]
              thread-info     (db/get-thread-info service board thread-no)
              thread-title    (remove-ng-words-from-thread-title (:title thread-info))
              new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
              thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
              start-time-for-response (System/nanoTime)
              select-id       (random-element-id)
              res-count       (:res-count thread-info)
              page-url-base   (str "./mobile-thread"
                                   "?thread-url="  (to-url-encoded-string thread-url)
                                   "&search-text=" (and search-text (to-url-encoded-string search-text))
                                   "&search-type=" (and search-type (to-url-encoded-string search-type))
                                   "&max-count="   (to-url-encoded-string max-count))
              result          (layout/mobile-login-required
                                [:script
                                  "imageList = [];"
                                  "resetThumbnailLists();"]
                                [:div.thread {:data-role "page" :data-dom-cache "false" :data-title thread-title}
                                [:div {:role "main" :class "ui-content" :style "padding: 0;"}
                                  posts-in-thread]
                                 [:div {:data-role "footer" :data-position "fixed" :data-tap-toggle "false"}
                                  [:div {:style "float: left;"}
                                   (if start-specified?
                                     (link-to {:data-role "button"
                                               :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-refresh"
                                               :onclick "window.location.replace($(this).attr('href')); return false;"
                                               :rel "external"}
                                              (str "./mobile-thread?thread-url=" (to-url-encoded-string thread-url))
                                              "新着")
                                     [:button {:class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-refresh"
                                               :onclick "location.reload(true);"}
                                      "新着"])
                                   (link-to {:data-role "button"
                                             :class "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-icon-edit"
                                             ; :onclick "window.location.replace($(this).attr('href')); return false;"
                                             }
                                            ; thread-url thread-title handle email message board-url board-name
                                            (str "./mobile-post"
                                                 "?thread-url="  (to-url-encoded-string thread-url)
                                                 "&thread-title="  (to-url-encoded-string "")
                                                 "&handle="  (to-url-encoded-string "")
                                                 "&email="  (to-url-encoded-string "")
                                                 "&message="  (to-url-encoded-string "")
                                                 "&board-url="  (to-url-encoded-string "")
                                                 "&board-name="  (to-url-encoded-string "")
                                                 )
                                            "書込")]

                                  [:div {:style "float: right;"}
                                   (link-to {:data-role "button"
                                             :data-direction "reverse"
                                             :class (str "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-btn-icon-notext ui-icon-carat-l"
                                                         (if (<= start 1) " ui-state-disabled" ""))
                                             :onclick "window.location.replace($(this).attr('href')); return false;"
                                             :rel "external"}
                                            (str page-url-base "&start=" (to-url-encoded-string (max 1 (- start max-count))))
                                            "")
                                   [:select {:id select-id :name "thread-start" :data-native-menu "false" :data-mini "true" :data-inline "true" :data-iconpos "noicon"}
                                    (for [new-start (range 1 (inc res-count) max-count)]
                                        [:option {:value (str page-url-base "&start=" (to-url-encoded-string new-start))}
                                         (str new-start "-")])]
                                   (link-to {:data-role "button"
                                             :class (str "ui-btn ui-shadow ui-corner-all ui-btn-icon-left ui-btn-icon-notext ui-icon-carat-r"
                                                         (if (< res-count (+ start max-count)) " ui-state-disabled" ""))
                                             :onclick "window.location.replace($(this).attr('href')); return false;"
                                             :rel "external"}
                                            (str page-url-base "&start=" (to-url-encoded-string (+ start max-count)))
                                            "次")]]
                                 [:script
                                  "$('#" select-id "').change(function () {"
                                  "window.location.replace($(this).val());"
                                  "});"
                                  "$( document ).on('pageshow', function( event ) {"
                                  "var myselect = $('#" select-id "');"
                                  "if (myselect.length > 0) {"
                                  "myselect[0].selectedIndex = " (format "%.0f" (clojure.math.numeric-tower/floor (/ (double (dec start)) max-count))) ";"
                                  "myselect.selectmenu('refresh');"
                                  "}"
                                  "loadThumbnails();"
                                  ; "updateIDs();"
                                  ; "createReverseAnchors();"
                                  ; (if (>= 0 (count options))
                                  ;   (str "try {"
                                  ;        "    if ($('#" new-res-count-id "').length > 0 /* && parseInt($('#" new-res-count-id "').html()) > 0 */) {"
                                  ;        "        $('#" new-res-count-id "').html('0');"
                                  ;        "        $('#" new-res-count-id "').removeClass('non-zero new-thread');"
                                  ;        "        $('." thread-title-class "').removeClass('new-thread');"
                                  ;        "        $('#thread-list-table').trigger('update');"
                                  ;        "    }"
                                  ;        "} catch (e) {"
                                  ;        "}"))
                                  "});"]])]
          (timbre/info (str "    Created response (" (format "%.0f" (* (- (System/nanoTime) start-time-for-response) 0.000001)) "ms, " (count result) " characters)."))
          (db/update-time-last-viewed service board thread-no (clj-time.coerce/to-sql-time (clj-time.core/now)))
          (db/update-board-server-if-there-is-no-info service server board)

          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (timbre/info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))

          (if (and (not start-specified?)
                   (> res-count bookmark)
                   (= bookmark (+ start max-count -1)))
            (do
              (timbre/info "    Redirecting to next page...")
              (redirect (str page-url-base "&start=" (to-url-encoded-string (+ start max-count)))))
            result))
        (catch Throwable t
          (timbre/debug "api-get-thread-content: Exception caught:" (str t))
          (print-stack-trace t)
          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (html [:div.message-error
                 "スレッドの読み込みに失敗しました。"
                 (let [bbn-result (bbn-check)]
                   (if (and (or (net-url? thread-url) (bbspink-url? thread-url)) bbn-result)
                     (list [:br] bbn-result)))]))))))

(defroutes mobile-thread-routes
  (GET "/mobile-thread"
       [thread-url search-text search-type start max-count]
       (mobile-thread (trim thread-url) search-text search-type start max-count)))
