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
                         (:type event)))

(defmethod handle-event :diff [{:keys [hash diff states shadow client? state] :as msg}]
  (let [history-shadow (sync/get-history-state states hash)]
    (if history-shadow
      (let [new-state (swap! diff/patch-state state diff)
            new-states (sync/add-history-state states new-state)
            new-shadow (diff/patch-state history-shadow diff)
            new-diff (diff/diff-states new-shadow new-state)
            new-hash (hasch/uuid new-shadow)]
        {:out-event (when-not (sync/empty-diff? diff)
                      (message/diff-msg new-diff new-hash))
         :new-states new-states
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

(defmethod handle-event :full-sync [{:keys [full-state states state]}]
  (reset! state full-state)
  {:new-states (sync/new-state full-state)
   :new-shadow full-state})

(defmethod handle-event :default [msg]
  #+cljs (logs "Unhandled message:" msg)
  #+clj (println "Unhandled message:" msg)
  {})

(defn diff-msg [shadow state]
  (let [diff (diff/diff-states shadow state)
        hash (hasch/uuid shadow)]
    (message/diff-msg diff hash)))

(defn make-agent 
  ([client? >remote events new-states state]
     (make-agent client? >remote events new-states state sync/empty-state))
  ([client? >remote events new-states state initial-shadow]
     (go (loop [shadow initial-shadow
                states (sync/new-state @state)]
           (let [[v c] (a/alts! [new-states events] :priority true)]
             (cond (nil? v) nil ;; drop out of loop
                   (= c new-states)
                   (do (when-not (= shadow v)
                         (>! >remote (diff-msg shadow v)))
                       (recur shadow (sync/add-history-state states v)))
                   (= c events)
                   (let [event (assoc v 
                                 :states states 
                                 :client? client? 
                                 :shadow shadow
                                 :state state)
                         {:keys [new-states new-shadow out-event]} (handle-event event)]
                     (when out-event (a/put! >remote out-event))
                     (recur (if new-shadow new-shadow shadow)
                            (if new-states new-states states)))))))))

(def make-server-agent (partial make-agent false))
(def make-client-agent (partial make-agent true))

#+clj
(defn sync-new-client! [>remote events state]
  (let [client-id (java.util.UUID/randomUUID)
        new-states (chan)]
    (add-watch state client-id (fn [_ _ old new] 
                                 (when-not (= old new)
                                   (a/put! new-states new))))
    (make-server-agent >remote events new-states state)))

#+cljs
(defn sync-client! [>remote events new-states state]
  (make-client-agent >remote events new-states state)
  (a/put! >remote message/full-sync-request))
