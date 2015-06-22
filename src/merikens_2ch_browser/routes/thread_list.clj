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



(ns merikens-2ch-browser.routes.thread-list
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
            [hiccup.util :refer [escape-html]]
            [taoensso.timbre :refer [log]]
            [clj-time.core]
            [clj-time.coerce]
            [clj-time.format]
            [merikens-2ch-browser.cursive :refer :all]
            [merikens-2ch-browser.util :refer :all]
            [merikens-2ch-browser.param :refer :all]
            [merikens-2ch-browser.url :refer :all]
            [merikens-2ch-browser.auth :refer :all]
            [merikens-2ch-browser.routes.thread-content :refer [get-posts-in-current-dat-file
                                                                get-posts-in-archived-dat-file
                                                                get-posts-through-read-cgi]]
            [merikens-2ch-browser.db.core :as db]
            [com.climate.claypoole :as cp])
  (import   [org.apache.commons.lang3.StringUtils]))




(defn get-log-list
  [^String board-url]
  (let [{:keys [service board]} (split-board-url board-url)
        ; board-info (db/get-board-info service board)
        log-list (concat (db/get-dat-file-list-for-board service board) (db/get-html-file-list-for-board service board))]
    (clojure.string/join
      (map #(let [thread-info (db/get-thread-info service board (:thread-no %1))]
              (str (:thread-no %1) ".dat<>" (:title thread-info) " (" (:res-count %1) ")\n"))
           log-list))))

(defn add-highlights-to-thread-title-and-escape-html
  ^String
[^String s
 ^java.util.regex.Pattern re]
  (let [matcher (re-matcher re s)]
    (if (not (java-matcher-find matcher))
      (escape-html s)
      (str
        (escape-html (subs s 0 (java-matcher-start matcher)))
        (str "<span style='background-color: yellow;'>"
             (escape-html (subs s (java-matcher-start matcher) (java-matcher-end matcher)))
             "</span>")
        (add-highlights-to-thread-title-and-escape-html (subs s (java-matcher-end matcher) (count s))
                                                        re)))))

(defn thread-list-item-to-html
  ^clojure.lang.IPersistentMap
[^String                  item
 ^Integer                 index
 ^String                  board-url
 ^java.util.regex.Pattern regex-search-pattern
 ^clojure.lang.IPersistentMap bookmark-map
 ^String                      ie]
  (try
    (if (nil? (re-find #"^([0-9]+\.(dat|cgi))(<>|,)" item))
      (throw (new Exception)))
    (let [dat (second (re-find #"^([0-9]+\.(dat|cgi))(<>|,)" item))
          created (-> dat
                    (clojure.string/replace #"\.(dat|cgi)$" "")
                    (Long/parseLong )
                    (* 1000)
                    (clj-time.coerce/from-long)
                    (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
          time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
          after-dat (clojure.string/replace item #"^[0-9]+\.(dat|cgi)(<>|,)" "")
          res-count (Integer/parseInt (clojure.string/replace (clojure.string/replace after-dat #"^.* ?\(" "") #"\)$" ""))
          thread-id (random-string 16)
          board-url-parts (re-find #"^http://([a-z0-9.-]+)/(.*)/$" board-url)
          server     (nth board-url-parts 1)
          board      (nth board-url-parts 2)
          board      (if (shingetsu-server? server) (clojure.string/replace board #"_[0-9A-F]+$" "") board)
          ; board-url  (create-board-url server board)
          thread-no  (clojure.string/replace dat #"\.(dat|cgi)$" "")
          thread-url (create-thread-url server board thread-no)
          speed      (float (/ res-count (/ time-since-creation 60 60 24)))
          service    (server-to-service server)
          new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
          res-count-id (clojure.string/replace (str "post-count-" service "-" board "-" thread-no) #"[./]" "_")
          thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
          bookmark   (or ((keyword thread-no) bookmark-map)
                         (db/get-bookmark service board thread-no))
          ; thread-info (db/get-thread-info service board thread-no)
          new-thread? (nil? bookmark)
          new-res?   (and bookmark (> bookmark 0) (> (- res-count bookmark) 0))
          new-res-count (cond
                          (nil? bookmark) "新"
                          new-res? (- res-count bookmark)
                          (<= res-count bookmark) 0
                          :else "")
          title      (unescape-html-entities (clojure.string/replace after-dat #" *\([0-9]+\)$" ""))
          title      (remove-ng-words-from-thread-title title)
          real-title (if (sc-url? board-url) (clojure.string/replace title #"^★" "") title)
          real-title-with-highlights (if (nil? regex-search-pattern)
                                       (escape-html real-title)
                                       (add-highlights-to-thread-title-and-escape-html real-title regex-search-pattern))
          item-in-html       (if (or (<= res-count 0) ; for machi.to
                                     (and regex-search-pattern (not (re-find regex-search-pattern real-title))))
                               (list)
                               (list
                                 [:tr {:id thread-id}
                                  [:td
                                   [:span
                                    {:id new-res-count-id
                                     :class (cond new-res?        "new-post-count non-zero"
                                                  (nil? bookmark) "new-post-count new-thread"
                                                  :else           "new-post-count")}
                                    new-res-count]]
                                  [:td {:id res-count-id} res-count]
                                  [:td index]
                                  (if (sc-url? board-url)
                                    [:td {:class (str thread-title-class (if new-thread? " new-thread" ""))} (if (re-find #"^★" title) "★" "")])
                                  [:td
                                   {:style "text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
                                    :class (str thread-title-class (if new-thread? " new-thread" ""))}
                                   real-title-with-highlights]
                                  [:td (if (<= speed 0) "" (format "%3.1f" speed))]
                                  [:td (if (<= speed 0) "" (clj-time.format/unparse (clj-time.format/formatter-local "yyyy/MM/dd HH:mm") created))]]
                                 [:script "threadURLList.push('" thread-url "');"]
                                 [:script "threadListItemList.push({id: '" thread-id "', url: '" thread-url "', title: decodeURIComponent('" (ring.util.codec/url-encode real-title) "')});"]
                                 ))]
      ; (if new-thread?
      ;   (db/update-bookmark service board thread-no 0))
      ; (db/update-thread-res-count service server board thread-no res-count)
      {:item-in-html (if (= ie "1") item-in-html (html item-in-html))
       :item-info    {:service     service
                      :server      server
                      :board       board
                      :thread-no   thread-no
                      :res-count   res-count
                      :new-thread? new-thread?}})
    (catch Throwable t
      (print-stack-trace t)
      nil)))

(defn update-database-for-thread-list
  [item-info-list]
  ; (log :info "update-database-for-thread-list")
  (Thread/sleep 1000)
  (doall (map #(let [{:keys [service server board thread-no res-count new-thread?]} %1]
                 ; (log :info "update-database-for-thread-list:" service server board thread-no res-count new-thread?)
                 (if new-thread?
                   (db/update-bookmark service board thread-no 0))
                 (db/update-thread-res-count service server board thread-no res-count)
                 (db/mark-thread-as-active service server board thread-no)
                 nil)
              item-info-list)))

(defn api-get-thread-list
  [^String board-url
   search-text
   _ ; search-type
   ie
   log-list]
  ; (log :debug "api-get-thread-list:" board-url search-text search-type)
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (try
      (log :info "Preparing thread list...")
      (increment-http-request-count)
      (.setPriority (java.lang.Thread/currentThread) web-sever-thread-priority)
      (let [start-time (System/nanoTime)
            {:keys [server service board]} (split-board-url board-url)
            body (if (= log-list "1") (get-log-list board-url) (get-subject-txt board-url true))
            _ (if (nil? body) (throw (Exception. "Failed to obtain subject.txt/log list.")))
            subject-txt-time-spent (* (- (System/nanoTime) start-time) 0.000001)

            board-info (db/get-board-info service board)
            board-name (and (:board-name board-info)
                            (str (:board-name board-info) " [" service "]"))
            bookmark-map (create-bookmark-map
                           service
                           (if (shingetsu-server? server)
                             (clojure.string/replace board #"_[0-9A-F]+$" "")
                             board))
            all-items (clojure.string/split body #"\n")
            items (if (shitaraba-url? board-url) (drop-last all-items) all-items) ; Shitaraba sucks.
            regex-search-pattern (cond
                                   (or (nil? search-text) (<= (count search-text) 0))
                                   nil
                                   :else
                                   (re-pattern search-text))
            items  (doall (cp/pmap
                            number-of-threads-for-thread-list
                            thread-list-item-to-html
                            items
                            (range 1 (inc (count items)))
                            (repeat board-url)
                            (repeat regex-search-pattern)
                            (repeat bookmark-map)
                            (repeat ie)))
            items-in-html (doall (map :item-in-html items))
            ; _ (println (nth items-in-html 0))
            ; _ (println (nth (nth (nth (nth (nth items-in-html 0) 0) 2) 1) 2))
            items-in-html (if (not (= ie "1"))
                            items-in-html
                            (try
                              (sort #(let [left  (nth (nth (nth (nth %1 0 nil) 2 nil) 1 nil) 2 nil)
                                           right (nth (nth (nth (nth %2 0 nil) 2 nil) 1 nil) 2 nil)
                                           left-star  (if (sc-url? board-url) (nth (nth (nth %1 0 nil) 5 nil) 2 nil) "")
                                           right-star (if (sc-url? board-url) (nth (nth (nth %2 0 nil) 5 nil) 2 nil) "")]
                                       (cond (and (not (= left "")) (= right "")) -1
                                             (and (= left "") (not (= right ""))) 1

                                             (and (string? left) (not (string? right))) -1
                                             (and (not (string? left)) (string? right)) 1

                                             (and (number? left) (number? right) (not (= left right))) (< right left)

                                             (and (not (= left-star "")) (= right-star "")) -1
                                             (and (= left-star "") (not (= right-star ""))) 1

                                             :else 0))
                                    items-in-html)
                              (catch Throwable t
                                (print-stack-trace t)
                                items-in-html)))
            item-info-list (doall (map :item-info items))
            thread-list-item-to-html-time-spent (- (* (- (System/nanoTime) start-time) 0.000001) subject-txt-time-spent)

            sort-arrow (if (not (= ie "1")) [:span.sortArrow "&nbsp;"] "")
            response (html
                       [:div#thread-list-fixed-table-container.fixed-table-container.sort-decoration
                        [:div.header-background]
                        [:div.fixed-table-container-inner.scrollbar

                         [:table#thread-list-table.tablesorter {:cellspacing 0}
                          [:thead
                           [:tr
                            [:th.first.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner  [:span"新着"] sort-arrow]]
                            [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner [:span "レス"] sort-arrow]]
                            [:th {:style "width: 47px"} [:div.th-inner [:span "No."] sort-arrow]]
                            (if (sc-url? board-url)
                              [:th {:style "width: 36px"} [:div.th-inner [:span "★"] sort-arrow]])
                            [:th                                                 [:div.th-inner [:span "タイトル"] sort-arrow]]
                            [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner [:span "勢い"] sort-arrow]]
                            [:th.sortInitialOrder-desc {:style "width: 140px"} [:div.th-inner [:span "開始日時"] sort-arrow]]]]
                          [:tbody items-in-html]]]]
                       (if board-name
                         [:script "setBoardName(decodeURIComponent('" (ring.util.codec/url-encode board-name) "'), '" board-url"');"])
                       (if (not (= ie "1"))
                         [:script
                          "$('#thread-list-table').tablesorter({"
                          "sortList: ["
                          "[0,1]"
                          (if (sc-url? board-url) ",[3,0]" "")
                          "]});"]))
            created-response-time-spent (- (* (- (System/nanoTime) start-time) 0.000001) subject-txt-time-spent thread-list-item-to-html-time-spent)]

        (do (future (update-database-for-thread-list item-info-list))) ; Update database later.
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (do (future (let [end-time (System/nanoTime)]
                      (log :info (str "    Retrieved subject.txt (" (format "%.0f" subject-txt-time-spent) "ms)."))
                      (log :info (str "    Converted subject.txt into HTML (" (format "%.0f" thread-list-item-to-html-time-spent) "ms)."))
                      (log :info (str "    Created response (" (format "%.0f" created-response-time-spent) "ms)."))
                      (log :info (str "    Total time: " (format "%.0f" (* (- end-time start-time) 0.000001)) "ms")))))
        response)

      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (print-stack-trace t)
        (html
          [:div.message-error-right-pane
           "スレッド一覧の読み込みに失敗しました。"
           (let [bbn-result (bbn-check)]
             (cond
               (and (or (net-url? board-url) (bbspink-url? board-url)) bbn-result)
               (list [:br] bbn-result)))])))))

(defn similar-thread-list-item-to-html
  [item board-url original-thread-url original-thread-title]
  (try
    (let [dat (second (re-find #"^([0-9]+\.(dat|cgi))(<>|,)" item))
          created (-> dat
                    (clojure.string/replace #"\.(dat|cgi)$" "")
                    (Long/parseLong )
                    (* 1000)
                    (clj-time.coerce/from-long)
                    (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
          time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
          after-dat (clojure.string/replace item #"^[0-9]+\.(dat|cgi)(<>|,)" "")
          res-count (Integer/parseInt (clojure.string/replace (clojure.string/replace after-dat #"^.* ?\(" "") #"\)$" ""))
          thread-id (random-string 16)
          board-url-parts (re-find #"^http://([a-z0-9.]+)/(.*)/$" board-url)
          server     (nth board-url-parts 1)
          board      (nth board-url-parts 2)
          thread-no  (clojure.string/replace dat #"\.(dat|cgi)$" "")
          thread-url (create-thread-url server board thread-no)
          speed      (float (/ res-count (/ time-since-creation 60 60 24)))
          service    (server-to-service server)
          new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
          res-count-id (clojure.string/replace (str "post-count-" service "-" board "-" thread-no) #"[./]" "_")
          thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
          bookmark   (db/get-bookmark service board thread-no)
          ; thread-info (db/get-thread-info service board thread-no)
          new-thread? (nil? bookmark)
          new-res?   (and bookmark (> bookmark 0) (> (- res-count bookmark) 0))
          new-res-count (cond
                          (nil? bookmark) "新"
                          new-res? (- res-count bookmark)
                          (= res-count bookmark) 0
                          :else "")
          title      (unescape-html-entities (clojure.string/replace after-dat #" *\([0-9]+\)$" ""))
          title      (remove-ng-words-from-thread-title title)
          real-title (if (sc-url? board-url) (clojure.string/replace title #"^★" "") title)
          original-thread-title (remove-ng-words-from-thread-title original-thread-title)
          similarity (double (- 1 (/ (org.apache.commons.lang3.StringUtils/getLevenshteinDistance original-thread-title title)
                                     (max (count original-thread-title) (count title)))))]
      (if new-thread?
        (db/update-bookmark service board thread-no 0))
      (db/update-thread-res-count service server board thread-no res-count)
      (if (or (= thread-url original-thread-url) (< similarity 0.4))
        nil
        (list
          [:tr {:id thread-id}
           [:td (format "%3.0f%%" (* 100 similarity))]
           [:td
            [:span
             {:id new-res-count-id
              :class (cond new-res?        "new-post-count non-zero"
                           (nil? bookmark) "new-post-count new-thread"
                           :else           "new-post-count")}
             new-res-count]]
           [:td {:id res-count-id} res-count]
           (if (sc-url? board-url)
             [:td {:class (str thread-title-class (if new-thread? " new-thread" ""))} (if (re-find #"^★" title) "★" "")])
           [:td
            {:style "text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
             :class (str thread-title-class (if new-thread? " new-thread" ""))}
            real-title]
           [:td (if (<= speed 0) "" (format "%3.1f" speed))]
           [:td (if (<= speed 0) "" (clj-time.format/unparse (clj-time.format/formatter-local "yyyy/MM/dd HH:mm") created))]]

          ; (set-mousedown-event-handler (str "#" thread-id)
          ;                              (str "updateThreadContent(decodeURIComponent('" (if title (ring.util.codec/url-encode title)) "'), '" thread-url "');")
          ;                              (str "displayThreadMenu(event, '" thread-url "', '" server "', '" service "', '" board "', '" thread-no "');")
          ;                              (str "updateThreadContent(decodeURIComponent('" (if title (ring.util.codec/url-encode title)) "'), '" thread-url "', '', '', true);"))
          [:script "threadURLList.push('" thread-url "');"]
          [:script "threadListItemList.push({id: '" thread-id "', url: '" thread-url "', title: decodeURIComponent('" (ring.util.codec/url-encode real-title) "')});"])))
    (catch Throwable t
      (print-stack-trace t)
      nil)))

(defn api-get-similar-thread-list
  [thread-url ie]
  ; (log :debug "api-get-thread-list:" board-url search-text search-type)
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (try
      (increment-http-request-count)
      (.setPriority (java.lang.Thread/currentThread) web-sever-thread-priority)
      (let [{:keys [server service board thread-no]} (split-thread-url thread-url)
            normalized-thread-url (create-thread-url server board thread-no)
            thread-title (remove-ng-words-from-thread-title (:title (db/get-thread-info service board thread-no)))
            board-url (str "http://" server "/" board "/")
            board-info (db/get-board-info service board)
            board-name (and (:board-name board-info)
                            (str (:board-name board-info) " [" service "]"))
            result (html
                     (let [body (get-subject-txt board-url true)]
                       (if (nil? body)
                         [:div.message-error-right-pane
                          "スレッド一覧の読み込みに失敗しました。"
                          (let [bbn-result (bbn-check)]
                            (cond
                              (and (or (net-url? board-url) (bbspink-url? board-url)) bbn-result)
                              (list [:br] bbn-result)

                              (re-find #"人大杉" body)
                              "<br>人大杉です。"))]
                         (let [all-items (clojure.string/split body #"\n")
                               items (if (shitaraba-url? board-url) (drop-last all-items) all-items) ; Shitaraba sucks.
                               items-in-html (cp/pmap
                                               number-of-threads-for-thread-list
                                               similar-thread-list-item-to-html
                                               items
                                               (repeat board-url)
                                               (repeat normalized-thread-url)
                                               (repeat thread-title))
                               ; _ (println (nth (nth items-in-html 0) 0))
                               sorted-items-in-html (try
                                                      (sort #(let [left  (nth (nth (nth %1 0) 2) 1)
                                                                   right (nth (nth (nth %2 0) 2) 1)]
                                                               (cond :else (compare right left)))
                                                            items-in-html)
                                                      (catch Throwable t
                                                        (print-stack-trace t)
                                                        items-in-html))
                               sort-arrow (if (not (= ie "1")) [:span.sortArrow "&nbsp;"] "")
                               set-board-name (if board-name
                                                [:script "setBoardName(decodeURIComponent('" (ring.util.codec/url-encode (str "「" thread-title "」の似スレ一覧")) "'));"])]
                           (if (empty? (remove nil? items-in-html))
                             (list
                               [:div.message-info-right-pane "似スレが見つかりませんでした。"]
                               set-board-name)
                             (list
                               [:div#thread-list-fixed-table-container.fixed-table-container.sort-decoration
                                [:div.header-background]
                                [:div.fixed-table-container-inner.scrollbar

                                 [:table#thread-list-table.tablesorter {:cellspacing 0}
                                  [:thead
                                   [:tr
                                    [:th.first.sortInitialOrder-desc {:style "width: 60px"} [:div.th-inner  [:span "類似率"] sort-arrow]]
                                    [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner  [:span"新着"] sort-arrow]]
                                    [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner [:span "レス"] sort-arrow]]
                                    (if (sc-url? board-url)
                                      [:th {:style "width: 36px"} [:div.th-inner [:span "★"] sort-arrow]])
                                    [:th                                                 [:div.th-inner [:span "タイトル"] sort-arrow]]
                                    [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner [:span "勢い"] sort-arrow]]
                                    [:th.sortInitialOrder-desc {:style "width: 140px"} [:div.th-inner [:span "開始日時"] sort-arrow]]]]
                                  [:tbody (if (= ie "1") sorted-items-in-html items-in-html)]]]]
                               set-board-name
                               (if (not (= ie "1"))
                                 [:script
                                  "$('#thread-list-table').tablesorter({"
                                  "sortList: [[0,1]]});"])))))))]
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        result)

      (catch Throwable t
        (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
        (decrement-http-request-count)
        (print-stack-trace t)
        (html
          [:div.message-error-right-pane
           "スレッド一覧の読み込みに失敗しました。"
           (let [bbn-result (bbn-check)]
             (cond
               (and (or (net-url? thread-url) (bbspink-url? thread-url)) bbn-result)
               (list [:br] bbn-result)))])))))

(defn convert-special-thread-list-item-to-html
  [item index context board-info-map enable-table-sorter]
  ; (log :debug item)
  (try
    (let [{:keys [service server board thread-no]} item
          thread-info (db/get-thread-info service board thread-no)
          {:keys [title res-count]} thread-info
          title      (remove-ng-words-from-thread-title title)
          ; board-info {:server server :board-name "名無しの板"}
          ; board-info (db/get-board-info service board)
          board-info ((keyword (str service "#" board)) board-info-map)
          current-server (:server board-info)
          thread-url (create-thread-url (if current-server current-server server) board thread-no)
          board-name (str (:board-name board-info) (cond (= service "2ch.sc") "(sc)"
                                                         (= service "2ch.net") "(net)"
                                                         (= service "open2ch.net") "(op)"))

          created (-> thread-no
                    (* 1000)
                    (clj-time.coerce/from-long)
                    (clj-time.core/to-time-zone (clj-time.core/time-zone-for-offset +9)))
          time-since-creation (/ (- (clj-time.coerce/to-long (clj-time.core/now)) (clj-time.coerce/to-long created)) 1000)
          thread-id (random-string 16)
          speed      (float (/ (if (nil? res-count) 1 res-count) (/ time-since-creation 60 60 24)))
          bookmark      (db/get-bookmark service board thread-no)
          new-thread? (nil? bookmark)
          new-res?   (or new-thread? (and bookmark (> bookmark 0) (> (- res-count bookmark) 0)))
          new-res-count (cond
                          (nil? bookmark) res-count
                          new-res? (- res-count bookmark)
                          (= res-count bookmark) 0
                          :else "")
          new-res-count-id (clojure.string/replace (str "new-post-count-" service "-" board "-" thread-no) #"[./]" "_")
          res-count-id (clojure.string/replace (str "post-count-" service "-" board "-" thread-no) #"[./]" "_")
          thread-title-class (clojure.string/replace (str "thread-title-" service "-" board "-" thread-no) #"[./]" "_")
          regex-search-pattern (:regex-search-pattern context)
          item-in-html (if (and regex-search-pattern (not (re-find regex-search-pattern title)))
                         nil
                         (list
                           [:tr {:class (if (:archived thread-info) "archived" "") :id thread-id}
                            [:td
                             [:span
                              {:id new-res-count-id
                               :class (cond new-res?        "new-post-count non-zero"
                                            :else           "new-post-count")}
                              new-res-count]]
                            [:td {:id res-count-id} res-count]
                            [:td index]
                            [:td
                             {:style "text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"}
                             (if (nil? regex-search-pattern)
                               (escape-html title)
                               (add-highlights-to-thread-title-and-escape-html title regex-search-pattern))]
                            [:td
                             {:style "text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :class thread-title-class}
                             board-name]
                            [:td (if (or (<= speed 0) (nil? res-count)) "" (format "%3.1f" speed))]
                            [:td (if (<= speed 0) "" (clj-time.format/unparse (clj-time.format/formatter-local "yyyy/MM/dd HH:mm") created))]]

                           ; The number of script tags must be even.
                           ; (set-mousedown-event-handler (str "#" thread-id)
                           ;                              (str "updateThreadContent(decodeURIComponent('" (if title (ring.util.codec/url-encode title)) "'), '" thread-url "');")
                           ;                              (str "displayThreadMenu(event, '" thread-url "', '" server "', '" service "', '" board "', '" thread-no "');")
                           ;                              (str "updateThreadContent(decodeURIComponent('" (if title (ring.util.codec/url-encode title)) "'), '" thread-url "', '', '', true);"))
                           [:script "threadURLList.push('" thread-url "');"]
                           [:script  "threadListItemList.push({id: '" thread-id "', url: '" thread-url "', title: decodeURIComponent('" (if title (ring.util.codec/url-encode title)) "')});"]))]

      (if enable-table-sorter (html item-in-html) item-in-html))

    (catch Throwable t
      (log :debug "convert-special-thread-list-item-to-html: Exception caught:" (str t))
      (log :debug "item:" item)
      (log :debug "index:" index)
      (log :debug "context:" context)
      (print-stack-trace t)
      nil)))

(defn update-res-count-for-board
  "Returns the number of new posts."
  [board-url]
  (try
    (let [{:keys [server service board]} (split-board-url board-url)
          board     (if (shingetsu-server? server)
                      (clojure.string/replace board #"_[0-9A-F]+$" "")
                      board)
          body      (get-subject-txt board-url true)
          all-items (clojure.string/split body #"\n")
          items     (if (shitaraba-url? board-url) (drop-last all-items) all-items)]

      (if (nil? body)
        nil
        (for [item items]
          (let [parts (if (or (shitaraba-url? board-url) (machi-bbs-url? board-url))
                        (re-find #"^(([0-9]+)\.cgi),(.*)\(([0-9]+)\)$" item)
                        (re-find #"^(([0-9]+)\.dat)<>(.*) +\(([0-9]+)\)$" item))]
            (if parts
              (let [thread-no (Long/parseLong (nth parts 2 ""))
                    res-count (Integer/parseInt (nth parts 4))]
                (db/update-thread-res-count service server board thread-no res-count)
                (create-thread-url server board thread-no))
              nil)))))
    (catch Throwable _
      nil)))

(defn update-res-count-for-multiple-threads
  [items archive-threads]
  ; (log :debug "update-res-count-for-multiple-threads:" items)
  (try
    (let [threads (remove nil? (map #(try
                                       (let [{:keys [service board thread-no]} %1
                                             board-info (db/get-board-info service board)
                                             server (:server board-info)
                                             thread-url (create-thread-url server board thread-no)]
                                         {:thread-url thread-url
                                          :board-url (create-board-url server board)})
                                       (catch Throwable _
                                         ; (print-stack-trace t)
                                         nil))
                                    items))
          ; _      (log :debug "threads:" (count threads))
          boards (distinct (map #(:board-url %1) threads))
          ; _      (log :debug "boards:" boards)
          updated-threads (apply concat (doall (cp/pmap :builtin update-res-count-for-board boards)))
          ; _      (log :debug "(map :thread-url threads):" (map :thread-url threads))
          ; _      (log :debug "updated-threads:" updated-threads)
          untouched-threads (into () (clojure.set/difference (set (map :thread-url threads)) (set updated-threads)))
          ; _      (log :debug "untouched-threads:" untouched-threads)
          untouched-active-threads (remove #(do
                                              (try
                                                (let [{:keys [service board thread-no]} (split-thread-url %1)]
                                                  ; (log :debug (:archived (db/get-thread-info service board thread-no)))
                                                  (:archived (db/get-thread-info service board thread-no)))
                                                (catch Throwable _ true)))
                                           untouched-threads)
          ; _      (log :debug "untouched-active-threads:" untouched-active-threads)
          threads-to-be-archived  (remove #(do
                                             (try
                                               (let [{:keys [server board thread-no]} (split-thread-url %1)]
                                                 (is-thread-active? server board thread-no))
                                               (catch Throwable _ true)))
                                          untouched-active-threads)
          ;_      (log :debug "threads-to-be-archived:" threads-to-be-archived)
          ]

      (when (and archive-threads (> (count threads-to-be-archived) 0))
        (log :debug "Archiving threads:" (count threads-to-be-archived))
        (do (future (doall (map #(let [_ (log :debug "Downloading thread to archive:" %1)
                                       {:keys [service server board thread-no]} (split-thread-url %1)
                                       board (if (shingetsu-server? server)
                                               (clojure.string/replace board #"_[0-9A-F]+$" "")
                                               board)
                                       context {:thread-url  %1
                                                :service     service
                                                :server      server
                                                :current-server  server
                                                :original-server server
                                                :board       board
                                                :thread      thread-no
                                                :thread-no   thread-no
                                                :shitaraba?  (shitaraba-url? %1)
                                                :machi-bbs?  (machi-bbs-url? %1)
                                                :count-posts true}]
                                   (try
                                     (check-dat-file-availability (:thread-url context))
                                     (get-posts-in-current-dat-file context)
                                     (catch Throwable _
                                       (try
                                         (check-dat-file-availability (:thread-url context))
                                         (get-posts-in-archived-dat-file context)
                                         (catch Throwable _
                                           (try
                                             (get-posts-through-read-cgi context)
                                             (catch Throwable _))))))
                                   (db/mark-thread-as-archived service server board thread-no))
                                (take number-of-threads-to-archive threads-to-be-archived)))))))

    (catch Throwable t
      (print-stack-trace t)
      (log :debug "update-res-count-for-multiple-threads: Exception caught:" (str t))
      nil)))

(defn get-special-thread-list
  [refresh items context ie]
  ; (log :debug "get-special-thread-list:" refresh items)
  (try
    (increment-http-request-count)
    (.setPriority (java.lang.Thread/currentThread) web-sever-thread-priority)
    (log :info "Preparing thread list...")
    (let [start-time (System/nanoTime)
          _ (if (= refresh "1") (update-res-count-for-multiple-threads items true))
          board-info-map (create-board-info-map items)
          enable-table-sorter (and (not (= ie "1")) (<= (count items) table-sorter-threshold))
          items-in-html (remove nil? (cp/pmap
                                       number-of-threads-for-thread-list
                                       convert-special-thread-list-item-to-html
                                       items
                                       (range 1 (inc (count items)))
                                       (repeat context)
                                       (repeat board-info-map)
                                       (repeat enable-table-sorter)))
          sort-arrow (if enable-table-sorter [:span.sortArrow "&nbsp;"])
          result (do
                   (html
                     [:div#thread-list-fixed-table-container.fixed-table-container.sort-decoration
                      [:div.header-background]
                      [:div.fixed-table-container-inner.scrollbar
                       [:table#thread-list-table.tablesorter {:cellspacing 0}
                        [:thead
                         [:tr
                          [:th.first.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner  [:span"新着"] sort-arrow]]
                          [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner [:span "レス"] sort-arrow]]
                          [:th {:style "width: 47px"} [:div.th-inner [:span "No."] sort-arrow]]
                          [:th                                                 [:div.th-inner [:span "タイトル"] sort-arrow]]
                          [:th                       {:style "width: 80px"}    [:div.th-inner [:span "板"] sort-arrow]]
                          [:th.sortInitialOrder-desc {:style "width: 47px"} [:div.th-inner [:span "勢い"] sort-arrow]]
                          [:th.sortInitialOrder-desc {:style "width: 140px"} [:div.th-inner [:span "開始日時"] sort-arrow]]]]
                        [:tbody (if enable-table-sorter
                                  items-in-html
                                  (try
                                    ; _ (println (nth (nth (nth (nth items-in-html 0) 0) 2) 2))
                                    (sort #(let [left  (nth (nth (nth %1 0 nil) 2 nil) 2 nil)
                                                 right (nth (nth (nth %2 0 nil) 2 nil) 2 nil)]
                                             (cond (and (number? left) (number? right) (not (= left right))) (< right left)
                                                   :else 0))
                                          items-in-html)
                                    (catch Throwable t
                                      (print-stack-trace t)
                                      items-in-html)))]]]]
                     (if enable-table-sorter
                       [:script "$('#thread-list-table').tablesorter({sortList: [[0,1]]});"])))]
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
      (decrement-http-request-count)
      (log :info (str "    Total time: " (format "%.0f" (* (- (System/nanoTime) start-time) 0.000001)) "ms"))
      result)
    (catch Throwable t
      (print-stack-trace t)
      (.setPriority (java.lang.Thread/currentThread) java.lang.Thread/NORM_PRIORITY)
      (decrement-http-request-count)
      (html [:div.message-error-right-pane "スレッド一覧の読み込みに失敗しました。" [:br] (bbn-check)]))))

(defn api-get-favorite-thread-list
  [refresh
   search-text
   _ ; search-type
   ie]
  ; (log :debug "api-get-favorite-thread-list")
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (get-special-thread-list refresh
                             (db/get-favorite-threads)
                             {:regex-search-pattern (if (> (count search-text) 0)
                                                      (re-pattern search-text)
                                                      nil)}
                             ie)))

(defn api-get-recently-viewed-thread-list
  [refresh
   search-text
   _ ; search-type
   ie]
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (get-special-thread-list refresh
                             (db/get-recently-viewed-threads)
                             {:regex-search-pattern (if (> (count search-text) 0)
                                                      (re-pattern search-text)
                                                      nil)}
                             ie)))

(defn api-get-recently-posted-thread-list
  [refresh
   search-text
   _ ; search-type
   ie]
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (get-special-thread-list refresh
                             (db/get-recently-posted-threads)
                             {:regex-search-pattern (if (> (count search-text) 0)
                                                      (re-pattern search-text)
                                                      nil)}
                             ie)))

(defn api-get-dat-file-list
  [refresh
   search-text
   _ ; search-type
   ie]
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (get-special-thread-list refresh
                             (db/get-dat-file-list)
                             {:regex-search-pattern (if (> (count search-text) 0)
                                                      (re-pattern search-text)
                                                      nil)}
                             ie)))

(defn api-get-html-file-list
  [refresh
   search-text
   _ ; search-type
   ie]
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (get-special-thread-list refresh
                             (db/get-html-file-list)
                             {:regex-search-pattern (if (> (count search-text) 0)
                                                      (re-pattern search-text)
                                                      nil)}
                             ie)))

(defn count-new-posts-in-thread
  [item]
  ; (log :debug "count-new-posts-in-thread:" item)
  (let  [{:keys [service board thread-no]} item
         res-count (:res-count (db/get-thread-info service board thread-no))
         bookmark  (db/get-bookmark service board thread-no)]
    (if (and res-count
             bookmark
             (> bookmark 0)
             (> res-count bookmark))
      (- res-count bookmark)
      0)))

(defn count-new-posts-in-thread-list
  [thread-list refresh]
  ; (log :debug "count-new-posts-in-thread-list")
  (if refresh
    (update-res-count-for-multiple-threads thread-list true))
  (apply +
         (cp/pmap
           :builtin
           #(count-new-posts-in-thread %1)
           thread-list)))

(defn special-menu-item-with-thread-list
  [id text left-click right-click middle-click bubble-click thread-list bubbles refresh]
  (let [new-post-count (if bubbles (count-new-posts-in-thread-list thread-list refresh) nil)
        new-post-bubble-id (random-element-id)]
    (list
      [:div.bbs-menu-item {:id id}
       [:div {:style (str "float:left;" (if (and new-post-count (> new-post-count 0)) "font-weight:bold;" ""))} text]
       (if (or (not bubbles) (or (nil? new-post-count) (<= new-post-count 0)))
         ""
         [:div {:style "float:right;"}
          [:div {:id new-post-bubble-id
                 :class (str "bubble "
                             "new-posts "
                             (if (and new-post-count (> new-post-count 0)) "non-zero " ""))}
           new-post-count]])]
      (set-click-event-handler (str "#" id) left-click right-click middle-click)
      [:script
       "$('#" new-post-bubble-id "').click(function(e) {"
       "e.preventDefault();"
       "e.stopPropagation();"
       bubble-click
       "});"])))

(defn api-get-special-menu-content
  [bubbles refresh]
  ; (log :debug "api-get-special-menu-content:" bubbles refresh)
  (if (not (check-login))
    (html [:script "open('/login', '_self');"])
    (try
      (increment-http-request-count)
      (let [result (html
                     (map #(special-menu-item-with-thread-list
                             (nth %1 0)
                             (nth %1 1)
                             (nth %1 2)
                             (nth %1 3)
                             (nth %1 4)
                             (nth %1 5)
                             (nth %1 6)
                             (nth %1 7)
                             (nth %1 8))
                          (list (list "menu-item-favorite-threads"
                                      "お気にスレ"
                                      "loadFavoriteThreadList(false);"
                                      "displayFavoriteThreadListMenu(event);"
                                      "loadFavoriteThreadList(false, '', '', true);"
                                      "loadNewPostsInFavoriteThreads();"
                                      (db/get-favorite-threads)
                                      bubbles
                                      refresh)
                                (list "menu-item-recently-viewed-threads"
                                      "最近読んだスレ"
                                      "loadRecentlyViewedThreadList(false);"
                                      "displayRecentlyViewedThreadListMenu(event);"
                                      "loadRecentlyViewedThreadList(false, '', '', true);"
                                      "loadNewPostsInRecentlyViewedThreads();"
                                      (db/get-recently-viewed-threads)
                                      bubbles
                                      refresh)
                                (list "menu-item-recently-posted-threads"
                                      "書込履歴"
                                      "loadRecentlyPostedThreadList(false);"
                                      "displayRecentlyPostedThreadListMenu(event);"
                                      "loadRecentlyPostedThreadList(false, '', '', true);"
                                      "loadNewPostsInRecentlyPostedThreads();"
                                      (db/get-recently-posted-threads)
                                      bubbles
                                      refresh)
                                (list "menu-item-dat-files"
                                      "DATファイル一覧"
                                      "loadDatFileList(false);"
                                      "displayDatFileListMenu(event);"
                                      "loadDatFileList(false, '', '', true);"
                                      nil
                                      nil
                                      false
                                      false)
                                (list "menu-item-html-files"
                                      "HTMLファイル一覧"
                                      "loadHtmlFileList(false);"
                                      "displayHtmlFileListMenu(event);"
                                      "loadHtmlFileList(false, '', '', true);"
                                      nil
                                      nil
                                      false
                                      false)))

                     ;[:div.bbs-menu-item "スレの殿堂"]
                     ;[:div.bbs-menu-item "ログイン管理"]
                     ;[:div.bbs-menu-item "設定管理"]
                     ;[:div.bbs-menu-item "datのインポート"]
                     )]
        (decrement-http-request-count)
        result)
      (catch Throwable t
        (decrement-http-request-count)
        (print-stack-trace t)
        "特別メニューの読み込みに失敗しました。"))))

(defn api-add-favorite-thread
  [thread-url]
  ; (log :debug "api-add-favorite-thread")
  ; (log :debug thread-url thread-title)
  (if (not (check-login))
    nil
    (let
      [{:keys [service original-server board thread]} (split-thread-url thread-url)]
      ; (log :debug (split-thread-url thread-url))
      (db/add-favorite-thread {:user-id   (:id (session/get :user))
                               :service   service
                               :server    original-server
                               :board     board
                               :thread-no (db/prepare-thread-no thread)})
      "OK")))

(defn api-remove-favorite-thread
  [thread-url]
  ; (log :debug "api-remove-favorite-thread")
  ; (log :debug thread-url)
  (if (not (check-login))
    (ring.util.response/not-found "404 Not Found")
    (let
      [{:keys [service board thread]} (split-thread-url thread-url)]
      (if (db/get-favorite-thread service board (db/prepare-thread-no thread))
        (db/delete-favorite-thread service board (db/prepare-thread-no thread)))
      "OK")))

(defn api-is-favorite-thread
  [thread-url]
  ; (log :debug "api-is-favorite-thread")
  (if (not (check-login))
    (ring.util.response/not-found "404 Not Found")
    (let
      [{:keys [server board thread]} (split-thread-url thread-url)
       service (server-to-service server)]
      (if (db/get-favorite-thread service board thread)
        "true"
        "false"))))

(defn api-get-new-post-counts
  [thread-url-list]
  ; (log :debug "api-get-new-post-counts:" (count thread-url-list))
  (when (check-login)
    (let [threads (map #(split-thread-url %1) (clojure.string/split thread-url-list #"\n"))]
      (update-res-count-for-multiple-threads threads false)
      (html [:script
             (apply str (map
                          #(let [bookmark      (db/get-bookmark (:service %1) (:board %1) (:thread-no %1))
                                 res-count     (:res-count (db/get-thread-info (:service %1) (:board %1) (:thread-no %1)))
                                 new-thread?   (nil? bookmark)
                                 new-res?      (and bookmark (> bookmark 0) (> (- res-count bookmark) 0))
                                 new-res-count (cond
                                                 (nil? bookmark) "新"
                                                 new-res? (- res-count bookmark)
                                                 (= res-count bookmark) 0
                                                 :else "")
                                 new-post-count-id (str "new-post-count-" (clojure.string/replace (str (:service %1) "-" (:board %1) "-" (:thread-no %1)) #"[./]" "_"))
                                 post-count-id     (str "post-count-"     (clojure.string/replace (str (:service %1) "-" (:board %1) "-" (:thread-no %1)) #"[./]" "_"))]
                             (str (if (and bookmark (< 0 bookmark))
                                    (str "$('#" new-post-count-id "')"
                                         (if new-thread? ".addClass('new-thread')" ".removeClass('new-thread')")
                                         (if (and (not new-thread?) new-res?) ".addClass('non-zero')" ".removeClass('non-zero')")
                                         ".html('" new-res-count "');"))
                                  "$('#" post-count-id "').html('" res-count "');"))
                          threads))
             "$('#thread-list-table')"
             ".trigger('update')"
             ; ".trigger('appendCache')"
             ";"]))))

(defroutes thread-list-routes
  (GET "/api-get-thread-list"
       [board-url search-text search-type ie log-list]
       (api-get-thread-list (trim board-url) search-text search-type ie log-list))

  (GET "/api-get-similar-thread-list"
       [thread-url ie]
       (api-get-similar-thread-list (trim thread-url) ie))

  (GET "/api-get-special-menu-content"
       [bubbles refresh]
       (api-get-special-menu-content (= bubbles "1") (= refresh "1")))
  (GET "/api-get-favorite-thread-list"
       [refresh search-text search-type ie]
       (api-get-favorite-thread-list refresh search-text search-type ie))
  (GET "/api-is-favorite-thread"
       [thread-url]
       (api-is-favorite-thread (trim thread-url)))
  (GET "/api-add-favorite-thread"
       [thread-url]
       (api-add-favorite-thread (trim thread-url)))
  (GET "/api-remove-favorite-thread"
       [thread-url]
       (api-remove-favorite-thread (trim thread-url)))
  (GET "/api-get-recently-viewed-thread-list"
       [refresh search-text search-type ie]
       (api-get-recently-viewed-thread-list refresh search-text search-type ie))
  (GET "/api-get-recently-posted-thread-list"
       [refresh search-text search-type ie]
       (api-get-recently-posted-thread-list refresh search-text search-type ie))
  (GET "/api-get-dat-file-list"
       [refresh search-text search-type ie]
       (api-get-dat-file-list refresh search-text search-type ie))
  (GET "/api-get-html-file-list"
       [refresh search-text search-type ie]
       (api-get-html-file-list refresh search-text search-type ie))

  (POST "/api-get-new-post-counts"
        [thread-url-list]
        (api-get-new-post-counts thread-url-list)))

