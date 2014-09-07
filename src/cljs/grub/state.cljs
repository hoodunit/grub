(ns grub.state
  (:require [grub.sync :as sync]
            [grub.common-state :as cs]
            [cljs.core.async :as a :refer [<! >! chan]]
            [hasch.core :as hasch])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def state (atom cs/empty-state))
(def server-state (atom cs/empty-state))

(def unacked-states (atom {}))

(defn get-server-state [hash]
  (if (= (hasch/uuid @server-state) hash)
    @server-state
    (get @unacked-states hash)))

(defn send-state-changes-to-server! [state-changes from]
  (go-loop []
           (when-let [current-state (<! state-changes)]
             (when-not (= @server-state current-state)
               (let [msg (cs/diff-states @server-state current-state)]
                 (swap! unacked-states assoc (:hash msg) current-state)
                 (a/put! from msg)))
             (recur))))

(defn handle-received-changes! [to from]
  (go-loop [] 
           (if-let [{:keys [type diff hash shadow-hash] :as msg} (<! to)]
             (do (condp = type
                   :diff 
                   (if-let [acked-server-state (get-server-state shadow-hash)]
                     (do (reset! server-state acked-server-state)
                         (reset! unacked-states {})
                         (let [new-server (swap! server-state #(sync/patch-state % diff))]
                           (if (= (hasch/uuid new-server) hash)
                             (swap! state sync/patch-state diff)
                             (do (log "State update failure --> complete sync")
                                 (a/put! from cs/complete-sync-request)))))
                     (do (log "Could not find server state locally --> complete sync")
                         (a/put! from cs/complete-sync-request)))
                   :complete (do 
                               (logs "Complete sync")
                               (reset! unacked-states {})
                               (reset! server-state (:state msg))
                               (reset! state (:state msg)))
                   (logs "Invalid msg:" msg))
                 (recur))
             (remove-watch state :state))))

(defn sync-state! [to from reset? state-changes]
  (send-state-changes-to-server! state-changes from)
  (handle-received-changes! to from)
  (a/put! from (if reset? cs/complete-sync-request (cs/diff-states @server-state @state))))
