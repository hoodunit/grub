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

(def state-history (atom []))

(defn save-history-state [history new-state]
  (when-not (= (hasch/uuid (last history)) (hasch/uuid new-state))
    (conj history new-state)))

(defn get-history-state [hash]
  (first (filter #(= (hasch/uuid %) hash) @state-history)))

(add-watch state :history (fn [_ _ _ new-state]
                            (swap! state-history save-history-state new-state)))

(defn initial-state [grubs recipes]
  {:grubs (util/map-by-key :id grubs)
   :recipes (util/map-by-key :id recipes)})

(defn sync-new-client! [to from]
  (let [client-id (java.util.UUID/randomUUID)
        client-state (atom cs/empty-state)
        log (fn [& args]
              (apply println client-id args))]
    (add-watch state client-id (fn [_ _ _ current-state] 
                                 (let [msg (cs/diff-states @client-state current-state)]
                                   (a/put! to msg)
                                   (reset! client-state current-state))))
    (a/go-loop []
               (if-let [{:keys [type diff hash shadow-hash] :as msg} (<! from)]
                 (do (condp = type
                       :diff 
                       (if (= (hasch/uuid @client-state) shadow-hash)
                         ;; We have what they thought we had
                         ;; Apply changes normally
                         (let [new-shadow (swap! client-state sync/patch-state diff)]
                           (log "Hash matched state, apply changes")
                           (if (= (hasch/uuid new-shadow) hash)
                             (let [new-state (swap! state sync/patch-state diff)]
                               (>! @to-db diff))
                             (do (log "Applying diff failed --> full sync")
                                 (let [sync-state @state]
                                   (reset! client-state sync-state)
                                   (a/put! to (cs/complete-sync-response sync-state))))))
                         ;; We have something different than they thought
                         ;; Check history
                         (do
                             (log "Hash check failed --> Reset from history")
                             (if-let [history-state (get-history-state shadow-hash)]
                               ;; Found what they thought we had in history, 
                               ;; reset client state to this and continue as normal
                               (do 
                                 (reset! client-state history-state)
                                 (let [new-shadow (swap! client-state sync/patch-state diff)]
                                   (if (= (hasch/uuid new-shadow) hash)
                                     (let [new-state (swap! state sync/patch-state diff)]
                                       (>! @to-db diff))
                                     (do (log "Applying diff failed --> full sync")
                                         (let [sync-state @state]
                                           (reset! client-state sync-state)
                                           (a/put! to (cs/complete-sync-response sync-state)))))))
                               ;; Not found in history, do complete sync
                               (do (log "Hash check failed, not in history --> full sync")
                                   (let [sync-state @state]
                                     (reset! client-state sync-state)
                                     (a/put! to (cs/complete-sync-response sync-state)))))))
                       :complete (let [new-state (reset! client-state @state)]
                                   (log "full sync")
                                   (a/put! to (cs/complete-sync-response new-state)))
                       (log "Invalid msg:" msg))
                     (recur))
                 (remove-watch state client-id)))))

(defn init [_to-db grubs recipes]
  (reset! state (initial-state grubs recipes))
  (reset! to-db _to-db))
