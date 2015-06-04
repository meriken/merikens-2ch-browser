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



(ns merikens-2ch-browser.util
  (:require [ajax.core :refer [GET POST]]
            [hipo.core :as hipo]
            [jayq.core :refer [$ width inner-width outer-width height inner-height outer-height attr css append ajax]]
            [jayq.util :refer [log wait]])
  (:require-macros [jayq.macros :refer [ready let-ajax]]))



;;;;;;;;;;;;;;;;;;;;;;
; UTILITY FUNCTIOINS ;
;;;;;;;;;;;;;;;;;;;;;;

(defn random-string
  [length]
  (let [salad "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"]
    (apply str (for [n (range length)] (get salad (rand-int (count salad)))))))

(defn random-element-id
  []
  (random-string 6))

(defn px->int
  [value]
  (int (clojure.string/replace value "px" "")))

(defn calculate-scrollbar-width
  []
  (let [inner (.createElement js/document "p")
        outer (.createElement js/document "div")]

    (set! (.-width  (.-style inner)) "100%")
    (set! (.-height (.-style inner)) "200px")
    (set! (.-innerHTML inner) "test")

    (set! (.-className           outer ) "scrollbar")
    (set! (.-position   (.-style outer)) "absolute")
    (set! (.-top        (.-style outer)) "0px")
    (set! (.-left       (.-style outer)) "-200px")
    ; (set! (.-visibility (.-style outer)) "hidden")
    (set! (.-width      (.-style outer)) "200px")
    (set! (.-height     (.-style outer)) "150px")
    (set! (.-overflow   (.-style outer)) "hidden")

    (.appendChild outer inner)
    (.appendChild (.-body js/document) outer)

    (let [w1 (.-offsetWidth inner)
          _  (set! (.-overflow (.-style outer)) "scroll")
          w2 (.-offsetWidth inner)
          w2 (if (= w1 w2) (.-clientWidth outer) w2)]
      (.removeChild (.-body js/document) outer)
      (inc (- w1 w2)))))
