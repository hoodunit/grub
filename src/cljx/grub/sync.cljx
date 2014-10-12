(ns grub.sync
  (:require [grub.diff :as diff]
            [grub.util :as util]
            [hasch.core :as hasch]))

(def num-history-states 20)

(def empty-state {:grubs {} :recipes {}})

(defn initial-state [grubs recipes]
  (let [state {:grubs (util/map-by-key :id grubs)
               :recipes (util/map-by-key :id recipes)}]
    [{:state state :hash (hasch/uuid state)}]))

(defn new-state [state]
  [{:hash (hasch/uuid state)
    :state state}])

(defn get-current-state [states]
  (:state (last states)))

(defn get-history-state [states hash]
  (:state (first (filter #(= (:hash %) hash) states))))

(defn add-history-state [states new-state]
  (let [last-hash (:hash (last states))
        new-hash (hasch/uuid new-state)]
    (if (= last-hash new-hash)
      states
      (let [new-states (conj states {:hash new-hash :state new-state})]
        (if (>= (count states) num-history-states)
          (into [] (rest new-states))
          new-states)))))

(defn empty-diff? [diff]
  (= diff {:recipes {:deleted #{}, :updated nil}, :grubs {:deleted #{}, :updated nil}}))
