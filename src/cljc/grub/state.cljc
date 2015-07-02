(ns grub.state)

(def num-history-states 20)

(def empty-state {:tag 0 :grubs {} :recipes {}})

(defn new-states [state]
  [(assoc state :tag 0)])

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
      (let [new-states (conj states (assoc new-state :tag (inc (:tag last-state))))]
        (if (>= (count states) num-history-states)
          (into [] (rest new-states))
          new-states)))))

(defn state= [a b]
  (= (dissoc a :tag) (dissoc b :tag)))
