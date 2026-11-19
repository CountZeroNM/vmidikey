# CLAUDE.md — VMidiKey (仮想 MIDI キーボード)

## 概要
- 文字キーと画面の鍵盤で弾く仮想 MIDI キーボード (Clojure + Swing)。押した音は UDP 127.0.0.1:47100 へ送る。
- 受け側は Java の仮想 MIDI 入力の機器 "VMidiKey" (`java/src/vmidikey/spi`、`bb device` で `target/vmidikey-device.jar`)。
  受け側の JVM の classpath に jar を足すと MidiSystem の機器の一覧に出る。
- 目的: ChromeOS の Linux では ALSA の仮想 MIDI 機器が使えず Java から見えないので、STED (`~/code/sted`) の
  MIDI 入力 (録音) を試すために作った。STED の deps.edn のエイリアス `:vmidikey` で使う。

## 動作確認
```bash
bb device   # jar を作る (Java のソースを変えたら必ず)
bb test     # clojure.test (device のタスクを先に実行する)。失敗があれば終了コード 1
bb start    # 起動
clojure -Sdeps '{:deps {clj-kondo/clj-kondo {:mvn/version "2024.08.01"}}}' -M -m clj-kondo.main --lint src test   # 警告 0 を保つ
```
- ChromeOS の Linux は DISPLAY=wayland-0 (sommelier が設定) で Java が X11 につなげない。bb のタスクは自動で DISPLAY=:0 にする。clojure を直接使うときは `DISPLAY=:0` を付ける。
- ウィンドウの確認は、同じ JVM で `vmidikey.gui/run!` を `:emit` 付きで開き、合成した KeyEvent を
  `KeyboardFocusManager` 経由で送って、送られたメッセージとフレームの画像を見る。
- 音は Claude Code からは確認できないので、ユーザーに頼む。

## 方針
- 返答は日本語で、結論から簡潔に。
- 計算 (キーの割り当て、鍵盤の配置、イベントの処理) は純粋関数にして clojure.test でテストする。副作用は out.clj と gui.clj の run! に集める。
- 機能を変えたら README の該当の節と「確認したこと」を更新する。
