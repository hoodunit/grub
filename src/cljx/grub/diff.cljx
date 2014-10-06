(ns grub.diff
  (:require [clojure.data :as data]
            [clojure.set :as set]))

(defn deleted [a b]
  (set/difference (into #{} (keys a)) (into #{} (keys b))))

(defn updated [a b]
  (second (data/diff a b)))

(defn diff-maps [a b]
  {:deleted (deleted a b)
   :updated (updated a b)})

(defn diff-states [prev next]
  (->> prev
       (keys)
       (map (fn [k] [k (diff-maps (k prev) (k next))]))
       (into {})))

(defn patch-map [state diff]
  (-> state
      (#(apply dissoc % (into [] (:deleted diff))))
      (#(merge-with merge % (:updated diff)))))

(defn patch-state [state diff]
  (->> state
       (keys)
       (map (fn [k] [k (patch-map (k state) (k diff))]))
       (into {})))
