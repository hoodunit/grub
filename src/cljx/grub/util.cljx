(ns grub.util)

(defn map-by-key [key coll]
  (->> coll
       (map (fn [a] [(get a key) a]))
       (into {})))

