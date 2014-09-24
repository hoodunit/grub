(ns grub.state
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.sync :as sync]
            [clojure.core.async :as a :refer [<! >! chan go]]))

;; Server state
(def states (ref []))
(def to-db (atom nil))

(defmulti handle-message (fn [msg states client-state] (:type msg)))

(defn full-sync! [msg states client-state]
  (let [new-client (dosync (let [state (sync/get-current-state @states)]
                                                (ref-set client-state state)))]
                       (println "full-sync!")
                       (message/full-sync new-client)))

(defmethod handle-message :full-sync [msg states client-state]
  (full-sync! msg states client-state))

(defmethod handle-message :new-state [msg states client-state]
  (let [diff-result (sync/diff-states (:new-states msg) @client-state)
        {:keys [diff shadow-hash]} diff-result]
    (println "new-state!")
    (message/diff-msg diff shadow-hash)))

(defmethod handle-message :diff [msg states client-state]
  (dosync
   (println "diff!")
   (let [{:keys [diff shadow-hash]} msg
         apply-result (sync/apply-diff @states diff shadow-hash)
         {:keys [new-states new-shadow full-sync?]} apply-result]
     (ref-set states new-states)
     (ref-set client-state new-shadow)
     (when full-sync? (full-sync! msg states client-state)))))

(defn make-client-agent [in initial-states]
  (let [out (chan)]
    (a/go-loop [client-state sync/empty-state
                states initial-states] 
               (if-let [msg (<! in)]
                 (do (when-let [{:keys [new-client new-states]} (handle-message msg states client-state)]
                       (>! >client response))
                     (recur))
                 (remove-watch states client-id)))
    out))

(defn sync-new-client! [>client <client]
  (let [client-id (java.util.UUID/randomUUID)
        state-changes (chan)
        state-change-events (a/map< (fn [s] {:type :new-state :new-states s}) state-changes)
        events (chan)
        client-state (ref sync/empty-state)]
    (add-watch states client-id (fn [_ _ _ new-states] (a/put! state-changes new-states)))
    (a/pipe (a/merge [<client state-change-events]) events)
    (a/go-loop [] (if-let [msg (<! in)]
                    (do (when-let [response (handle-message msg states client-state)]
                          (>! >client response))
                        (recur))
                    (remove-watch states client-id)))))

(defn init [_to-db grubs recipes]
  (dosync (ref-set states (sync/initial-state grubs recipes)))
  (reset! to-db _to-db))
