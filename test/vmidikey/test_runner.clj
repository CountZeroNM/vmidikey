(ns vmidikey.test-runner
  "テストの実行: clojure -M:test (bb test)。失敗があれば終了コード 1"
  (:require [clojure.test :as t]
            [vmidikey.keys-test] [vmidikey.piano-test] [vmidikey.gui-test] [vmidikey.device-test] [vmidikey.out-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'vmidikey.keys-test 'vmidikey.piano-test 'vmidikey.gui-test 'vmidikey.device-test 'vmidikey.out-test)]
    (shutdown-agents)
    (System/exit (if (pos? (+ fail error)) 1 0))))
