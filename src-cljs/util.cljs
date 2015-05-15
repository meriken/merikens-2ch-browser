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
