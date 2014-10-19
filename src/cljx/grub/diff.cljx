(ns grub.diff
  (:require [clojure.data :as data]
            [clojure.set :as set]
            [grub.tag :as tag]))

(defn deleted [a b]
  (set/difference (into #{} (keys a)) (into #{} (keys b))))

(defn updated [a b]
  (second (data/diff a b)))

(defn diff-maps [a b]
  (when (and (map? a) (map? b))
    {:- (deleted a b)
     :+ (updated a b)}))

(defn diff-keys [prev next]
  (->> prev
       (keys)
       (map (fn [k] [k (diff-maps (k prev) (k next))]))
       (filter #(not (nil? (second %))))
       (into {})))

(defn diff-states [prev next]
  (let [key-diffs (diff-keys prev next)]
    (if (and (:tag prev) (:tag next))
      (assoc key-diffs 
        :shadow-tag (:tag prev)
        :tag (:tag next))
      key-diffs)))

(defn patch-map [state diff]
  (-> state
      (#(apply dissoc % (into [] (:- diff))))
      (#(merge-with merge % (:+ diff)))))

(defn patch-state 
  ([state diff] (patch-state state diff false))
  ([state diff use-diff-tag?]
     (let [patched (->> state
                        (keys)
                        (map (fn [k] [k (patch-map (k state) (k diff))]))
                        (into {}))]
       (if use-diff-tag?
         (assoc patched :tag (:tag diff))
         (if (= state patched)
           state
           (assoc patched :tag (tag/new-tag)))))))
