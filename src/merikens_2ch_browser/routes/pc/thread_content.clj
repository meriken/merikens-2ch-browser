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



(ns merikens-2ch-browser.routes.pc.thread-content
  (:use compojure.core)
  (:require [clojure.string :refer [split trim]]
            [clojure.stacktrace :refer [print-stack-trace]]
            [ring.handler.dump]
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
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.routes.thread-content :refer [get-posts-from-database
                                                                get-posts-through-read-cgi
                                                                get-posts-in-json
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
        real-url-with-proxy (str "/image-proxy?thread-url=" (ring.util.codec/url-encode (:thread-url context)) "&url=" (ring.util.codec/url-encode real-url))
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
                          "    openImageViewer('" src "', "
                          "'" (:service context) "',"
                          "'" (:board context) "',"
                          "'" (:thread-no context) "',"
                          (:res-no context) ");"
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
                          "'" (:service context) "',"
                          "'" (:board context) "',"
                          "'" (:thread-no context) "',"
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
      (if (and (not ng?) (not image) (not failed-download))
        [:script "addThumbnail('" thumbnail-element-id "', '" (cond
                                                                image (str "/thumbnails/" (:id image) ".png")
                                                                (:use-image-proxy context) real-url-with-proxy
                                                                :else real-url) "');"]))))

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
      (str "<a id='" element-id "' class='" element-id "'>" url "</a>"
           (html (set-mousedown-event-handler (str "." element-id)
                                              (str "updateThreadContent(null, '" thread-url "');")
                                              (str "displayThreadMenu(event, '" thread-url "', '" service "', '" server "', '" board "', '" thread-no "');")
                                              (str "updateThreadContent(null, '" thread-url "', '', '', true);")))))

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
          (str "<div id=" element-id " class='anchor-in-message' onmouseover=\"displayFloatingPost(event, '" (:service context) "', '" (:board context) "', '" (:thread-no context) "', '" anchor-res-no "', " (:res-count context) ");\">"
               anchor
               "</div>"
               "<script>"
               "addReverseAnchors(" (:res-no context) ", '" anchor-res-no "', " (:res-count context) ");"
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
      (str "<div class=\"id-in-message-wrapper\" onmouseover=\"displayFloatingPostWithID(event, '" (:service context) "', '" (:board context) "', '" (:thread-no context) "', '" converted-id "')\">"
            "<div class=\"id-in-message-label\">ID</div>"
            "<div class=\"id-in-message id-in-message-" converted-id "\">"
            (clojure.string/replace id #"^ID:" "")
            "</div>"
            "<div class=\"id-in-message-count id-in-message-" converted-id "-count\">"
            "</div>"
            "</div>"))
    (catch Throwable t
      (timbre/debug "process-id-in-plain-text: Exception caught:" (str t))
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
   {:pattern   #"(>>?|＞＞?|≫)(([0-9]{1,4})(-[0-9]{1,4})?,)*([0-9]{1,4})(-[0-9]{1,4})?"
    :processor process-anchor-in-plain-text}
   {:pattern   #"ID:[a-zA-Z0-9+/.]{3,}"
    :processor process-id-in-plain-text}
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
  (let [{:keys [service server board thread thread-url]} context
        bookmark      (:bookmark context)
        res-count     (:res-count context)
        new-post?     (not (and bookmark
                                (not (= bookmark 0))
                                (<= index bookmark)))
        hidden?       (or (and (:regex-search-pattern context)
                               (not (re-find (:regex-search-pattern context) message)))
                          (not ((:display-post-fn context) index)))
        id            (re-find #"ID:[a-zA-Z0-9/+.]{3,}" etc)
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
                                                     (:post-filters context)))))]
    ; (if (or (and (not (sc-url? (:thread-url context))) (re-find #"Over [0-9]+ Thread" etc))
    ;         (and      (sc-url? (:thread-url context))  (re-find #"Over [0-9]+ Comments" etc)))
    ;   (db/mark-thread-as-archived service server board thread))
    (if (>= index (:res-count context))
      (db/update-thread-res-count service server board thread index))
    (if (and (:append-after context)
             (<= index (:append-after context)))
      ""
      (html
        [:div
         {:id    (create-element-id-for-post index "heading" context)
          :class (str "post-heading"
                      (if aborn? " aborn" "")
                      (if hidden? " hidden" "")
                      (if id      (str " " converted-id) ""))
          :post-index index
          :post-id id
          :post-signature post-signature}
         [:span {:onmouseover (str "displayPostMenu(event, '" server "', '" service "', '" board "', '" thread "', " index ");")}
          [:span {:class (if new-post?
                           "new-post-index"
                           "")}
           index]
          "："]
         [:span {:style (if special-color (str "color: #" special-color ";") "color: green;")}
          [:strong (escape-html handle-or-cap)]
          (if (and (and handle-or-cap (> (count handle-or-cap) 0))
                   (or (and tripcode (> (count tripcode) 0))
                       (and cap (> (count cap) 0))))
            " ")
          (escape-html tripcode)
          (if (and (and tripcode (> (count tripcode) 0))
                   (and cap (> (count cap) 0)))
            " ")
          [:strong (escape-html cap)]
          ]
         "："
         (escape-html email)
         "："
         ; (timbre/debug etc)
         ; BE
         ; ex: http://hayabusa3.2ch.net/test/read.cgi/news/1418828981/1 BE:829826275-PLT(12000)
         ; ex: http://anago.2ch.sc/test/read.cgi/software/1408011845/680 BE:48417874-2BP(0)
         (-> etc
           (escape-html)
           (clojure.string/replace
             #" BE:([0-9]+)-([^ ]+)\(([0-9]+)\)"
             #(let [be-url (cond
                             (sc-url? (:thread-url context))
                             (str "http://be.2ch.sc/test/p.php?i=" (nth % 1 ""))

                             (or (net-url? (:thread-url context)) (bbspink-url? (:thread-url context)))
                             (str "http://be.2ch.net/user/" (nth % 1 "")))]
                (str " <div class=\"be-in-heading\" onclick=\"openURLInNewWindowWithReferrerDisabled('" be-url "')\">" (nth % 2 "") "(" (nth % 3 "") ")</div>")))
           (clojure.string/replace
             #"ID:([a-zA-Z0-9/+.]{3,})"
             #(str "<div class=\"id-in-heading-wrapper\" onmouseover=\"displayFloatingPostWithID(event, '" (:service context) "', '" (:board context) "', '" (:thread-no context) "', '" converted-id "')\">"
                   "<div class=\"id-in-heading-label\">ID</div>"
                   "<div class=\"id-in-heading id-in-heading-" converted-id "\">"
                   (nth % 1 nil)
                   "</div>"
                   "<div class=\"id-in-heading-count id-in-heading-" converted-id "-count\">"
                   "</div>"
                   "</div>"))
           )]
        [:div
         {:id    (create-element-id-for-post index "message" context)
          :class (str "post-message"
                      (if aborn? " aborn" "")
                      (if hidden? " hidden" "")
                      (if id      (str " " converted-id) ""))
          :post-service    service
          :post-board      board
          :post-thread-no  thread
          :post-thread-url thread-url
          :post-index      index}
         (convert-message-in-post-into-html message (assoc context :res-no index))]))))



(defn jump-to-end-of-thread
  []
  [:script
   "function jumpToPost() {"
   "    var initialHeight = $('#thread-content-wrapper').height();"
   "    $('#thread-content').scrollTop($('#thread-content-wrapper').height());"
   "    setTimeout(function () {"
   "        if (initialHeight != $('#thread-content-wrapper').height())"
   "            $('#thread-content').scrollTop($('#thread-content-wrapper').height());"
   "    }, 500);"
   "}"])

(defn do-not-jump
  []
  [:script "function jumpToPost() {}"])

(defn update-bookmark-without-jumping
  [context]
  (let [{:keys [service server board thread bookmark]} context
        {:keys [res-count archived]} (db/get-thread-info service board thread)]
    (db/update-bookmark service board thread res-count)
    [:script "function jumpToPost() {}"]))

(defn jump-to-first-new-post
  [context]
  ; (timbre/debug "jump-to-first-new-post:" context)
  (let [{:keys [service server board thread bookmark]} context
        {:keys [res-count archived]} (db/get-thread-info service board thread)]
    ; (db/update-bookmark service board thread res-count)
    ; (timbre/debug bookmark res-count)
    (cond
      ; There are new posts.
      (and bookmark (> bookmark 0) (> res-count bookmark))
      [:script
       "function jumpToPost() {\n"
       "    var initialHeight = $('#thread-content-wrapper').height();\n"

       "    var firstNewPostIndex = " bookmark ";\n"
       "    var firstNewPostSelector;\n"
       "    do {\n"
       "        ++firstNewPostIndex;\n"
       "        firstNewPostSelector = '#' + createElementIdForPost('" service "', '" board "', '" thread "', firstNewPostIndex.toString(), 'heading');\n"
       "    } while ($(firstNewPostSelector).length > 0 && ($(firstNewPostSelector).hasClass('aborn') || $(firstNewPostSelector).hasClass('hidden')));\n"

       "    if ($(firstNewPostSelector).length > 0) {\n"
       "        $('#thread-content').scrollTop($(firstNewPostSelector).offset().top - $('#thread-content-wrapper > div:first-child').offset().top - $('#thread-content-wrapper > div:first-child').innerHeight());\n"
       "        setTimeout(function () {"
       "            if (initialHeight != $('#thread-content-wrapper').height())\n"
       "                $('#thread-content').scrollTop($(firstNewPostSelector).offset().top - $('#thread-content-wrapper > div:first-child').offset().top + $('#thread-content-wrapper > div:first-child').innerHeight());\n"
       "        }, 500);\n"
       "    } else {\n"
       "        $('#thread-content').scrollTop($('#thread-content-wrapper').height());\n"
       "        setTimeout(function () {\n"
       "            if (initialHeight != $('#thread-content-wrapper').height())\n"
       "                $('#thread-content').scrollTop($('#thread-content-wrapper').height());\n"
       "        }, 500);\n"
       "    }\n"
       "}\n"]

      ; There are no new posts.
      (and bookmark (> bookmark 0) (not archived))
      (jump-to-end-of-thread)

      :else
      (do-not-jump))))

(defn get-posts-in-thread
  [context]
  ; (timbre/debug "get-posts-in-thread")
  (let [{:keys [service server board thread]} context
        thread-info         (db/get-thread-info service board thread)
        posts-from-database (if (:archived thread-info)
                              (get-posts-from-database (assoc context :archived true)))
        jump?               (and (not (:regex-search-pattern context))
                                 (>= 0 (count (:thread-options context)))
                                 (not (:new-posts-only context)))]
    (if (and (:archived thread-info) posts-from-database)
      (list
        (if (nil? (:append-after context))
          [:div.message-info-right-pane "このスレッドには書き込めません。" [:br] "保存されている過去ログを表示します。"])
        posts-from-database
        (if (and (:bookmark context)
                 (> (:res-count thread-info) (:bookmark context))
                 (not (:new-posts-only context)))
          (jump-to-first-new-post context)
          (do-not-jump)))

      (try
        (check-dat-file-availability (:thread-url context))
        (list
          (get-posts-in-current-dat-file context)
          (if jump?
            (jump-to-first-new-post context)
            (do-not-jump)))
        (catch Throwable t
          (timbre/info "    Failed to download current DAT file:" (str t))
          ; (print-stack-trace t)

          (try
            (check-thread-in-html-availability (:thread-url context))
            (list
              (get-posts-through-read-cgi context)
              (if jump?
                (jump-to-first-new-post context)
                (do-not-jump)))
            (catch Throwable t
              (timbre/info "    Failed to download thread in HTML:" (str t))
              ; (print-stack-trace t)

              (try
                (check-thread-in-json-availability (:thread-url context))
                (list
                  (get-posts-in-json context)
                  (if jump?
                    (jump-to-first-new-post context)
                    (do-not-jump)))
                (catch Throwable t
                  (timbre/info "    Failed to download thread in JSON:" (str t))
                  ; (print-stack-trace t)

                  (try
                    ; (throw (Exception.))
                    ; (check-dat-file-availability (:thread-url context))
                    (list
                      (get-posts-in-archived-dat-file context)
                      (if jump?
                        (jump-to-first-new-post context)
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
                            (if (nil? (:append-after context))
                              [:div.message-error-right-pane
                               "スレッドの読み込みに失敗しました。" [:br]
                               "保存されている過去ログを表示します。"])
                            (if jump?
                              (jump-to-end-of-thread)
                              (do-not-jump))))
                        (catch Throwable t
                          ; (timbre/debug "get-thread-content: get-posts-through-read-cgi failed:" (str t))
                          ; (print-stack-trace t)
                          (throw t))))))))))))))

(defn api-get-thread-content
  [original-thread-url search-text search-type append-after]
  ; (timbre/debug "api-get-thread-content")
  (timbre/info "Preparing thread content...")
  (let [start-time          (System/nanoTime)
        parts (split-thread-url original-thread-url)
        {:keys [service server original-server current-server board thread-no options]} parts
        thread-url (and parts (create-thread-url server board thread-no))
        append-after (if (< 0 (count append-after)) (Integer/parseInt append-after) nil)]
    (cond
      (not (check-login))
      (html [:script "open('/login', '_self');"])

      (nil? parts)
      (html (if (nil? append-after)
              [:div.message-error-right-pane
               "スレッドの読み込みに失敗しました。" [:br]
               "スレッドのアドレスが不正です。"]))

      :else
      (try
        (increment-http-request-count)
        ;  Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
        ; (timbre/debug "api-get-thread-content: options:" options)
        (let [start-time-for-subject-txt (System/nanoTime)
              active?             (is-thread-active? server board thread-no)
              _                   (if active?
                                    (db/mark-thread-as-active service server board thread-no)
                                    (db/mark-thread-as-archived service server board thread-no))
              _                   (timbre/info (str "    Checked subject.txt (" (format "%.0f" (* (- (System/nanoTime) start-time-for-subject-txt) 0.000001)) "ms)."))

              start-time-for-post-filters (System/nanoTime)
              post-signature-head (str service "," board "," thread-no ",")
              post-filters        (remove nil? (map #(if (and (or (= 0 (count (:board %1))) (= board (:board %1)))
                                                              (or (not (= (:filter-type %1) "post"))
                                                                        (re-find (re-pattern (str "^" post-signature-head)) (:pattern %1))))
                                                             %1
                                                             nil)
                                                          (db/get-all-post-filters)))
              ; _                   (timbre/debug post-filters)
              _                   (timbre/info (str "    Prepared post filters (" (format "%.0f" (* (- (System/nanoTime) start-time-for-post-filters) 0.000001)) "ms)."))

              bookmark            (db/get-bookmark service board thread-no)
              context             {:thread-url           thread-url
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
                                   :bookmark             bookmark
                                   :download-images      (= (db/get-user-setting "download-images") "true")
                                   :use-image-proxy      (= (db/get-system-setting "use-image-proxy") "true")
                                   :post-filters         post-filters
                                   :append-after         append-after
                                   :convert-raw-post-to-html convert-raw-post-to-html
                                   :active?              active?}
              ; _               (timbre/debug "api-get-thread-content: Calling get-posts-in-thread.")
              thread-heading-id (random-element-id)
              posts-in-thread   (get-posts-in-thread context)
              posts-in-thread     (if append-after
                                    posts-in-thread
                                    [:div#thread-content-wrapper
                                     [:div.thread-heading {:id thread-heading-id}
                                      (escape-html (remove-ng-words-from-thread-title (:title (db/get-thread-info service board thread-no))))]
                                     posts-in-thread])
              ; _               (timbre/debug "api-get-thread-content: Call to get-posts-in-thread is done.")
              {:keys [title res-count]} (db/get-thread-info service board thread-no)
              new-res-count-id    (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
              thread-title-class  (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
              start-time-for-response (System/nanoTime)
              result              (html
                                    [:script
                                     "currentThreadURL = decodeURIComponent('" (ring.util.codec/url-encode (str thread-url options)) "');"
                                     "setThreadTitle(decodeURIComponent('"
                                     (ring.util.codec/url-encode (remove-ng-words-from-thread-title (if title title "")))
                                     "'), "
                                     "decodeURIComponent('"
                                     (ring.util.codec/url-encode thread-url)
                                     "'));"]
                                    (if (nil? append-after)
                                      [:script
                                       "imageList = [];"
                                       "resetThumbnailLists();"
                                       "reverseAnchors = [];"])
                                    ; (do (timbre/debug "api-get-thread-content: before posts-in-thread.") nil)
                                    posts-in-thread
                                    ; (do (timbre/debug "api-get-thread-content: after posts-in-thread.") nil)
                                    [:script#thread-content-run-once
                                     ; "$(document).ready(function() {"
                                     "loadThumbnails();"
                                     "updateIDs();"
                                     "createReverseAnchors('" (:service context) "', '" (:board context) "', '" (:thread-no context) "');"
                                     (if (and (:bookmark context) (> res-count (:bookmark context)))
                                       (str
                                         "try {"
                                         "    if ($('#" new-res-count-id "').length > 0 /* && parseInt($('#" new-res-count-id "').html()) > 0 */) {"
                                         ; "        $('#" new-res-count-id "').html('-');"
                                         "        /* $('#" new-res-count-id "').removeClass('non-zero new-thread'); */"
                                         "        $('#" new-res-count-id "').removeClass('new-thread');"
                                         "        $('." thread-title-class "').removeClass('new-thread');"
                                         "        $('#thread-list-table').trigger('update');"
                                         "    }"
                                         "} catch (e) {"
                                         "}"))
                                     ; "});"
                                     ])]

          (timbre/info (str "    Created response (" (format "%.0f" (* (- (System/nanoTime) start-time-for-response) 0.000001)) "ms, " (count result) " characters)."))
          (db/update-board-server-if-there-is-no-info service server board)
          (if (or (nil? bookmark) (zero? bookmark))
            (db/update-bookmark service board thread-no 1))

          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (timbre/info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
          result)
        (catch Throwable t
          (timbre/debug "api-get-thread-content: Exception caught:" (str t))
          (print-stack-trace t)
          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (html (if (nil? append-after)
                  [:div.message-error-right-pane
                   "スレッドの読み込みに失敗しました。"
                   (let [bbn-result (bbn-check)]
                     (if (and (or (net-url? thread-url) (bbspink-url? thread-url)) bbn-result)
                       (list [:br] bbn-result)))])))))))

(defn get-new-posts-in-thread
  [service server board thread-no board-info]
  (let [active?         (is-thread-active? server board thread-no)
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
        thread-url      (create-thread-url server board thread-no)
        thread-info     (db/get-thread-info service board thread-no)
        context         {:thread-url           thread-url
                         :service              service
                         :server               (:server board-info)
                         :original-server      server
                         :current-server       (:server board-info)
                         :board                board
                         :thread               (str thread-no)
                         :thread-no            (str thread-no)
                         :thread-options       nil
                         :shitaraba?           (shitaraba-url? thread-url)
                         :machi-bbs?           (machi-bbs-url? thread-url)
                         :regex-search-pattern nil
                         :bookmark             (db/get-bookmark service board thread-no)
                         :download-images      (= (db/get-user-setting "download-images") "true")
                         :use-image-proxy      (= (db/get-system-setting "use-image-proxy") "true")
                         :post-filters         post-filters
                         :append-after         false
                         :convert-raw-post-to-html convert-raw-post-to-html
                         :new-posts-only       true
                         :res-count            (:res-count thread-info)
                         :active?              active?}
        posts-in-thread (get-posts-in-thread context)
        {:keys [title res-count]} (db/get-thread-info service board thread-no)
        thread-heading-id (random-element-id)]

    (list
      [:div.thread-heading {:id thread-heading-id} (escape-html (remove-ng-words-from-thread-title (:title (db/get-thread-info service board thread-no))))]
      [:script "reverseAnchors = [];"]
      posts-in-thread
      [:script
       "createReverseAnchors('" (:service context) "', '" (:board context) "', '" (:thread-no context) "');"
			 "$(document).ready(function() {"
			 "$('#" thread-heading-id "').click(function () {"
       "updateThreadContent(null, '" thread-url "', '', '', true);"
			 "});"
			 "});"
      ])))

(defn get-threads-with-new-posts
  [board-url]
  (try
    (let [{:keys [server service board]} (split-board-url board-url)
          body      (get-subject-txt board-url true)
          all-items (clojure.string/split body #"\n")
          items     (if (shitaraba-url? board-url) (drop-last all-items) all-items)]

      (if (nil? body)
        nil
        (remove not
                (for [item items]
                  (let [parts (if (shitaraba-url? board-url)
                                (re-find #"^(([0-9]+)\.cgi),(.*)\(([0-9]+)\)$" item)
                                (re-find #"^(([0-9]+)\.dat)<>(.*) +\(([0-9]+)\)$" item))]
                    (and parts
                         (let [thread-no (nth parts 2 "")
                               res-count (Integer/parseInt (nth parts 4))
                               bookmark  (db/get-bookmark service board thread-no)]
                           (and bookmark
                                (> bookmark 0)
                                (> res-count bookmark)
                                thread-no))))))))
    (catch Throwable e
      nil)))

(defn api-get-new-posts-in-board
  [board-url]
  (let [parts (split-board-url board-url)]
    (cond
      (not (check-login))
      (html [:script "open('/login', '_self');"])

      (nil? parts)
      (html [:div.message-error-right-pane
             "新着まとめ読みの読み込みに失敗しました。" [:br]
             "板のアドレスが不正です。"])

      :else
      (try
        (timbre/info "Preparing new posts in board...")
        (increment-http-request-count)
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
        (let [start-time (System/nanoTime)
              {:keys [service server board]} (split-board-url board-url)
              board-info      (db/get-board-info service board)
              result          (html
                                [:script
                                 "currentThreadURL = ''"";"
                                 "setThreadTitle(decodeURIComponent('"
                                 (ring.util.codec/url-encode (str "「" (:board-name board-info) "」の新着まとめ"))
                                 "'), '');"]
                                [:script
                                   "imageList = [];"
                                   "resetThumbnailLists();"]

                                ; posts-in-thread
                                [:div#thread-content-wrapper
                                 (cp/pmap
                                   number-of-threads-for-new-posts
                                   #(get-new-posts-in-thread service server board %1 board-info)
                                   (get-threads-with-new-posts board-url))]

                                [:script#thread-content-run-once
                                 ; "$(document).ready(function() {"
                                 "loadThumbnails();"
                                 "updateIDs();"
                                 ; "});"
                                 ])]

          (db/update-board-server-if-there-is-no-info service server board)
          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (timbre/info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
          result)

        (catch Throwable t
          (timbre/debug "api-get-new-posts-in-board: Exception caught:" (str t))
          (print-stack-trace t)
          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (html [:div.message-error-right-pane "スレッドの読み込みに失敗しました。"]))))))

(defn get-new-posts
  [title thread-list-fn]
  (let []
    (cond
      (not (check-login))
      (html [:script "open('/login', '_self');"])

      :else
      (try
        (timbre/info "Preparing new posts...")
        (increment-http-request-count)
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/MAX_PRIORITY)
        (let [start-time (System/nanoTime)
              result          (html
                                [:script
                                 "currentThreadURL = ''"";"
                                 "setThreadTitle(decodeURIComponent('"
                                 (ring.util.codec/url-encode title)
                                 "'), '');"]
                                [:script
                                   "imageList = [];"
                                   "resetThumbnailLists();"]

                                ; posts-in-thread
                                [:div#thread-content-wrapper
                                 (map #(let [board-info (db/get-board-info (:service %1) (:board %1))
                                             res-count (:res-count (db/get-thread-info (:service %1) (:board %1) (:thread-no %1)))
                                             bookmark  (db/get-bookmark (:service %1) (:board %1) (:thread-no %1))]
                                         (if (and bookmark
                                                  (> bookmark 0)
                                                  (> res-count bookmark))
                                           (try
                                             (get-new-posts-in-thread (:service %1)
                                                                      (:server board-info)
                                                                      (:board %1)
                                                                      (:thread-no %1)
                                                                      board-info)
                                             (catch Throwable t
                                               (timbre/error "get-new-posts: Exception caught:" (str t))
                                               (print-stack-trace t)
                                               nil))))
                                      (thread-list-fn))]

                                [:script#thread-content-run-once
                                 "$(document).ready(function() {"
                                 "loadThumbnails();"
                                 "updateIDs();"
                                 "});"])]

          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (timbre/info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
          result)

        (catch Throwable t
          (timbre/error "get-new-posts: Exception caught:" (str t))
          (print-stack-trace t)
          (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
          (decrement-http-request-count)
          (html [:div.message-error-right-pane "新着まとめの読み込みに失敗しました。"]))))))

(defn get-new-posts-in-favorite-threads
  []
  (get-new-posts "「お気にスレ」の新着まとめ" db/get-favorite-threads))

(defn get-new-posts-in-recently-viewed-threads
  []
  (get-new-posts "「最近読んだスレ」の新着まとめ" db/get-recently-viewed-threads))

(defn get-new-posts-in-recently-posted-threads
  []
  (get-new-posts "「書き込み履歴」の新着まとめ" db/get-recently-posted-threads))

(defn api-delete-thread-log
  [thread-url]
  ; (timbre/debug "api-delete-thread-log")
  (if (not (check-login))
    (ring.util.response/not-found "404 Not Found")
    (let [{:keys [server board thread posts]} (split-thread-url thread-url)
          service (server-to-service server)]
      (try (db/delete-dat-file service board thread)        (catch Throwable t (timbre/debug "db/delete-dat-file failed")))
      (try (db/delete-thread-in-html service board thread)  (catch Throwable t (timbre/debug "db/delete-thread-in-html")))
      (try (db/delete-thread-info service board thread)     (catch Throwable t (timbre/debug "db/delete-thread-info failed")))
      (try (db/delete-bookmark service board thread)        (catch Throwable t (timbre/debug "db/delete-bookmark failed")))
      (try (db/update-bookmark service board thread 0)      (catch Throwable t (timbre/debug "db/update-bookmark failed")))
      (try (db/delete-favorite-thread service board thread) (catch Throwable t (timbre/debug "db/delete-favorite-thread failed")))
      (timbre/info "Thread log was deleted:" thread-url)
      "OK")))

(defn api-convert-thread-url-to-board-url
  [thread-url]
  (let [{:keys [server board]} (split-thread-url thread-url)]
    (str "http://" server "/" board "/")))

(defn api-add-aborn-filter-with-post-signature
  [post-signature]
  ; (timbre/debug "api-add-aborn-filter-with-post-signature:" post-signature)
  (if (or
        (not (check-login))
        (nil? (re-find #"^[a-z0-9._]+,[a-z0-9]+,[0-9]+,[0-9]+," post-signature)))
    (ring.util.response/not-found "404 Not Found")
    (let [parts (clojure.string/split post-signature #",")
          service    (nth parts 0)
          board      (nth parts 1)
          thread-no  (nth parts 2)
          post-index (nth parts 3)]
      (db/add-post-filter {:user-id     (:id (session/get :user))
                           :filter-type "post"
                           :pattern     post-signature
                           :board       board})
      (timbre/debug "OK")
      "OK")))

(defn api-add-aborn-filter-with-id
  [id]
  ; (timbre/debug "api-add-aborn-filter-with-id:" id)
  (if (or
        (not (check-login))
        (nil? (post-id? id)))
    nil
    (let []
      (db/add-post-filter {:user-id     (:id (session/get :user))
                           :filter-type "id"
                           :pattern     id})
      (timbre/debug "api-add-aborn-filter-with-id: OK")
      "OK")))

(defn api-update-bookmark
  [service board thread-no post-index]
  ; (timbre/debug "api-update-bookmark:" service board thread-no post-index)
  (and (check-login)
       (let [thread-no  (java.lang.Long/parseLong thread-no)
             post-index (java.lang.Integer/parseInt post-index)
             thread-info (db/get-thread-info service board thread-no)
             bookmark   (db/get-bookmark service board thread-no)
             updated?   (or (nil? bookmark) (> post-index bookmark))]
         ; (timbre/debug "api-update-bookmark:" bookmark post-index)
         (when thread-info
           (if updated?
             (db/update-bookmark service board thread-no post-index))
           (db/update-time-last-viewed service board thread-no (clj-time.coerce/to-sql-time (clj-time.core/now)))
           (let [viewed-post-count (if updated? (- post-index bookmark) 0)
                 post-count (:res-count thread-info)
                 bookmark   (cond updated? post-index
                                  bookmark bookmark
                                  :else    post-count)
                 new-post-count (- post-count bookmark)]
             (str new-post-count "," post-count "," viewed-post-count))))))

(defroutes thread-content-routes
  (GET "/api-get-thread-content"
       [thread-url search-text search-type append-after]
       (api-get-thread-content (trim thread-url) search-text search-type append-after))
  (GET "/api-get-new-posts-in-board"
       [board-url]
       (api-get-new-posts-in-board (trim board-url)))
  (GET "/api-get-new-posts-in-favorite-threads"
       []
       (get-new-posts-in-favorite-threads))
  (GET "/api-get-new-posts-in-recently-viewed-threads"
       []
       (get-new-posts-in-recently-viewed-threads))
  (GET "/api-get-new-posts-in-recently-posted-threads"
       []
       (get-new-posts-in-recently-posted-threads))

  (GET "/api-delete-thread-log"
       [thread-url]
       (api-delete-thread-log (trim thread-url)))

  (GET "/api-convert-thread-url-to-board-url"
       [thread-url]
       (api-convert-thread-url-to-board-url (trim thread-url)))

  (GET "/api-add-aborn-filter-with-post-signature"
       [post-signature]
       (api-add-aborn-filter-with-post-signature post-signature))
  (GET "/api-add-aborn-filter-with-id"
       [id]
       (api-add-aborn-filter-with-id id))
  (GET "/api-update-bookmark"
       [service board thread-no post-index]
       (api-update-bookmark service board thread-no post-index)))
