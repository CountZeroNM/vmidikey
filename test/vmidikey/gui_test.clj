(ns vmidikey.gui-test
  (:require [clojure.test :refer [deftest is testing]]
            [vmidikey.gui :as gui])
  (:import (java.awt Color)))

(defn- run-events [evs]
  (reduce (fn [[st out] ev] (let [[st2 m] (gui/handle st ev)] [st2 (into out m)])) [gui/initial []] evs))

(deftest events
  (testing "文字キー・マウス (押す・なぞる・離す)"
    (is (= [[0x90 48 100] [0x90 60 100] [0x80 60 0] [0x90 62 100] [0x80 62 0] [0x80 48 0]]
           (second (run-events [[:down 90 false] [:mouse-down 60] [:mouse-move 62] [:mouse-up] [:up 90]])))))
  (testing "↑ は +8、Shift+↑ は +1、Space はホールド、PgUp は音色"
    (let [[st msgs] (run-events [[:down 38 false] [:down 38 true] [:down 32 false] [:up 32] [:down 33 false]])]
      (is (= 109 (:vel (:kb st))))
      (is (= [[0xB0 64 127] [0xB0 64 0] [0xC0 1]] msgs))))
  (testing "Esc はマウスの音も止める、チャンネルの切り替え"
    (let [[st msgs] (run-events [[:mouse-down 64] [:down 27 false] [:channel 3] [:down 90 false]])]
      (is (= [[0x90 64 100] [0xB0 123 0] [0x80 64 0] [0xB0 123 0] [0x93 48 100]] msgs))
      (is (= #{48} (gui/sounding st))))))

(deftest drawing
  (testing "画像に描ける。鳴らしている鍵 (Z = 48) は橙色"
    (let [[st] (gui/handle gui/initial [:down 90 false])
          img (gui/render-image st 1008 200)]
      (is (= (.getRGB (Color. 0xFF 0xB0 0x40)) (.getRGB img 210 180)))
      (is (not= (.getRGB (Color. 0xFF 0xB0 0x40)) (.getRGB img 240 180))))))
