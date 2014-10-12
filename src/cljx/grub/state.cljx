(ns grub.state
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.sync :as sync]
            [hasch.core :as hasch]
            #+clj [clojure.core.async :as a :refer [<! >! chan go]]
            #+cljs [cljs.core.async :as a :refer [<! >! chan]])
  #+cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]]))

(def empty-state sync/empty-state)

(defmulti handle-event (fn [event] 
                         #+cljs (logs (:type event))
                         (:type event)))

(defmethod handle-event :diff [{:keys [hash diff states shadow client?] :as msg}]
  (let [history-shadow (sync/get-history-state states hash)]
    (if history-shadow
      (let [state (sync/get-current-state states)
            new-state (diff/patch-state state diff)
            new-states (sync/add-history-state states new-state)
            new-shadow (diff/patch-state history-shadow diff)
            new-diff (diff/diff-states new-shadow new-state)
            new-hash (hasch/uuid new-shadow)]
        {:out-event (when-not (sync/empty-diff? diff)
                      (message/diff-msg new-diff new-hash))
         :new-states (if client?
                       (sync/new-state new-state)
                       new-states)
         :new-shadow new-shadow})
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

(defmethod handle-event :new-state [{:keys [client? state states shadow] :as event}]
  (let [diff (diff/diff-states shadow state)
        hash (hasch/uuid shadow)]
    {:new-states (sync/add-history-state states state)
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
               (recur (if new-shadow new-shadow shadow))))))))

(defn make-server-agent
  ([<remote >remote states] (make-agent false <remote >remote states))
  ([<remote >remote states initial-shadow] (make-agent false <remote >remote states initial-shadow)))

(defn make-client-agent
  ([<remote >remote states] (make-agent true <remote >remote states))
  ([<remote >remote states initial-shadow] (make-agent true <remote >remote states initial-shadow)))

#+clj
(defn sync-new-client! [>client <client states]
  (let [client-id (java.util.UUID/randomUUID)
        state-change-events (chan 1 (map (fn [s] {:type :new-state :state s})))
        client-events (chan)]
    (add-watch states client-id 
               (fn [_ _ _ new-states] 
                 (a/put! state-change-events (sync/get-current-state new-states))))
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
  (let [states (atom (sync/new-state initial-state))]
    (add-watch states :to-db (fn [_ _ old-states new-states] 
                               (a/put! to-db (sync/get-current-state new-states))))
    states))

#+cljs
(defn init-client [<remote >remote <view >view]
  (let [states (atom (sync/initial-state {} {}))
        local-events (chan 1 (map (fn [s] {:type :new-state :state s})))]
    (add-watch states :render (fn [_ _ _ new-states]
                                (let [new-state (sync/get-current-state new-states)]
                                  (a/put! >view new-state))))
    (a/pipe <view local-events)
    (make-client-agent (a/merge [local-events <remote]) >remote states)
    (a/put! >remote message/full-sync-request)
    states))
