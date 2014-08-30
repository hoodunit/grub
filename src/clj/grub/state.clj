(ns grub.state
  (:require [grub.sync :as sync]
            [grub.util :as util]
            [grub.common-state :as cs]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hasch.core :as hasch]))

(def state (atom cs/empty-state))
(def to-db (atom nil))
(def to-all (chan))
(def from-all (a/mult to-all))

(defn initial-state [grubs recipes]
  {:grubs (util/map-by-key :id grubs)
   :recipes (util/map-by-key :id recipes)})

(defn sync-new-client! [to from]
  (let [client-id (java.util.UUID/randomUUID)
        server-shadow (atom cs/empty-state)]
    (add-watch state client-id (fn [_ _ _ new-state] 
                                 (when-let [msg (cs/diff-states new-state @server-shadow)]
                                   (a/put! to msg)
                                   (reset! server-shadow new-state))))
    (a/go-loop []
               (if-let [{:keys [type diff hash shadow-hash] :as msg} (<! from)]
                 (do (condp = type
                       :diff (let [new-shadow (swap! server-shadow sync/patch-state diff)]
                               (if (= (hasch/uuid new-shadow) hash)
                                 (let [new-state (swap! state sync/patch-state diff)]
                                   (>! @to-db diff))
                                 (do (println "Hash check failed --> complete sync")
                                     (let [sync-state @state]
                                       (reset! server-shadow sync-state)
                                       (a/put! to (cs/complete-sync-response sync-state))))))
                       :complete (let [new-state (reset! server-shadow @state)]
                                   (a/put! to (cs/complete-sync-response new-state)))
                       (println "Invalid msg:" msg))
                     (recur))
                 (remove-watch state client-id)))))

(defn init [_to-db grubs recipes]
  (reset! state (initial-state grubs recipes))
  (reset! to-db _to-db))
