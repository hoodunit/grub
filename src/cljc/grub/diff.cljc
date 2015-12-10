(ns grub.diff
  (:require [clojure.data :as data]
            [clojure.set :as set]))

(defn deleted [a b]
  (set/difference (into #{} (keys a)) (into #{} (keys b))))

(defn updated [a b]
  (second (data/diff a b)))

(defn diff-maps [a b]
  (when (and (map? a) (map? b))
    {:- (deleted a b)
     :+ (updated a b)}))

(defn diff-states [prev next]
  (let [prev* (dissoc prev :tag)
        next* (dissoc next :tag)]
    (->> prev*
         (keys)
         (map (fn [k] [k (diff-maps (k prev*) (k next*))]))
         (filter #(not (nil? (second %))))
         (into {})
         (#(assoc % :shadow-tag (:tag prev) :tag (:tag next))))))

(defn patch-map [state diff]
  (-> state
      (#(apply dissoc % (into [] (:- diff))))
      (#(merge-with merge % (:+ diff)))))

(defn patch-state [state diff]
  (->> state
       (keys)
       (map (fn [k] [k (patch-map (k state) (k diff))]))
       (into {})
       (#(assoc % :tag (:tag diff)))))
