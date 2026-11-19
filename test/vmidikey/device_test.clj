(ns vmidikey.device-test
  "Java の仮想 MIDI 機器 (target/vmidikey-device.jar) の結合テスト: 機器の一覧に出る、開いて送信口をつなぐと
   UDP で送ったメッセージが届く。先に bb device で jar を作る"
  (:require [clojure.test :refer [deftest is testing]]
            [vmidikey.out :as out])
  (:import (javax.sound.midi MidiSystem MidiDevice Receiver Sequencer Synthesizer)))

(deftest device
  (System/setProperty "vmidikey.port" "47199")               ; ほかで動いているキーボードとぶつからないように
  (let [info (first (filter #(= "VMidiKey" (.getName %)) (MidiSystem/getMidiDeviceInfo)))
        ^MidiDevice dev (some-> info MidiSystem/getMidiDevice)]
    (testing "機器の一覧に出る (STED の midi-inputs と同じ条件: 送信口があり、シーケンサ・音源でない)"
      (is (some? dev))
      (is (and (not= 0 (.getMaxTransmitters dev)) (not (instance? Sequencer dev)) (not (instance? Synthesizer dev)))))
    (when dev
      (.open dev)
      (try
        (let [got (atom []) done (promise)]
          (.setReceiver (.getTransmitter dev)
                        (reify Receiver
                          (send [_ m _] (swap! got conj (vec (map #(bit-and % 0xFF) (.getMessage m))))
                            (when (= 2 (count @got)) (deliver done true)))
                          (close [_])))
          (out/send-udp! [0x90 60 100])
          (out/send-udp! [0xF0 0x41 0x10 0x42 0x12 0xF7])
          (deref done 3000 false)
          (testing "UDP で送ったノートとエクスクルーシブが届く"
            (is (= [[0x90 60 100] [0xF0 0x41 0x10 0x42 0x12 0xF7]] @got))))
        (finally (.close dev)))
      (is (not (.isOpen dev))))))
