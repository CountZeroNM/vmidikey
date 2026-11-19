(ns vmidikey.gui
  "仮想 MIDI キーボードのウィンドウ (Swing)。
   - 純粋部分: handle (状態 + イベント → [状態 メッセージ])、paint! (状態を Graphics2D に描く)、render-image (画像に描く)。
   - 副作用: run! (ウィンドウ)。メッセージは emit (既定は UDP と内蔵音源) へ。
   操作: 文字キー (下の段 Z〜/、上の段 Q〜P) と画面の鍵盤のクリック・ドラッグで弾く。
         ← → オクターブ、↑ ↓ ベロシティ (±8、Shift で ±1)、PgUp PgDn 音色、Space ホールド、Esc すべての音を止める。"
  (:refer-clojure :exclude [run!])
  (:require [vmidikey.keys :as k]
            [vmidikey.piano :as piano]
            [vmidikey.out :as out])
  (:import (javax.swing JFrame JPanel JCheckBox JComboBox JLabel SwingUtilities Timer WindowConstants)
           (java.awt BorderLayout FlowLayout Color Font Graphics2D RenderingHints Dimension KeyboardFocusManager
                     KeyEventDispatcher)
           (java.awt.event KeyEvent MouseAdapter MouseEvent ActionListener WindowAdapter)
           (java.awt.image BufferedImage)))

;; ---- 状態とイベント (純粋) ----
;; 状態: {:kb キーボードの状態 (vmidikey.keys) :mouse マウスで鳴らしている音 :udp? :monitor?}
(def initial {:kb k/initial :mouse nil :udp? true :monitor? true})

(def ^:private vk-left 37) (def ^:private vk-right 39) (def ^:private vk-up 38) (def ^:private vk-down 40)
(def ^:private vk-pgup 33) (def ^:private vk-pgdn 34) (def ^:private vk-space 32) (def ^:private vk-esc 27)

(defn- on-kb [st f & args]
  (let [[kb msgs] (apply f (:kb st) args)] [(assoc st :kb kb) msgs]))

(defn handle
  "イベント → [新しい状態 送るメッセージ]。イベント:
   [:down キーコード shift?] [:up キーコード] [:mouse-down 音] [:mouse-move 音か nil] [:mouse-up] [:channel 0〜15]"
  [st [kind a b]]
  (case kind
    :down (cond
            (= a vk-left)  [(update st :kb k/octave -1) []]
            (= a vk-right) [(update st :kb k/octave 1) []]
            (= a vk-up)    [(update st :kb k/velocity (if b 1 8)) []]
            (= a vk-down)  [(update st :kb k/velocity (if b -1 -8)) []]
            (= a vk-pgup)  (on-kb st k/program (inc (:program (:kb st))))
            (= a vk-pgdn)  (on-kb st k/program (dec (:program (:kb st))))
            (= a vk-space) (on-kb st k/sustain true)
            (= a vk-esc)   (let [[st msgs] (on-kb st k/all-off)
                                 m (:mouse st)]
                             [(assoc st :mouse nil) (cond-> msgs m (conj [(bit-or 0x80 (:ch (:kb st))) m 0]))])
            :else (on-kb st k/key-down a))
    :up (if (= a vk-space) (on-kb st k/sustain false) (on-kb st k/key-up a))
    :mouse-down (let [ch (:ch (:kb st))]
                  [(assoc st :mouse a) [[(bit-or 0x90 ch) a (:vel (:kb st))]]])
    :mouse-move (let [ch (:ch (:kb st)) m (:mouse st)]
                  (if (or (nil? m) (= m a) (nil? a))
                    [st []]
                    [(assoc st :mouse a) [[(bit-or 0x80 ch) m 0] [(bit-or 0x90 ch) a (:vel (:kb st))]]]))
    :mouse-up (if-let [m (:mouse st)]
                [(assoc st :mouse nil) [[(bit-or 0x80 (:ch (:kb st))) m 0]]]
                [st []])
    :channel (on-kb st k/channel a)
    [st []]))

(defn sounding "鳴らしている音 (文字キーとマウス)" [st]
  (cond-> (set (vals (:held (:kb st)))) (:mouse st) (conj (:mouse st))))

;; ---- 描画 ----
(def ^:private bg (Color. 0x1E 0x22 0x28))
(def ^:private on-color (Color. 0xFF 0xB0 0x40))

(defn status-text [st]
  (let [{:keys [base vel ch program sustain?]} (:kb st)]
    (str "下の段 Z = " (k/note-name base) "   上の段 Q = " (k/note-name (+ base 12))
         "   VEL " vel "   CH " (inc ch) "   音色 " program (if sustain? "   HOLD" ""))))

(defn paint!
  "鍵盤を描く (幅 w・高さ h)。=> 鍵盤の配置 (マウスの当たり判定に使う)"
  [^Graphics2D g st w h]
  (.setRenderingHint g RenderingHints/KEY_ANTIALIASING RenderingHints/VALUE_ANTIALIAS_ON)
  (.setRenderingHint g RenderingHints/KEY_TEXT_ANTIALIASING RenderingHints/VALUE_TEXT_ANTIALIAS_ON)
  (.setColor g bg) (.fillRect g 0 0 w h)
  (let [base (:base (:kb st))
        [lo hi] (piano/visible-range base)
        lay (piano/layout lo hi (- w 8) (- h 8))
        on (sounding st)
        font (.getFont g)]
    (.translate g 4 4)
    (doseq [{:keys [note x y w h black?]} (:keys lay)
            :let [x (int x) y (int y) w (int w) h (int h)
                  off (- note base) labels (k/key-labels off)]]
      (.setColor g (cond (on note) on-color black? (Color. 0x20 0x20 0x24) :else (Color. 0xF4 0xF4 0xF0)))
      (.fillRect g x y w h)
      (.setColor g (Color. 0x50 0x50 0x58))
      (.drawRect g x y w h)
      ;; 文字キー (届く範囲だけ) と C の音名
      (.setFont g (.deriveFont font (float (max 9 (min 13 (* 0.45 w))))))
      (.setColor g (if (and black? (not (on note))) (Color. 0xE0 0xE0 0xE0) (Color. 0x30 0x30 0x38)))
      (doseq [[i l] (map-indexed vector labels)]
        (.drawString g (str l) (int (+ x (/ w 2) -4)) (int (- (+ y h) 22 (* i 14)))))
      (when (zero? (mod note 12))
        (.setColor g (Color. 0x80 0x80 0x88))
        (.drawString g (k/note-name note) (int (+ x 3)) (int (+ y h -6)))))
    (.setFont g font)
    (.translate g -4 -4)
    lay))

(defn render-image "鍵盤を画像に描く (テスト用)" ^BufferedImage [st w h]
  (let [img (BufferedImage. w h BufferedImage/TYPE_INT_RGB) g (.createGraphics img)]
    (paint! g st w h) (.dispose g) img))

;; ---- ウィンドウ (副作用) ----
(defonce state (atom initial))

(defn default-emit "メッセージを送る (UDP と内蔵音源、状態の切り替えに従う)" [st msgs]
  (doseq [m msgs]
    (when (:udp? st) (try (out/send-udp! m) (catch Exception _ nil)))
    (when (:monitor? st) (try (out/send-monitor! m) (catch Exception _ nil)))))

(defn run!
  "ウィンドウを開く。opts: {:emit (fn [状態 メッセージの列]) :exit-on-close? true}"
  ([] (run! {}))
  ([{:keys [emit exit-on-close?] :or {emit default-emit}}]
   (reset! state initial)
   (let [result (promise)]
     (SwingUtilities/invokeLater
      (fn []
        (let [frame (JFrame. "VMidiKey — 仮想 MIDI キーボード")
              lay (atom nil)
              status (JLabel. (status-text @state))
              piano (proxy [JPanel] []
                      (paintComponent [^Graphics2D g]
                        (proxy-super paintComponent g)
                        (reset! lay (paint! g @state (.getWidth this) (.getHeight this)))))
              act! (fn [ev]
                     (let [[st msgs] (handle @state ev)]
                       (reset! state st)
                       (emit st msgs)
                       (.setText status (status-text st))
                       (.repaint piano)))
              ;; Linux のオートリピートは「離した → 押した」が続けて届くので、離したことは少し待ってから処理する
              pending (atom {})
              key-down (fn [^KeyEvent e]
                         (let [code (.getKeyCode e)]
                           (if-let [^Timer t (@pending code)]
                             (do (.stop t) (swap! pending dissoc code))          ; オートリピート: 無視
                             (act! [:down code (.isShiftDown e)]))))
              key-up (fn [^KeyEvent e]
                       (let [code (.getKeyCode e)
                             t (Timer. 40 (reify ActionListener
                                            (actionPerformed [_ _] (swap! pending dissoc code) (act! [:up code]))))]
                         (.setRepeats t false)
                         (swap! pending assoc code t)
                         (.start t)))
              udp (JCheckBox. (str "VMidiKey へ送る (UDP " (out/port) ")") true)
              mon (JCheckBox. "内蔵音源で鳴らす" true)
              chs (JComboBox. ^"[Ljava.lang.Object;" (into-array Object (map #(str "CH " %) (range 1 17))))
              top (JPanel. (FlowLayout. FlowLayout/LEFT))
              help (JLabel. "  Z〜/ と Q〜P で弾く   ← → オクターブ   ↑ ↓ ベロシティ   PgUp PgDn 音色   Space ホールド   Esc 全部止める")
              note-at (fn [^MouseEvent e] (when-let [l @lay] (piano/hit l (- (.getX e) 4) (- (.getY e) 4))))]
          (.setPreferredSize piano (Dimension. 1000 220))
          (.setFont status (Font. Font/MONOSPACED Font/BOLD 13))
          (doseq [c [udp mon chs status]] (.add top c))
          (.addActionListener udp (reify ActionListener (actionPerformed [_ _] (swap! state assoc :udp? (.isSelected udp)))))
          (.addActionListener mon (reify ActionListener (actionPerformed [_ _] (swap! state assoc :monitor? (.isSelected mon)))))
          (.addActionListener chs (reify ActionListener (actionPerformed [_ _] (act! [:channel (.getSelectedIndex chs)]) (.requestFocusInWindow piano))))
          (doseq [c [udp mon chs]] (.setFocusable c false))
          (let [ma (proxy [MouseAdapter] []
                     (mousePressed [e] (when-let [n (note-at e)] (act! [:mouse-down n])))
                     (mouseDragged [e] (act! [:mouse-move (note-at e)]))
                     (mouseReleased [_] (act! [:mouse-up])))]
            (.addMouseListener piano ma)
            (.addMouseMotionListener piano ma))
          ;; キーはウィンドウ全体で受ける (どの部品にフォーカスがあっても弾ける)
          (.addKeyEventDispatcher (KeyboardFocusManager/getCurrentKeyboardFocusManager)
                                  (reify KeyEventDispatcher
                                    (dispatchKeyEvent [_ e]
                                      (if (and (.isActive frame) (not (.isAltDown e)) (not (.isControlDown e)))
                                        (do (case (.getID e)
                                              401 (key-down e)       ; KEY_PRESSED
                                              402 (key-up e)         ; KEY_RELEASED
                                              nil)
                                            (not= 400 (.getID e)))   ; KEY_TYPED は他へ渡さない
                                        false))))
          (.add (.getContentPane frame) top BorderLayout/NORTH)
          (.add (.getContentPane frame) piano BorderLayout/CENTER)
          (.add (.getContentPane frame) help BorderLayout/SOUTH)
          (.setDefaultCloseOperation frame WindowConstants/DISPOSE_ON_CLOSE)
          (.addWindowListener frame (proxy [WindowAdapter] []
                                      (windowClosed [_]
                                        (let [[st msgs] (handle @state [:down vk-esc false])] (reset! state st) (emit st msgs))
                                        (out/close!)
                                        (when exit-on-close? (shutdown-agents) (System/exit 0)))))
          (.pack frame)
          (.setLocationRelativeTo frame nil)
          (.setVisible frame true)
          (deliver result {:frame frame :piano piano}))))
     @result)))
