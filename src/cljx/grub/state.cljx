(ns grub.state
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.sync :as sync]
            #+clj [clojure.core.async :as a :refer [<! >! chan go]]
            #+cljs [cljs.core.async :as a :refer [<! >! chan]])
  #+cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]]))

(defn make-agent 
  ([client? in out states] (make-agent client? in out states sync/empty-state))
  ([client? in out states initial-shadow]
     (go (loop [shadow initial-shadow]
           (when-let [msg (<! in)]
             (condp = (:type msg)
               :diff
               (let [states* @states
                     shadow (sync/get-history-state states* (:hash msg))]
                 #+cljs (logs "diff msg:" msg)
                 #+clj (println "diff msg:" msg)
                 (if shadow
                   (let [new-states (sync/apply-diff states* (:diff msg))
                         new-shadow (diff/patch-state shadow (:diff msg))
                         {:keys [diff hash]} (sync/diff-states (sync/get-current-state new-states) new-shadow)]
                     (reset! states new-states)
                     (when-not client? (>! out (message/diff-msg diff hash))) ;; HERE
                     (recur new-shadow))
                   (if client?
                     (do (>! out message/full-sync-request)
                         (recur shadow))
                     (let [state (sync/get-current-state states*)]
                       (>! out (message/full-sync state))
                       (recur state)))))

               :full-sync
               (if client?
                 (let [state (:state msg)]
                   #+cljs (logs "received full sync")
                   (reset! states (sync/new-state state))
                   (recur state))
                 (let [state (sync/get-current-state @states)]
                   #+clj (println "sending full sync")
                   (>! out (message/full-sync state)) ;; HERE
                   (recur state)))

               :new-state
               (let [{:keys [diff hash]} (sync/diff-states (:state msg) shadow)]
                 #+cljs (logs "new state")
                 #+clj (println "new state")
                 (if client?
                   (when-not (sync/empty-diff? diff)
                     (>! out (message/diff-msg diff hash)))
                   (>! out (message/diff-msg diff hash)))
                 (recur shadow))
               (recur shadow)))))))

(defn make-server-agent
  ([in out states] (make-agent false in out states))
  ([in out states initial-shadow] (make-agent false in out states initial-shadow)))

(defn make-client-agent
  ([in out states] (make-agent true in out states))
  ([in out states initial-shadow] (make-agent true in out states initial-shadow)))

(def states (atom []))
(def empty-state sync/empty-state)

#+clj
(defn sync-new-client! [>client <client]
  (let [client-id (java.util.UUID/randomUUID)
        state-changes (chan)
        state-change-events (a/map< (fn [s] {:type :new-state :state s}) state-changes)
        client-events (chan)]
    (add-watch states client-id (fn [_ _ _ new-states] 
                                  (a/put! state-changes (sync/get-current-state new-states))))
    (a/go-loop []
               (let [[val _] (a/alts! [<client state-change-events])]
                 (if val
                  (do (>! client-events val)
                      (recur))
                  (do #+clj (println "client disconnected, clean up")
                      (remove-watch states client-id)
                      (a/close! <client)
                      (a/close! state-change-events)))))
    (make-server-agent client-events >client states)))

#+clj
(defn init-server [to-db initial-state]
  (reset! states (sync/new-state initial-state))
  (add-watch states :to-db (fn [_ _ old-states new-states] 
                             (a/put! to-db (sync/get-current-state new-states)))))

#+cljs
(defn init-client [in out state-changes current-state]
  (reset! states (sync/initial-state {} {}))
  (add-watch states :render (fn [_ _ _ new-states]
                              (let [new-state (sync/get-current-state new-states)]
                                (reset! current-state new-state))))
  (a/pipe (a/map< (fn [s] 
                    (swap! states sync/add-history-state s)
                    {:type :new-state :state s}) state-changes) in)
  (make-client-agent in out states)
  (a/put! out message/full-sync-request))
