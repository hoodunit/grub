(ns grub.state
  (:require [grub.sync :as sync]
            [grub.common-state :as cs]
            [cljs.core.async :as a :refer [<! >! chan]]
            [hasch.core :as hasch])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def app-state (atom cs/empty-state))

(defn sync-state! [to from]
  (let [client-shadow (atom cs/empty-state)]
    (add-watch app-state :app-state (fn [_ _ _ new] 
                                      (when-let [msg (cs/diff-states new @client-shadow)]
                                        (a/put! from msg)
                                        ;; TODO: reset shadow only if send succeeds
                                        (reset! client-shadow new))))
    (go-loop [] 
             (when-let [{:keys [type diff hash shadow-hash] :as msg} (<! to)]
               (condp = type
                 :diff (do
                         ;(logs "Received server diff:" shadow-hash "->" hash)
                         ;(logs "Before shadow:" (hasch/uuid @client-shadow) @client-shadow)
                         (if (= (hasch/uuid @client-shadow) shadow-hash)
                           (log "Before hash check: good")
                           (log "Before hash check: FAIL"))
                         (let [new-shadow (swap! client-shadow #(sync/patch-state % diff))
                               new-state (swap! app-state #(sync/patch-state % diff))]
                           ;(logs "After shadow:" (hasch/uuid @client-shadow) @client-shadow)
                           (if (= (hasch/uuid new-shadow) hash)
                             (log "After hash check: good")
                             (log "After hash check: FAIL"))))
                 :complete (do (reset! client-shadow (:state msg))
                               (reset! app-state (:state msg)))
                 (logs "Invalid msg:" msg))
               (recur)))
    (a/put! from cs/complete-sync-request)))
