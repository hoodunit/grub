(ns grub.state
  (:require [grub.diff :as diff]
            [grub.util :as util]
            [grub.tag :as tag]))

(def num-history-states 20)

(def empty-state {:tag (tag/oldest-tag) :grubs {} :recipes {}})

(defn new-states [state]
  [(assoc state :tag (tag/new-tag))])

(defn get-latest [states]
  (last states))

(defn get-tagged [states tag]
  (->> states
       (filter #(= (:tag %) tag))
       (first)))

(defn add [states new-state]
  (let [last-state (last states)]
    (if (= last-state new-state)
      states
      (let [new-states (conj states (assoc new-state :tag (tag/new-tag)))]
        (if (>= (count states) num-history-states)
          (into [] (rest new-states))
          new-states)))))

(defn state= [a b]
  (= (dissoc a :tag) (dissoc b :tag)))
