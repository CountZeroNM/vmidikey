(ns vmidikey.keys-test
  (:require [clojure.test :refer [deftest is testing]]
            [vmidikey.keys :as k]))

(def Z 90) (def Q 81) (def P 80)

(deftest note-on-off
  (testing "Z は下の段の C (base 48 = C3)、押したままのオートリピートは無視、離すと止める"
    (let [[s1 m1] (k/key-down k/initial Z)
          [s2 m2] (k/key-down s1 Z)
          [s3 m3] (k/key-up s2 Z)]
      (is (= [[0x90 48 100]] m1))
      (is (= [] m2))
      (is (= [[0x80 48 0]] m3))
      (is (= {} (:held s3)))))
  (testing "オクターブを変えた後に離しても、押したときの音を止める"
    (let [[s1] (k/key-down k/initial Z)
          s2 (k/octave s1 1)
          [_ m] (k/key-up s2 Z)]
      (is (= [[0x80 48 0]] m))))
  (testing "上の段の Q は 1 オクターブ上、割り当ての無いキーと 127 を超える音は何もしない"
    (is (= [[0x90 72 100]] (second (k/key-down (k/octave k/initial 1) Q))))
    (is (= [] (second (k/key-down k/initial 65))))
    (is (= [] (second (k/key-down (assoc k/initial :base 108) P))))))

(deftest settings
  (is (= 127 (:vel (k/velocity (assoc k/initial :vel 120) 8))))
  (is (= 1 (:vel (k/velocity (assoc k/initial :vel 5) -8))))
  (is (= 108 (:base (k/octave k/initial 9))))
  (is (= [[0xB0 64 127]] (second (k/sustain k/initial true))))
  (is (= [] (second (k/sustain (assoc k/initial :sustain? true) true))))
  (is (= [[0xC0 127]] (second (k/program k/initial 130))))
  (testing "すべて止める: 押したままの音・ホールド・オールノートオフ。チャンネルを変えるときも先に止める"
    (let [[s1] (k/key-down k/initial Z)
          [s2] (k/sustain s1 true)]
      (is (= [[0x80 48 0] [0xB0 64 0] [0xB0 123 0]] (second (k/all-off s2))))
      (is (= 2 (:ch (first (k/channel s2 2)))))
      (is (= [[0x80 48 0] [0xB0 64 0] [0xB0 123 0]] (second (k/channel s2 2)))))))

(deftest names-and-labels
  (is (= ["C4" "C#4" "C-1" "G9"] (map k/note-name [60 61 0 127])))
  (is (= [false true false true false false true] (map k/black? [60 61 62 63 64 65 66])))
  (is (= ["Z"] (k/key-labels 0)))
  (is (= ["," "Q"] (k/key-labels 12)))
  (is (= ["P"] (k/key-labels 28))))
