(ns vmidikey.keys
  "文字キー → 音 の割り当てと、キーボードの状態 (純粋関数)。

   割り当ては、多くの音楽ソフトの仮想キーボードと同じ 2 段:
     下の段  Z S X D C V G B H N J M  , L . ; /   → base から (白鍵 = 下の列、黒鍵 = その上の列)
     上の段  Q 2 W 3 E R 5 T 6 Y 7 U  I 9 O 0 P   → base + 12 から
   キーは java.awt.event.KeyEvent のキーコード (VK_*) の数値で持つ (配列の違う JIS キーボードでも同じ位置)。
   音名は STED2・polysted と同じく C4 = 60。")

;; KeyEvent の VK_ の値 (java.awt に頼らずテストできるよう数値で持つ)
(def ^:private vk
  {:z 90 :s 83 :x 88 :d 68 :c 67 :v 86 :g 71 :b 66 :h 72 :n 78 :j 74 :m 77
   :comma 44 :l 76 :period 46 :semicolon 59 :slash 47
   :q 81 :k2 50 :w 87 :k3 51 :e 69 :r 82 :k5 53 :t 84 :k6 54 :y 89 :k7 55 :u 85
   :i 73 :k9 57 :o 79 :k0 48 :p 80})

(def key-offsets
  "キーコード → base からの半音の数"
  (into {}
        (map (fn [[k off]] [(vk k) off])
             (concat
              ;; 下の段
              [[:z 0] [:s 1] [:x 2] [:d 3] [:c 4] [:v 5] [:g 6] [:b 7] [:h 8] [:n 9] [:j 10] [:m 11]
               [:comma 12] [:l 13] [:period 14] [:semicolon 15] [:slash 16]]
              ;; 上の段 (1 オクターブ上)
              [[:q 12] [:k2 13] [:w 14] [:k3 15] [:e 16] [:r 17] [:k5 18] [:t 19] [:k6 20] [:y 21] [:k7 22] [:u 23]
               [:i 24] [:k9 25] [:o 26] [:k0 27] [:p 28]]))))

(def ^:private lower-row (set (map vk [:z :s :x :d :c :v :g :b :h :n :j :m :comma :l :period :semicolon :slash])))
(def ^:private key-names
  {90 "Z" 83 "S" 88 "X" 68 "D" 67 "C" 86 "V" 71 "G" 66 "B" 72 "H" 78 "N" 74 "J" 77 "M"
   44 "," 76 "L" 46 "." 59 ";" 47 "/" 81 "Q" 50 "2" 87 "W" 51 "3" 69 "E" 82 "R" 53 "5" 84 "T"
   54 "6" 89 "Y" 55 "7" 85 "U" 73 "I" 57 "9" 79 "O" 48 "0" 80 "P"})

(def key-labels
  "鍵盤に書く文字: base からの半音 → [文字…] (下の段が先。12〜16 は下の段と上の段の両方で弾ける)"
  (reduce (fn [m [code off]] (update m off (fnil conj []) (key-names code)))
          {} (sort-by (fn [[code _]] (if (lower-row code) 0 1)) key-offsets)))

(def note-names ["C" "C#" "D" "D#" "E" "F" "F#" "G" "G#" "A" "A#" "B"])

(defn note-name "キー番号 → 音名 (C4 = 60)" [n]
  (str (note-names (mod n 12)) (dec (quot n 12))))

(defn black? "黒鍵か" [n] (contains? #{1 3 6 8 10} (mod n 12)))

;; ---- キーボードの状態 ----
;; {:base 下の段の C のキー番号 :vel ベロシティ :ch チャンネル 0〜15 :held {キーコード 鳴らしている音} :sustain? }
(def initial {:base 48 :vel 100 :ch 0 :program 0 :held {} :sustain? false})

(defn clamp [lo hi v] (-> v (max lo) (min hi)))

(defn key-down
  "文字キーを押した → [新しい状態 送るメッセージの列]。割り当ての無いキーや、押したままの (オートリピート) は何もしない。
   メッセージは [ステータス データ1 データ2] のベクタ"
  [st code]
  (if-let [off (key-offsets code)]
    (if (contains? (:held st) code)
      [st []]
      (let [n (+ (:base st) off)]
        (if (<= 0 n 127)
          [(assoc-in st [:held code] n) [[(bit-or 0x90 (:ch st)) n (:vel st)]]]
          [st []])))
    [st []]))

(defn key-up
  "文字キーを離した → [新しい状態 メッセージ]。押したときの音を止める (途中でオクターブを変えても正しく止まる)"
  [st code]
  (if-let [n (get-in st [:held code])]
    [(update st :held dissoc code) [[(bit-or 0x80 (:ch st)) n 0]]]
    [st []]))

(defn octave "下の段の C を d オクターブ動かす (C-1〜C8 の範囲で)" [st d]
  (update st :base #(clamp 0 108 (+ % (* 12 d)))))

(defn velocity "ベロシティを d 増減 (1〜127)" [st d]
  (update st :vel #(clamp 1 127 (+ % d))))

(defn sustain
  "ホールド (CC64) を on? に → [新しい状態 メッセージ]"
  [st on?]
  (if (= on? (:sustain? st))
    [st []]
    [(assoc st :sustain? on?) [[(bit-or 0xB0 (:ch st)) 64 (if on? 127 0)]]]))

(defn program "音色を p (0〜127) に → [新しい状態 メッセージ]" [st p]
  (let [p (clamp 0 127 p)]
    [(assoc st :program p) [[(bit-or 0xC0 (:ch st)) p]]]))

(defn all-off
  "鳴らしている音をすべて止める (押したままの文字キーの音、ホールド、オールノートオフ) → [新しい状態 メッセージ]"
  [st]
  [(assoc st :held {} :sustain? false)
   (vec (concat (for [n (vals (:held st))] [(bit-or 0x80 (:ch st)) n 0])
                (when (:sustain? st) [[(bit-or 0xB0 (:ch st)) 64 0]])
                [[(bit-or 0xB0 (:ch st)) 123 0]]))])

(defn channel "チャンネルを ch (0〜15) に。鳴っている音は先に止める → [新しい状態 メッセージ]" [st ch]
  (let [[st msgs] (all-off st)]
    [(assoc st :ch (clamp 0 15 ch)) msgs]))
