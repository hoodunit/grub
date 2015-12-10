(ns grub.util)

(defn map-by-key [key coll]
  (->> coll
       (map (fn [a] [(keyword (get a key)) a]))
       (into {})))

(defn rand-str [n]
  (let [chars "0123456789abcdefghijklmnopqrstuvwxyz"
        rand-index #(rand-int (count chars))]
    (->> (repeatedly n rand-index)
         (map #(.charAt chars %))
         (clojure.string/join))))
