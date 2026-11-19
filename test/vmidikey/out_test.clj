(ns vmidikey.out-test
  (:require [clojure.test :refer [deftest is testing]]
            [vmidikey.out :as out]))

(deftest latency-ms-test
  (testing "指定が無ければ既定の 40ms (システムプロパティで変えられる)"
    (System/clearProperty "vmidikey.latency")
    (when-not (System/getenv "VMIDIKEY_LATENCY")
      (is (= 40 (out/latency-ms))))
    (System/setProperty "vmidikey.latency" "30")
    (is (= 30 (out/latency-ms)))
    (System/clearProperty "vmidikey.latency")))
