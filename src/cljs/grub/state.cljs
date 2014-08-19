(ns grub.state
  (:require [grub.sync :as sync]
            [grub.common-state :as cs]
            [cljs.core.async :as a :refer [<! >! chan]]
            [hasch.core :as hasch])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def app-state (atom cs/empty-state))
(def client-shadow (atom cs/empty-state))

(defn sync-local-changes [to-remote state*]
  (let [client-shadow* @client-shadow]
    (when (not= state* client-shadow*)
      (let [diff (sync/diff-states client-shadow* state*)
            msg {:type :diff
                 :diff diff 
                 :hash (hasch/uuid state*)
                 :shadow-hash (hasch/uuid client-shadow*)}]
        (logs "Sync because:")
        (logs "Server = " client-shadow*)
        (logs "Client = " state*)
        (logs "Diff:" diff)
        (logs "Send" (hasch/uuid client-shadow*) "->" (hasch/uuid state*))
        ;; TODO: reset client shadow only if send succeeds
        (a/put! to-remote msg)
        (reset! client-shadow state*)))))

(defn sync-state! [to from]
  (go-loop [] 
           (when-let [{:keys [type diff hash shadow-hash] :as msg} (<! to)]
             (condp = type
               :diff (do
                       (logs "Received server diff:" shadow-hash "->" hash)
                       (logs "Before shadow:" (hasch/uuid @client-shadow) @client-shadow)
                       (if (= (hasch/uuid @client-shadow) shadow-hash)
                         (log "Before hash check: good")
                         (log "Before hash check: FAIL"))
                       (let [new-shadow (swap! client-shadow #(sync/patch-state % diff))
                             new-state (swap! app-state #(sync/patch-state % diff))]
                         (logs "After shadow:" (hasch/uuid @client-shadow) @client-shadow)
                         (if (= (hasch/uuid new-shadow) hash)
                           (log "After hash check: good")
                           (log "After hash check: FAIL"))))
               :complete (do (log "Received complete sync, reset state")
                             (logs msg)
                             (reset! client-shadow (:state msg))
                             (reset! app-state (:state msg)))
               (logs "Invalid msg:" msg))
             (recur)))
  (add-watch app-state :app-state (fn [k ref old new] (sync-local-changes from new)))
  (a/put! from cs/complete-sync-request))
