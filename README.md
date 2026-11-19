# VMidiKey — 仮想 MIDI キーボード

文字キーと画面の鍵盤で弾ける、仮想 MIDI キーボードです（Clojure + Swing）。押した音は、同じパソコンの中の UDP で **Java の仮想 MIDI 入力の機器「VMidiKey」** に届きます。この機器の jar を classpath に足した Java のアプリ（[polysted](https://github.com/CountZeroNM/polysted) など）からは、普通の **MIDI 入力の機器** として見えます。

ChromeOS の Linux（Crostini）のように、ALSA の仮想 MIDI 機器（snd-virmidi）が使えず、Java の MIDI 機器の一覧に内蔵の音源しか出ない環境でも、MIDI 入力を使うアプリを試せるようにするためのものです。

```
 ┌──────────── vmidikey (このアプリ) ───────────┐        ┌────── 受け側の JVM (polysted など) ──────┐
 │ 文字キー / 画面の鍵盤 → MIDI メッセージ           │  UDP   │ VMidiKey 機器 (target/vmidikey-device.jar) │
 │   → UDP 127.0.0.1:47100 へ 1 メッセージずつ  ─────┼──────→ │   → 送信口 (Transmitter) → アプリの受け手  │
 │   → 内蔵音源で鳴らす (モニター)                  │        │ MidiSystem の機器の一覧に "VMidiKey"       │
 └──────────────────────────────────────────────┘        └──────────────────────────────────────────┘
```

## ダウンロードして試す（Clojure の開発環境は不要）

[Releases](https://github.com/CountZeroNM/vmidikey/releases) から `vmidikey-0.1.0.jar`（約 5MB）をダウンロードして、`java -jar vmidikey-0.1.0.jar` かダブルクリックで開きます。必要なのは **Java 17 以上**だけです（Java 21 で確認しています。[Adoptium（Temurin）](https://adoptium.net/ja/) など）。

- [polysted](https://github.com/CountZeroNM/polysted/releases) の jar には、受け口（仮想 MIDI 入力の機器「VMidiKey」）が入っています。2 つの jar を起動するだけで、polysted の録音を VMidiKey で試せます。
- ほかの Java のアプリで受けるときは、この jar を受け側の classpath に足すと、機器「VMidiKey」が見えます（jar に機器も入っています）。
- macOS で「開発元を確認できない」と止められたら、Control キーを押しながらクリックして「開く」。ChromeOS の Linux でウィンドウが開かないときは `DISPLAY=:0 java -jar vmidikey-0.1.0.jar`。

自分で jar を作るときは `clojure -T:build uber`（`target/vmidikey-<版>.jar`）。タグ `v*` を push すると、GitHub Actions が jar を作って Releases に置きます。

## 必要なもの（ソースから使う場合）

- JDK 11 以上（`javac` と `jar`。仮想 MIDI 機器を作るとき）
- Clojure CLI
- babashka（`bb` のタスクを使う場合）

## 使い方

```bash
git clone https://github.com/CountZeroNM/vmidikey.git
cd vmidikey
bb device      # 仮想 MIDI 機器の jar を作る (target/vmidikey-device.jar)
bb start       # キーボードを起動 (clojure -M:run)
bb test        # テスト
```

- ChromeOS の Linux では `DISPLAY` が `wayland-0` になっていて Java の画面が開けないので、`bb start` と `bb test` は、`DISPLAY` が X11 の形でなければ `DISPLAY=:0` を付けて起動します（`clojure -M:run` を直接使うときは `DISPLAY=:0 clojure -M:run`）。

### 弾き方

| 操作 | 内容 |
|---|---|
| `Z S X D C V G B H N J M , L . ; /` | 下の段（`Z` が C。白鍵は下の列、黒鍵はその上の列） |
| `Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P` | 上の段（1 オクターブ上） |
| 画面の鍵盤 | クリックで鳴らし、なぞると音が移る |
| `←` `→` | オクターブ（下の段の C を 1 オクターブずつ） |
| `↑` `↓` | ベロシティ ±8（`Shift` で ±1） |
| `PgUp` `PgDn` | 音色（プログラムチェンジ） |
| `Space` | ホールド（押している間 CC64） |
| `Esc` | すべての音を止める |
| 上の欄 | 送り先（VMidiKey へ送る・内蔵音源で鳴らす）、MIDI チャンネル |

- 鍵盤には、届く範囲の文字キーと C の音名を書いています。音名は STED2・polysted と同じく C4 = 60 です。
- キーは文字ではなくキーの位置（キーコード）で決めるので、JIS 配列でも同じ位置で弾けます。
- Linux のオートリピート（押したままにすると「離した → 押した」が続けて届く）は無視します。

### 受け側のアプリで使う

受け側の JVM の classpath に `target/vmidikey-device.jar` を足すと、`MidiSystem.getMidiDeviceInfo()` の一覧に `VMidiKey` が出ます。開くと UDP の 47100 番で待ち受け、届いたメッセージを送信口（`Transmitter`）につながった受け手へ渡します。

deps.edn の場合:

```clojure
{:aliases {:vmidikey {:extra-deps {vmidikey/device {:local/root "../vmidikey/target/vmidikey-device.jar"}}}}}
```

### polysted で録音する

配布用の jar どうしなら、上の「ダウンロードして試す」のとおり 2 つを起動するだけです。ソースから使うときは、次のようにします。

[polysted](https://github.com/CountZeroNM/polysted)（STED2 に強い影響を受けた MIDI ステップエディタ／シーケンサ、Clojure）の `deps.edn` には、このエイリアス `:vmidikey` があります。vmidikey と polysted を **同じ階層** に clone して使います。

```
(作業用のフォルダ)/
├── polysted/   (deps.edn の :vmidikey が ../vmidikey/target/vmidikey-device.jar を指す)
└── vmidikey/
```

```bash
cd vmidikey && bb device && bb start     # jar を作って、キーボードを開く
# 別の端末で
cd polysted
clojure -M:vmidikey:tui my_song.edn      # GUI なら -M:vmidikey:gui、REPL なら -M:vmidikey:repl
# トラック設定 (Tab) で MODE を Rec か Mix に → Ctrl-X rec → Enter で録音開始 → vmidikey で弾く → Esc で取り込む
```

録音したトラックを聴くときは、MODE を Play か Mix に戻します（Rec のトラックは再生で鳴りません）。詳しくは polysted の README の「レコーディング」を見てください。

ポートは、両方の JVM でシステムプロパティ `vmidikey.port` か環境変数 `VMIDIKEY_PORT` で変えられます（同じ値にする）。

## ファイル

| ファイル | 役割 |
|---|---|
| `java/src/vmidikey/spi/VMidiKeyProvider.java` | Java の MIDI の拡張の仕組み（`MidiDeviceProvider`）。機器 "VMidiKey" を見せる |
| `java/src/vmidikey/spi/VMidiKeyDevice.java` | 仮想 MIDI 入力の機器（UDP で待ち受けて、送信口へ渡す） |
| `java/resources/META-INF/services/…MidiDeviceProvider` | 拡張の登録 |
| `src/vmidikey/keys.clj` | 文字キー → 音の割り当てと、キーボードの状態（純粋関数） |
| `src/vmidikey/piano.clj` | 画面の鍵盤の配置と当たり判定（純粋関数） |
| `src/vmidikey/out.clj` | 送り先（UDP・内蔵音源） |
| `src/vmidikey/gui.clj` | ウィンドウ（イベントの処理 `handle` は純粋関数） |
| `src/vmidikey/main.clj` | 起動（配布用の jar の入口。`--version` `--help`） |
| `build.clj` / `.github/workflows/release.yml` | 配布用の jar を作る（`clojure -T:build uber`）/ タグ `v*` で Releases に置く |
| `test/vmidikey/*_test.clj` | テスト（clojure.test。仮想 MIDI 機器の結合テストを含む） |
| `bb.edn` / `deps.edn` | タスクとエイリアス |

## 確認したこと（2026-11-19）

- テスト 8 件（43 の確認）: キーの割り当て・オクターブ・ベロシティ・ホールド・すべて止める、鍵盤の配置と当たり判定、イベントの処理と描画、仮想 MIDI 機器（一覧に出る・開く・UDP のノートとエクスクルーシブが届く・閉じる）。
- ウィンドウで、文字キーの和音・オートリピートの無視・鍵盤のクリック。
- polysted（`clojure -M:vmidikey`）の機器の一覧に `VMidiKey` が出て、録音で C4・E4・G4 とボリュームの変更が取り込まれた（polysted の開発版で確認。公開版でも UDP で送ったノートが届くことを確認）。
- clj-kondo の警告 0。

## ライセンス

[MIT License](LICENSE)
