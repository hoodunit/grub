(ns grub.state
  (:require [grub.sync :as sync]
            [cljs.core.async :as a :refer [<! >! chan]]
            [hasch.core :as hasch])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def empty-state {:grubs {} :recipes {}})
(def app-state (atom empty-state))
(def client-shadow (atom empty-state))

(defn update-state-on-event! [in]
  (let [out (chan)]
    (go-loop [] 
             (when-let [{:keys [diff hash shadow-hash]} (<! in)]
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
                   (log "After hash check: FAIL"))
                 (recur))))
    out))
