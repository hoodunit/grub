(ns grub.diff
  (:require [clojure.data :as data]
            [clojure.set :as set]))

(defn deleted [a b]
  (set/difference (into #{} (keys a)) (into #{} (keys b))))

(defn updated [a b]
  (println "*******************updated")
  (println "********************a:" a "\n\n\n")
  (println "********************b:" b "\n\n\n")
  (println "diff:" (second (data/diff a b)) "\n\n\n")
  (second (data/diff a b)))

(def a {:grub-e1ff4b5a-05eb-4364-8884-fc124ac1091c {:id :grub-e1ff4b5a-05eb-4364-8884-fc124ac1091c, :text "a", :completed false}})

(def b {:grub-e1ff4b5a-05eb-4364-8884-fc124ac1091c {:id :grub-e1ff4b5a-05eb-4364-8884-fc124ac1091c, :text "a", :completed false}})

(def d (second (data/diff a b)))


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
  (println "diff states")
  (println "prev:" (dissoc prev :recipes))
  (println "next:" (dissoc next :recipes))
  (let [prev* (dissoc prev :tag)
        next* (dissoc next :tag)]
    (->> prev*
         (keys)
         (map (fn [k] [k (diff-maps (k prev*) (k next*))]))
         (filter #(not (nil? (second %))))
         (into {}))))

(defn patch-map [state diff]
  (-> state
      (#(apply dissoc % (into [] (:- diff))))
      (#(merge-with merge % (:+ diff)))))

(defn patch-state [state diff]
  (->> state
       (keys)
       (map (fn [k] [k (patch-map (k state) (k diff))]))
       (into {})))
