(ns vmidikey.main
  "起動: clojure -M:run (bb start)"
  (:require [vmidikey.gui :as gui]
            [vmidikey.out :as out]))

(defn -main [& _]
  (println (str "VMidiKey: UDP 127.0.0.1:" (out/port) " へ送ります (受け側は target/vmidikey-device.jar の VMidiKey 機器)"))
  (gui/run! {:exit-on-close? true}))
