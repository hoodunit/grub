(ns grub.sync
  (:require [clojure.data :as data]
            [clojure.pprint :as pprint :refer [pprint]]
            [clojure.set :as set]))

(defn deleted [a b]
  (set/difference (into #{} (keys a)) (into #{} (keys b))))

(defn updated [a b]
  (second (data/diff a b)))

(defn diff [a b]
  {:deleted (deleted a b)
   :updated (changed a b)})

(defn diff-states [prev next]
  (->> prev
       (keys)
       (map (fn [k] [k (diff (k prev) (k next))]))
       (into {})))

