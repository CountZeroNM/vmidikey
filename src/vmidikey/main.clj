(ns vmidikey.main
  "起動: clojure -M:run (bb start)、配布用の jar は java -jar vmidikey.jar (ダブルクリックでも開く)"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [vmidikey.gui :as gui]
            [vmidikey.out :as out])
  (:gen-class))

(defn version
  "版の番号 (jar を作るときに vmidikey/version.txt に書く。開発中は dev)"
  []
  (or (some-> (io/resource "vmidikey/version.txt") slurp str/trim) "dev"))

(defn -main [& args]
  (cond
    (some #{"--version" "-v"} args) (println "vmidikey" (version))
    (some #{"--help" "-h"} args)
    (println (str "VMidiKey " (version) " — 文字キーと画面の鍵盤で弾く仮想 MIDI キーボード\n"
                  "  java -jar vmidikey.jar     キーボードを開く (UDP 127.0.0.1:" (out/port) " へ送る)\n"
                  "受け側 (polysted など) では、MIDI 入力の機器「VMidiKey」として見えます。\n"
                  "https://github.com/CountZeroNM/vmidikey"))
    (java.awt.GraphicsEnvironment/isHeadless)
    (do (println "画面 (ウィンドウ) が使えない環境です。") (System/exit 1))
    :else
    (do (println (str "VMidiKey " (version) ": UDP 127.0.0.1:" (out/port) " へ送ります (受け側は VMidiKey の機器)"))
        (let [{:keys [latency-ms tuned?]} (try (out/warm-up!) (catch Throwable _ nil))]
          (when latency-ms
            (println (str "内蔵音源の遅れ: " latency-ms "ms"
                          (when-not tuned? " (指定できない起動のしかたなので既定のまま。java -jar か clojure -M:run で起動してください)")))))
        (gui/run! {:exit-on-close? true}))))
