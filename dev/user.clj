(ns user
  (:require [grub.core :as system]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]))

(def system nil)

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system system/start system/dev-config))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (system/stop s)))))

(defn reset []
  (stop)
  (refresh :after 'user/start))
