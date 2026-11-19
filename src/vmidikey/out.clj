(ns vmidikey.out
  "MIDI の送り先 (副作用)。
   - UDP: 127.0.0.1 の vmidikey.port / VMIDIKEY_PORT (既定 47100) へ、メッセージ 1 つを 1 つのデータグラムで送る。
     受け側は Java の仮想 MIDI 機器 VMidiKey (target/vmidikey-device.jar)。受け側がいなくても送れる (届かないだけ)。
   - モニター: この JVM の内蔵音源 (Gervill) で鳴らす (自分の耳で確かめる用)。"
  (:import (java.net DatagramSocket DatagramPacket InetAddress)
           (javax.sound.midi MidiSystem ShortMessage Receiver)))

(def default-port 47100)

(defn port []
  (or (some-> (or (System/getProperty "vmidikey.port") (System/getenv "VMIDIKEY_PORT")) parse-long)
      default-port))

(defn ->bytes "メッセージ [ステータス データ…] → バイト配列" ^bytes [msg]
  (byte-array (map unchecked-byte msg)))

(defonce ^:private udp (delay (DatagramSocket.)))

(defn send-udp! "メッセージを UDP で送る" [msg]
  (let [b (->bytes msg)]
    (.send ^DatagramSocket @udp (DatagramPacket. b (alength b) (InetAddress/getLoopbackAddress) (int (port))))))

(defonce ^:private synth (atom nil))

(defn- synth-receiver ^Receiver []
  (or @synth
      (let [s (MidiSystem/getSynthesizer)]
        (.open s)
        (reset! synth (.getReceiver s)))))

(defn send-monitor! "メッセージを内蔵音源で鳴らす" [msg]
  (let [[s d1 d2] msg]
    (.send (synth-receiver)
           (if (#{0xC0 0xD0} (bit-and s 0xF0)) (ShortMessage. (int s) (int d1) 0) (ShortMessage. (int s) (int (or d1 0)) (int (or d2 0))))
           -1)))

(defn close! []
  (when-let [r @synth] (.close ^Receiver r) (reset! synth nil)))
