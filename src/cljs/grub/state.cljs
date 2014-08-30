(ns grub.state
  (:require [grub.sync :as sync]
            [grub.common-state :as cs]
            [cljs.core.async :as a :refer [<! >! chan]]
            [hasch.core :as hasch])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def state (atom cs/empty-state))

(def unacked-history (atom {}))

(defn get-unacked-state [hash]
  (logs "Look for history state:" hash)
  (get @unacked-history hash))

(defn sync-state! [to from reset?]
  (let [server-state (atom cs/empty-state)]
    (add-watch state :state (fn [_ _ _ current-state] 
                              (when-not (= @server-state current-state)
                                (let [msg (cs/diff-states @server-state current-state)]
                                  (when-not (get @unacked-history (hasch/uuid current-state))
                                    (logs "state change! msg: " msg)
                                    (swap! unacked-history assoc (hasch/uuid current-state) current-state)
                                    (logs "History:" (keys @unacked-history))
                                    (a/put! from msg))
                                  ))))
    (go-loop [] 
             (if-let [{:keys [type diff hash shadow-hash] :as msg} (<! to)]
               (do (condp = type
                     :diff (do
                             (logs "Received diff:" msg)
                             (when (not (= (hasch/uuid @server-state) shadow-hash))
                               (reset! server-state (get-unacked-state shadow-hash)))
                             (reset! unacked-history {})
                             (let [ ;; what they now think we have (after updating)
                                   new-shadow (swap! server-state #(sync/patch-state % diff))]
                               ;; should match hash
                               (if (= (hasch/uuid new-shadow) hash)
                                 ;; apply same changes locally
                                 ;; if there are differences, they will be sent back
                                 (swap! state sync/patch-state diff)
                                 (do (log "Hash check failed --> complete sync")
                                     (a/put! from cs/complete-sync-request)))))
                     :complete (do 
                                 (logs "Complete sync:" (hasch/uuid (:state msg)))
                                 (reset! unacked-history {})
                                 (reset! server-state (:state msg))
                                 (reset! state (:state msg)))
                     (logs "Invalid msg:" msg))
                   (recur))
               (remove-watch state :state)))
    (when reset? (a/put! from cs/complete-sync-request))))
