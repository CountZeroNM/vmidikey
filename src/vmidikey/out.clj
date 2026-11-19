(ns vmidikey.out
  "MIDI の送り先 (副作用)。
   - UDP: 127.0.0.1 の vmidikey.port / VMIDIKEY_PORT (既定 47100) へ、メッセージ 1 つを 1 つのデータグラムで送る。
     受け側は Java の仮想 MIDI 機器 VMidiKey (target/vmidikey-device.jar)。受け側がいなくても送れる (届かないだけ)。
   - モニター: この JVM の内蔵音源 (Gervill) で鳴らす (自分の耳で確かめる用)。
     内蔵音源の既定の遅れ (音のバッファ) は 120ms あり、弾いてから鳴るまでが遅れて聞こえるので、
     vmidikey.latency / VMIDIKEY_LATENCY (ミリ秒、既定 40) で開く。遅れの指定は Java の内部の API
     (com.sun.media.sound.AudioSynthesizer の open) にしか無いので、JVM の起動の設定
     --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED (jar では manifest の Add-Exports) が要る。
     使えなければ既定 (120ms) で開く。Gervill の下限はおよそ 35ms (512 フレームの 3 倍)。"
  (:import (java.net DatagramSocket DatagramPacket InetAddress)
           (javax.sound.midi MidiSystem ShortMessage Receiver Synthesizer)
           (javax.sound.sampled SourceDataLine)))

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

(def default-latency-ms 40)

(defn latency-ms
  "内蔵音源の遅れ (ミリ秒)。システムプロパティ vmidikey.latency か環境変数 VMIDIKEY_LATENCY、無ければ 40"
  []
  (or (some-> (or (System/getProperty "vmidikey.latency") (System/getenv "VMIDIKEY_LATENCY")) parse-long)
      default-latency-ms))

(defn open-synth!
  "内蔵音源を、遅れ ms ミリ秒で開く。=> {:latency-ms 実際の遅れ :tuned? 指定が効いたか}"
  [^Synthesizer s ms]
  (let [tuned? (try
                 (let [m (.getMethod (Class/forName "com.sun.media.sound.AudioSynthesizer") "open"
                                     (into-array Class [SourceDataLine java.util.Map]))]
                   (.invoke m s (object-array [nil {"latency" (long (* 1000 ms))}]))
                   true)
                 (catch Throwable _
                   (when-not (.isOpen s) (.open s))
                   false))]
    {:latency-ms (quot (.getLatency s) 1000) :tuned? tuned?}))

(defonce ^:private synth (atom nil))
(defonce synth-info (atom nil))

(defn- synth-receiver ^Receiver []
  (or @synth
      (let [s (MidiSystem/getSynthesizer)]
        (reset! synth-info (open-synth! s (latency-ms)))
        (reset! synth (.getReceiver s)))))

(defn warm-up!
  "内蔵音源を先に開いておく (最初の音の遅れをなくす)。=> synth-info"
  []
  (synth-receiver)
  @synth-info)

(defn send-monitor! "メッセージを内蔵音源で鳴らす" [msg]
  (let [[s d1 d2] msg]
    (.send (synth-receiver)
           (if (#{0xC0 0xD0} (bit-and s 0xF0)) (ShortMessage. (int s) (int d1) 0) (ShortMessage. (int s) (int (or d1 0)) (int (or d2 0))))
           -1)))

(defn close! []
  (when-let [r @synth] (.close ^Receiver r) (reset! synth nil)))
