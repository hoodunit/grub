(ns grub.sync
  (:require [grub.diff :as diff]
            [grub.util :as util]
            [hasch.core :as hasch]))

(def empty-state {:grubs {} :recipes {}})

(defn initial-state [grubs recipes]
  (let [state {:grubs (util/map-by-key :id grubs)
               :recipes (util/map-by-key :id recipes)}]
    [{:state state :hash (hasch/uuid state)}]))

(defn get-current-state [states]
  (:state (last states)))

(defn get-history-state [states hash]
  (:state (first (filter #(= (:hash %) hash) states))))

(defn add-history-state [states new-state]
  (let [last-hash (:hash (last states))
        new-hash (hasch/uuid new-state)]
    (if (= last-hash new-hash)
      states
      (conj states {:hash new-hash :state new-state}))))

(defn diff-states [states shadow]
  (let [state (get-current-state states)]
    {:shadow-hash (hasch/uuid shadow)
     :diff (diff/diff-states shadow state)}))

(defn apply-diff [states diff shadow-hash]
  (let [state (:state (first states))
        shadow (get-history-state states shadow-hash)]
    (if shadow
      {:new-states (add-history-state states (diff/patch-state state diff))
       :new-shadow (diff/patch-state shadow diff)
       :full-sync? false}
      {:new-states states
       :new-shadow state
       :full-sync? true})))
