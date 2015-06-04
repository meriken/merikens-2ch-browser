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



(ns merikens-2ch-browser.css
  (:require [garden.core :refer [css]]))
(ns-unmap 'garden.units 'px+)
(require '[merikens-2ch-browser.cursive :refer :all])


(defn css-background
  [selector color]
  (let [selector-vector (if (vector? selector) selector (vector selector))]
    (list (conj selector-vector {:background color}))))

(defn css-background-with-gradation
  [selector color1 color2]
  (let [selector-vector (if (vector? selector) selector (vector selector))]
    (list (conj selector-vector {:background color1})
          (conj selector-vector {:background (str "-moz-linear-gradient(top,  " color1 " 0%, " color2 " 100%)")})
          (conj selector-vector {:background (str "-webkit-gradient(linear, left top, left bottom, color-stop(0%," color1 "), color-stop(100%," color2 "))")})
          (conj selector-vector {:background (str "-webkit-linear-gradient(top,  " color1 " 0%," color2 " 100%)")})
          (conj selector-vector {:background (str "-o-linear-gradient(top,  " color1 " 0%," color2 " 100%)")})
          (conj selector-vector {:background (str "-ms-linear-gradient(top,  " color1 " 0%," color2 " 100%)")})
          (conj selector-vector {:background (str "linear-gradient(to bottom,  " color1 " 0%," color2 " 100%)")})
          (conj selector-vector {:filter     (str "progid:DXImageTransform.Microsoft.gradient( startColorstr='" color1 "', endColorstr='" color2 "',GradientType=0 )")}))))

(defn css-border
  [selector & rest]
  (let [border "2px solid #000000"
        entries {}
        entries (if (some #(or (= %1 :left  ) (= %1 :vertical  ) (= %1 :all)) rest) (assoc entries :border-left   border) entries)
        entries (if (some #(or (= %1 :right ) (= %1 :vertical  ) (= %1 :all)) rest) (assoc entries :border-right  border) entries)
        entries (if (some #(or (= %1 :top   ) (= %1 :horizontal) (= %1 :all)) rest) (assoc entries :border-top    border) entries)
        entries (if (some #(or (= %1 :bottom) (= %1 :horizontal) (= %1 :all)) rest) (assoc entries :border-bottom border) entries)]
    (conj
      (if (vector? selector) selector (vector selector))
      entries)))

(defn css-thin-border
  [selector & rest]
  (let [border "1px solid #000000"
        entries {}
        entries (if (some #(or (= %1 :left  ) (= %1 :vertical  ) (= %1 :all)) rest) (assoc entries :border-left   border) entries)
        entries (if (some #(or (= %1 :right ) (= %1 :vertical  ) (= %1 :all)) rest) (assoc entries :border-right  border) entries)
        entries (if (some #(or (= %1 :top   ) (= %1 :horizontal) (= %1 :all)) rest) (assoc entries :border-top    border) entries)
        entries (if (some #(or (= %1 :bottom) (= %1 :horizontal) (= %1 :all)) rest) (assoc entries :border-bottom border) entries)]
    (conj
      (if (vector? selector) selector (vector selector))
      entries)))

(defn css-no-border
  [selector & rest]
  (let [border "none"
        entries {}
        entries (if (some #(or (= %1 :left  ) (= %1 :vertical  ) (= %1 :all)) rest) (assoc entries :border-left   border) entries)
        entries (if (some #(or (= %1 :right ) (= %1 :vertical  ) (= %1 :all)) rest) (assoc entries :border-right  border) entries)
        entries (if (some #(or (= %1 :top   ) (= %1 :horizontal) (= %1 :all)) rest) (assoc entries :border-top    border) entries)
        entries (if (some #(or (= %1 :bottom) (= %1 :horizontal) (= %1 :all)) rest) (assoc entries :border-bottom border) entries)]
    (conj
      (if (vector? selector) selector (vector selector))
      entries)))

(defn css-border-radius
  [selector & rest]
  (let [radius (if (integer? (last rest)) (str (last rest) "px") "5px")
        value  (str (if (some #(or (= %1 :left ) (= %1 :left-top    ) (= %1 :top))    rest) radius "0") " "
                    (if (some #(or (= %1 :right) (= %1 :right-top   ) (= %1 :top))    rest) radius "0") " "
                    (if (some #(or (= %1 :left ) (= %1 :left-bottom ) (= %1 :bottom)) rest) radius "0") " "
                    (if (some #(or (= %1 :right) (= %1 :right-bottom) (= %1 :bottom)) rest) radius "0"))]
    (conj
      (if (vector? selector) selector (vector selector))
      {:-webkit-border-radius value
       :-moz-border-radius    value
       :border-radius         value})))

(defn css-ui-font
  [selector]
  (conj
    (if (vector? selector) selector (vector selector))
    {:font-size "14px"
     :line-height "21px"
     :text-decoration "none"
     :font-weight "normal"
     :white-space "nowrap"
     :text-overflow "ellipsis"}))

(defn css-ui-font-bold
  [selector]
  (list (css-ui-font selector)
        (conj
          (if (vector? selector) selector (vector selector))
          {:font-weight "bold"})))



;;;;;;;;;;;;
; SETTINGS ;
;;;;;;;;;;;;

(defn css-settings
  []
  (css
    ; fonts
    (css-ui-font-bold [:#title :.heading])
    (css-ui-font [:#submit-button :.entry-item])

    ; backgrounds
    (css-background [:#entries] "#dfe8f7")
    (css-background [:#headings :.heading] "#C7D7F1")
    (css-background [:.entry-item] "#eff4fc")
    (css-background [:.delete-button:hover] "#A8C0EA")
    (css-background-with-gradation [:#title :#submit-button] "#dfe8f7" "#a5c3ef")
    [:#title :#headings :#entries :#submit-button
     {:-moz-box-shadow    "5px 5px 5px #86A8DF"
      :-webkit-box-shadow "5px 5px 5px #86A8DF"
      :box-shadow         "5px 5px 5px #86A8DF"}]

    ; borders
    (css-border [:#title :#headings :#entries :#submit-button] :vertical)
    (css-border :#title :top)
    (css-border :#submit-button :bottom)
    (css-border :.entry-item:last-child :right)
    (css-thin-border :#submit-button :top)
    (css-thin-border [:#title :#entries :#headings :.entry] :bottom)
    (css-thin-border [:.heading :.entry-item] :right)
    (css-no-border :.heading:last-child :right)
    (css-no-border (keyword "input[type=text]") :left :horizontal)
    (css-border-radius :#title :top)
    (css-border-radius :#submit-button :bottom)
    (css-border-radius (keyword "input[type=text]") :all 0)

    [:#title
     {:margin-top "12px"
      :padding "5px 12px"
      :text-align "center"}]

    [:#entries
     {:height    (px 300)
      :overflow-x "hidden"
      :overflow-y "scroll"}]

    [:#headings
     :.entry
     {:height   (px 25)
      :overflow "hidden"}]
    [:.entry {:width "8000px"}]

    [:.heading
     :.entry-item
     (keyword "input[type=text].entry-item")
     {:display "inline-block"
      :padding "2px 8px"
      :overflow "hidden"
      :float "left"}]

    [:.delete-button :.delete-button:hover
     {:padding 0
      :width "24px"
      :height "25px"
      :background-image "url(\"img/delete-entry-14x14.png\")"
      :background-repeat "no-repeat"
      :background-position "center"}]))



;;;;;;;;;;;;;;;;;
; ABORN FILTERS ;
;;;;;;;;;;;;;;;;;

(def aborn-filters-inner-window-width 700)

(defn css-aborn-filters
  []
  (str
    (css-settings)
    (css
      [:#title
       {:width     (px+ aborn-filters-inner-window-width -28)
        :min-width (px+ aborn-filters-inner-window-width -28)}]

      [:#entries
       {:width     (px+ aborn-filters-inner-window-width -4)
        :min-width (px+ aborn-filters-inner-window-width -4)}]

      [:#headings
       {:width     (px+ aborn-filters-inner-window-width -4)
        :min-width (px+ aborn-filters-inner-window-width -4)}]

      [:#submit-button
       {:width     (px+ aborn-filters-inner-window-width 0)
        :min-width (px+ aborn-filters-inner-window-width 0)}]

      [:.pattern    {:width "20px"}]
      [:.board      {:width "100px"}]
      [:.thread-title-pattern {:width "200px"}])))



;;;;;;;;;;;;;;;
; ABORN POSTS ;
;;;;;;;;;;;;;;;

(def aborn-posts-inner-window-width 700)

(defn css-aborn-posts
  []
  (str
    (css-settings)
    (css
      [:#title
       {:width     (px+ aborn-posts-inner-window-width -28)
        :min-width (px+ aborn-posts-inner-window-width -28)}]

      [:#entries
       {:width     (px+ aborn-posts-inner-window-width -4)
        :min-width (px+ aborn-posts-inner-window-width -4)}]

      [:#headings
       {:width     (px+ aborn-posts-inner-window-width -4)
        :min-width (px+ aborn-posts-inner-window-width -4)}]

      [:#submit-button
       {:width     (px+ aborn-posts-inner-window-width 0)
        :min-width (px+ aborn-posts-inner-window-width 0)}]

      [:.service    {:width "100px"}]
      [:.board      {:width "100px"}]
      [:.thread-no  {:width "100px"}]
      [:.post-index {:width "40px"}]
      [:.etc        {:width "20px"}])))



;;;;;;;;;;;;;
; ABORN IDS ;
;;;;;;;;;;;;;

(def aborn-ids-inner-window-width 200)

(defn css-aborn-ids
  []
  (str
    (css-settings)
    (css
      [:#title
       {:width     (px+ aborn-ids-inner-window-width -28)
        :min-width (px+ aborn-ids-inner-window-width -28)}]

      [:#entries
       {:width     (px+ aborn-ids-inner-window-width -4)
        :min-width (px+ aborn-ids-inner-window-width -4)}]

      [:#headings
       {:width     (px+ aborn-ids-inner-window-width -4)
        :min-width (px+ aborn-ids-inner-window-width -4)}]

      [:#submit-button
       {:width     (px+ aborn-ids-inner-window-width 0)
        :min-width (px+ aborn-ids-inner-window-width 0)}]

      [:.pattern   {:width "20px"}])))
