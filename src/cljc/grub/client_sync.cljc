(ns grub.client-sync
  (:require [grub.diff :as diff]
            [grub.state :as state]
            [grub.event :as event]
    #?(:cljs [cljs.core.async :as a :refer [<! >! chan]]
       :clj [clojure.core.async :as a :refer [<! >! chan go]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(def DEBUG false)

(defn sync-client! [initial-state to-server ui-state-buffer diffs full-syncs connected ui-state]
  (go (loop [client-state initial-state
             server-state initial-state
             awaiting-ack? false]
        (let [channels (if awaiting-ack?
                         [diffs full-syncs connected]
                         [diffs full-syncs connected ui-state-buffer])
              [event ch] (a/alts! channels)]
          (when DEBUG (println event))
          (when-not (nil? event)
            (condp = ch
              full-syncs (let [{:keys [full-state]} event]
                           (reset! ui-state full-state)
                           (when DEBUG (println "Full sync, new ui state tag:" (:tag @ui-state)))
                           (recur full-state full-state false))
              ui-state-buffer (let [new-ui-state @ui-state]
                                (if (state/state= server-state new-ui-state)
                                  (recur server-state server-state false)
                                  (do
                                    (when DEBUG (println "Changes, current ui state tag:" (:tag new-ui-state)))
                                    (>! to-server (event/diff-msg server-state new-ui-state))
                                    (recur new-ui-state server-state true))))
              diffs (let [{:keys [diff]} event]
                      (if (= (:shadow-tag diff) (:tag server-state))
                        ;; Our state is based on what they think it's based on
                        (let [;; Update server state we are based on
                              new-server-state (diff/patch-state client-state diff)
                              ;; Apply changes directly to UI
                              new-client-state (swap! ui-state diff/patch-state diff)]
                          (when DEBUG (println "Applied diff, new ui tag:" (:tag new-client-state)))
                          (when DEBUG (println "Applied diff, new server tag:" (:tag new-server-state)))
                          ;; If there are any diffs to reconcile, they will come back through input buffer
                          (recur new-client-state new-server-state false))

                        ;; State mismatch, do full sync
                        (do (>! to-server (event/full-sync-request))
                            (recur client-state server-state true))))
              connected
              ;; Need to make sure we are in sync, send diff
              (do
                (when DEBUG (println "Reconnected, sending diff"))
                (>! to-server (event/diff-msg server-state @ui-state))
                (recur client-state server-state true))

              (throw "Bug: Received a sync event on an unknown channel")))))))

(defn start-sync! [to-server new-ui-states diffs full-syncs connected ui-state]
  (let [ui-state-buffer (chan (a/sliding-buffer 1))]
    (a/pipe new-ui-states ui-state-buffer)
    (go (<! connected)
        (>! to-server (event/full-sync-request))
        (let [full-sync-event (<! full-syncs)
              initial-state (:full-state full-sync-event)]
          (reset! ui-state initial-state)
          (sync-client! initial-state to-server ui-state-buffer diffs full-syncs connected ui-state)))))
