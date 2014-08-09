(ns grub.state
  (:require [grub.sync :as sync]
            [grub.util :as util]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def empty-state
  {:grubs {}
   :recipes {}})

(def state (atom empty-state))
(def to-db (atom nil))
(def to-all (chan))
(def from-all (a/mult to-all))

(defn get-initial-state [grubs recipes]
  {:grubs (util/map-by-key :id grubs)
   :recipes (util/map-by-key :id recipes)})

(defn add-client! [to from]
  (let [client-id (java.util.UUID/randomUUID)]
    (println "New client id:" client-id)
    (a/go-loop []
               (when-let [diff (<! from)]
                 (swap! state #(sync/patch-state % diff))
                 (>! @to-db diff)
                 (>! to-all {:diff diff :source-id client-id})
                 (recur)))
    (let [all-diffs (chan)]
      (a/tap from-all all-diffs)
      (a/go-loop [] (if-let [{:keys [diff source-id] :as event} (<! all-diffs)]
                      (do 
                        (when-not (= source-id client-id)
                          (>! to diff))
                        (recur))
                      (a/untap from-all all-diffs))))
    (a/put! to (sync/diff-states empty-state @state))))

(defn init [_to-db grubs recipes]
  (reset! state (get-initial-state grubs recipes))
  (reset! to-db _to-db))
