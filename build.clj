;; build.clj -- 配布用の jar を作る (tools.build)
;;   clojure -T:build uber     target/vmidikey-<版>.jar (java -jar でキーボードが開く)
;;   clojure -T:build clean
;; 仮想 MIDI 入力の機器 (java/src) もコンパイルして同梱する (この jar を受け側の classpath に足しても機器が見える)。
(ns build
  (:require [clojure.tools.build.api :as b]))

(def version "0.1.0")
(def class-dir "target/uber-classes")
(def uber-file (format "target/vmidikey-%s.jar" version))

(defn clean [_]
  (b/delete {:path class-dir})
  (b/delete {:path uber-file}))

(defn uber [_]
  (clean nil)
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/javac {:src-dirs ["java/src"] :class-dir class-dir :basis basis
              :javac-opts ["--release" "11" "-encoding" "UTF-8"]})
    (b/copy-dir {:src-dirs ["src" "java/resources"] :target-dir class-dir})
    (b/copy-file {:src "LICENSE" :target (str class-dir "/LICENSE")})
    (b/write-file {:path (str class-dir "/vmidikey/version.txt") :string version})
    (b/compile-clj {:basis basis :class-dir class-dir :ns-compile '[vmidikey.main]
                    :java-opts ["-Djava.awt.headless=true"]})
    (b/uber {:class-dir class-dir :uber-file uber-file :basis basis :main 'vmidikey.main})
    (println "作成:" uber-file)))
