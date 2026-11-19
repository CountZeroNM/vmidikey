(ns vmidikey.piano-test
  (:require [clojure.test :refer [deftest is]]
            [vmidikey.piano :as piano]))

(deftest layout-and-hit
  (is (= [60 62 64 65 67 69 71 72] (piano/white-keys 60 72)))
  (let [lay (piano/layout 60 71 700 100)
        c# (first (filter #(= 61 (:note %)) (:keys lay)))]
    (is (= 100.0 (:white-w lay)))
    (is (= [70.0 60.0 62.0] [(:x c#) (:w c#) (:h c#)]))
    (is (= 61 (piano/hit lay 75 10)))          ; 黒鍵が上
    (is (= 60 (piano/hit lay 75 80)))          ; 黒鍵より下は白鍵
    (is (= 71 (piano/hit lay 650 10)))
    (is (nil? (piano/hit lay 750 10)))))

(deftest visible-range
  (is (= [36 96] (piano/visible-range 48)))
  (is (= [0 48] (piano/visible-range 0)))
  (is (= [96 127] (piano/visible-range 108))))
