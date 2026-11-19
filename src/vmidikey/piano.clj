(ns vmidikey.piano
  "画面の鍵盤の配置と当たり判定 (純粋関数)。
   白鍵を同じ幅で並べ、黒鍵はその境目に幅 0.6、高さ 0.62 で重ねる。"
  (:require [vmidikey.keys :as k]))

(defn white-keys "lo〜hi の白鍵のキー番号" [lo hi] (vec (remove k/black? (range lo (inc hi)))))

(defn layout
  "鍵盤の配置。lo〜hi のキーを幅 w・高さ h に並べる。
   => {:white-w 白鍵の幅 :keys [{:note :x :y :w :h :black?} …]} (白鍵が先、黒鍵が後 = 描く順)"
  [lo hi w h]
  (let [whites (white-keys lo hi)
        ww (/ (double w) (max 1 (count whites)))
        wx (into {} (map-indexed (fn [i n] [n (* i ww)]) whites))
        bw (* ww 0.6) bh (* h 0.62)
        blacks (for [n (range lo (inc hi)) :when (and (k/black? n) (wx (dec n)))]
                 {:note n :x (- (+ (wx (dec n)) ww) (/ bw 2)) :y 0 :w bw :h bh :black? true})]
    {:white-w ww
     :keys (vec (concat (for [n whites] {:note n :x (wx n) :y 0 :w ww :h h :black? false}) blacks))}))

(defn hit
  "点 (x, y) にあるキー番号 (黒鍵を優先)。無ければ nil"
  [{:keys [keys]} x y]
  (let [in? (fn [{kx :x ky :y kw :w kh :h}] (and (<= kx x) (< x (+ kx kw)) (<= ky y) (< y (+ ky kh))))]
    (:note (or (first (filter #(and (:black? %) (in? %)) keys))
               (first (filter #(and (not (:black? %)) (in? %)) keys))))))

(defn visible-range
  "見せる範囲: 下の段の C (base) の 1 オクターブ下から 4 オクターブ上の C まで (文字キーの届く範囲を含む)"
  [base]
  (let [lo (max 0 (- base 12)) hi (min 127 (+ base 48))] [lo hi]))
