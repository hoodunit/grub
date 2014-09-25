(ns grub.state
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.sync :as sync]
            [clojure.core.async :as a :refer [<! >! chan go]]))

;; Server state
(def states (atom []))

(defn make-server-agent [in >client states]
  (a/go-loop [client-state sync/empty-state]
             (when-let [msg (<! in)]
               (condp = (:type msg)
                 :diff
                 (let [{:keys [new-states new-shadow full-sync?]} (sync/apply-diff @states (:diff msg) (:hash msg))]
                   (println "diff!")
                   (if full-sync? 
                     (let [state (sync/get-current-state @states)]
                       (println "state not found, full sync!")
                       (>! >client (message/full-sync state))
                       (recur state))
                     (do (println "state found, just send changes")
                         (let [{:keys [diff hash]} (sync/diff-states new-states new-shadow)]
                           (reset! states new-states)
                           (>! >client (message/diff-msg diff hash))
                           (recur new-shadow)))))
                 :full-sync
                 (let [state (sync/get-current-state @states)]
                   (println "got full sync, send full sync")
                   (>! >client (message/full-sync state))
                   (recur state))
                 :new-state
                 (let [{:keys [diff shadow-hash]} (sync/diff-states (:new-states msg) client-state)]
                   (println "new-state!")
                   (>! >client (message/diff-msg diff shadow-hash)))
                 (do (println "Unknown message:" msg)
                     (recur client-state))))))

;; TODO: Remove watch, close up channels properly
(defn sync-new-client! [>client <client]
  (let [client-id (java.util.UUID/randomUUID)
        state-changes (chan)
        state-change-events (a/map< (fn [s] {:type :new-state :new-states s}) state-changes)
        client-events (chan)]
    (add-watch states client-id (fn [_ _ _ new-states] (a/put! state-changes new-states)))
    (a/pipe (a/merge [<client state-change-events]) client-events)
    (make-server-agent client-events >client states)))

(defn init [to-db grubs recipes]
  (reset! states (sync/initial-state grubs recipes))
  (add-watch states :to-db (fn [_ _ old-states new-states] 
                             (a/put! to-db (sync/get-current-state new-states)))))
