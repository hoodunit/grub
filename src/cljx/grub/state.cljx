(ns grub.state
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.sync :as sync]
            #+clj [clojure.core.async :as a :refer [<! >! chan go]]
            #+cljs [cljs.core.async :as a :refer [<! >! chan]])
  #+cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]]))

(defmulti handle-event (fn [event] (:type event)))

(defmethod handle-event :diff [{:keys [hash diff states shadow client?] :as msg}]
  (let [shadow (sync/get-history-state states hash)]
    (if shadow
      (let [new-states (sync/apply-diff states diff)
            new-shadow (diff/patch-state shadow diff)
            {new-diff :diff new-hash :hash} (sync/diff-states (sync/get-current-state new-states) new-shadow)]
        (if client?
          {:new-states (sync/new-state (sync/get-current-state new-states))
           :new-shadow new-shadow}
          {:out-event (when-not (sync/empty-diff? diff)
                        (message/diff-msg new-diff new-hash))
           :new-states new-states
           :new-shadow (sync/get-current-state new-states)}))
      (if client?
        {:out-event message/full-sync-request
         :new-shadow shadow}
        (let [state (sync/get-current-state states)]
          {:out-event (message/full-sync state)
           :new-shadow state})))))

(defmethod handle-event :full-sync-request [{:keys [states]}]
  (let [state (sync/get-current-state states)]
    {:new-shadow state
     :out-event (message/full-sync state)}))

(defmethod handle-event :full-sync [{:keys [state states]}]
  {:new-states (sync/new-state state)
   :new-shadow state})

(defmethod handle-event :new-state [{:keys [state states shadow]}]
  (let [{:keys [diff hash]} (sync/diff-states state shadow)]
    {:new-shadow shadow
     :out-event (when-not (sync/empty-diff? diff) (message/diff-msg diff hash))}))

(defn make-agent 
  ([client? <remote >remote states*] (make-agent client? <remote >remote states* sync/empty-state))
  ([client? <remote >remote states* initial-shadow]
     (go (loop [shadow initial-shadow]
           (when-let [msg (<! <remote)]
             (let [states @states*
                   event (assoc msg :states states :client? client? :shadow shadow)
                   {:keys [new-states new-shadow out-event]} (handle-event event)]
               (when (and new-states (not= states new-states)) (reset! states* new-states))
               (when out-event (a/put! >remote out-event))
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
                   (do (remove-watch states client-id)
                       (a/close! <client)
                       (a/close! state-change-events)))))
    (make-server-agent client-events >client states)))

#+clj
(defn init-server [to-db initial-state]
  (reset! states (sync/new-state initial-state))
  (add-watch states :to-db (fn [_ _ old-states new-states] 
                             (a/put! to-db (sync/get-current-state new-states)))))

#+cljs
(defn init-client [<remote >remote <view >view]
  (let [states (atom (sync/initial-state {} {}))]
    (add-watch states :render (fn [_ _ _ new-states]
                                (let [new-state (sync/get-current-state new-states)]
                                  (a/put! >view new-state))))
    (a/pipe (a/map< (fn [s] 
                      (swap! states sync/add-history-state s)
                      {:type :new-state :state s}) <view) <remote)
    (make-client-agent <remote >remote states)
    (a/put! >remote message/full-sync-request)))
