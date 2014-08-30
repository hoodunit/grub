(ns grub.state
  (:require [grub.sync :as sync]
            [grub.common-state :as cs]
            [cljs.core.async :as a :refer [<! >! chan]]
            [hasch.core :as hasch])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def app-state (atom cs/empty-state))

(defn sync-state! [to from reset?]
  (let [client-shadow (atom cs/empty-state)]
    (add-watch app-state :app-state (fn [_ _ _ current-state] 
                                      (when-let [msg (cs/diff-states @client-shadow current-state)]
                                        (a/put! from msg)
                                        ;; TODO: reset only if send succeeds
                                        (reset! client-shadow current-state))))
    (go-loop [] 
             (if-let [{:keys [type diff hash shadow-hash] :as msg} (<! to)]
               (do (condp = type
                     :diff (let [new-shadow (swap! client-shadow #(sync/patch-state % diff))]
                             (if (= (hasch/uuid new-shadow) hash)
                               (swap! app-state #(sync/patch-state % diff))
                               (do (log "Hash check failed --> complete sync")
                                   (a/put! from cs/complete-sync-request))))
                     :complete (do (reset! client-shadow (:state msg))
                                   (reset! app-state (:state msg)))
                     (logs "Invalid msg:" msg))
                   (recur))
               (remove-watch app-state :app-state)))
    (when reset? (a/put! from cs/complete-sync-request))))
