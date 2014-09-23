(ns grub.state
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.sync :as sync]
            [clojure.core.async :as a :refer [<! >! chan go]]))

;; Server state
(def states (ref []))

(def to-db (atom nil))

(defn make-client [in client-state states]
  (let [out (chan)
        full-sync! (fn [] 
                     (let [new-client (dosync (let [state (sync/get-current-state @states)]
                                                (ref-set client-state state)))]
                       (println "full-sync!")
                       (a/put! out (message/full-sync new-client))))]
    (a/go-loop 
     []
     (when-let [msg (<! in)]
       (condp = (:type msg)
         :new-state (let [diff-result (sync/diff-states (:new-states msg) @client-state)
                          {:keys [diff shadow-hash]} diff-result
                          out-msg (message/diff-msg diff shadow-hash)]
                      (println "new-state!")
                      (a/put! out out-msg))
         :diff (dosync
                (println "diff!")
                (let [{:keys [diff shadow-hash]} msg
                      apply-result (sync/apply-diff @states diff shadow-hash)
                      {:keys [new-states new-shadow full-sync?]} apply-result]
                  (ref-set states new-states)
                  (ref-set client-state new-shadow)
                  (when full-sync? (full-sync!))))
         :full-sync (full-sync!)
         nil)
       (recur)))
    out))

(defn sync-new-client! [>client <client]
  (let [client-id (java.util.UUID/randomUUID)
        state-changes (chan)
        state-change-events (a/map< (fn [s] {:type :new-state :new-states s}) state-changes)
        events (chan)
        client-state (ref sync/empty-state)]
    (add-watch states client-id (fn [_ _ _ new-states] (a/put! state-changes new-states)))
    (a/pipe (a/merge [<client state-change-events]) events)
    (let [out (make-client events client-state states)]
      (a/go-loop [] (if-let [v (<! out)]
                      (do (>! >client v)
                          (recur))
                      (remove-watch states client-id))))))

(defn init [_to-db grubs recipes]
  (dosync (ref-set states (sync/initial-state grubs recipes)))
  (reset! to-db _to-db))
