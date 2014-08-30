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
  (when-not (= (last history) new-state)
    (println "Adding state to history: " (hasch/uuid new-state))
    (println "History size:" (inc (count history)))
    (conj history new-state)))

(defn get-history-state [hash]
  (println "Look for history state:" hash)
  (println "History:")
  (doseq [s @state-history]
    (println (hasch/uuid s)))
  (first (filter #(= (hasch/uuid %) hash) @state-history)))

(add-watch state :history (fn [_ _ _ new-state]
                            (swap! state-history save-history-state new-state)))

(defn initial-state [grubs recipes]
  {:grubs (util/map-by-key :id grubs)
   :recipes (util/map-by-key :id recipes)})

(defn sync-new-client! [to from]
  (let [client-id (java.util.UUID/randomUUID)
        server-shadow (atom cs/empty-state)]
    (add-watch state client-id (fn [_ _ _ current-state] 
                                 (when-let [msg (cs/diff-states @server-shadow current-state)]
                                   (a/put! to msg)
                                   ;; TODO: reset only if send succeeds?
                                   (reset! server-shadow current-state))))
    (a/go-loop []
               (if-let [{:keys [type diff hash shadow-hash] :as msg} (<! from)]
                 (do (condp = type
                       :diff 
                       (if (= (hasch/uuid @server-shadow) shadow-hash)
                         ;; we have what they thought we had
                         ;; apply changes normally
                         (let [new-shadow (swap! server-shadow sync/patch-state diff)]
                           (println "Hash matched state, apply changes")
                           (if (= (hasch/uuid new-shadow) hash)
                             (let [new-state (swap! state sync/patch-state diff)]
                               (>! @to-db diff))
                             (do (println "Applying diff failed --> full sync")
                                 (let [sync-state @state]
                                   (reset! server-shadow sync-state)
                                   (a/put! to (cs/complete-sync-response sync-state))))))
                         ;; we have something different than they thought
                         ;; check history
                         (if-let [history-state (get-history-state shadow-hash)]
                           ;; Found what they thought in history, 
                           ;; reset client state to this
                           ;; and continue as normal
                           (do 
                             (println "Hash check failed --> Reset from history")
                             (reset! server-shadow history-state)
                               (let [new-shadow (swap! server-shadow sync/patch-state diff)]
                                 (if (= (hasch/uuid new-shadow) hash)
                                   (let [new-state (swap! state sync/patch-state diff)]
                                     (>! @to-db diff))
                                   (do (println "Applying diff failed --> full sync")
                                       (let [sync-state @state]
                                         (reset! server-shadow sync-state)
                                         (a/put! to (cs/complete-sync-response sync-state)))))))
                           ;; No history found, do complete sync
                           (do (println "Hash check failed, not in history --> full sync")
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
