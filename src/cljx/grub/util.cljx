(ns grub.util
  (:require #+clj [clojure.core.async :as a :refer [<! >! chan go]]
            #+cljs [cljs.core.async :as a :refer [<! >! chan]])
  #+cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]]))

(defn map-by-key [key coll]
  (->> coll
       (map (fn [a] [(get a key) a]))
       (into {})))

(defn printer []
  (let [in (chan)]
    (go (loop [] 
          (when-let [msg (<! printer)]
            #+clj (clojure.pprint/pprint msg)
            #+clj (println "-------")
            #+cljs (logs msg)
            #+cljs (log "-------")
            (recur))))))

