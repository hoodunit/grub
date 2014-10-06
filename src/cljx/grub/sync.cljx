(ns grub.sync
  (:require [grub.diff :as diff]
            [grub.util :as util]
            [hasch.core :as hasch]))

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
      (conj states {:hash new-hash :state new-state}))))

(defn diff-states [states shadow]
  (let [state states;(get-current-state states)
        ]
    {:hash (hasch/uuid shadow)
     :diff (diff/diff-states shadow state)}))

(defn apply-diff [states diff]
  (let [new-state (diff/patch-state (get-current-state states) diff)]
    (add-history-state states new-state)))

(defn empty-diff? [diff]
  (= diff {:recipes {:deleted #{}, :updated nil}, :grubs {:deleted #{}, :updated nil}}))
