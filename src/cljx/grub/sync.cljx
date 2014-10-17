(ns grub.sync
  (:require [grub.diff :as diff]
            [grub.message :as message]
            [grub.state :as state]
            [hasch.core :as hasch]
            #+clj [clojure.core.async :as a :refer [<! >! chan go]]
            #+cljs [cljs.core.async :as a :refer [<! >! chan]])
  #+cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]]))

(def empty-state state/empty-state)

(defn update-states [states diff]
  (let [state (state/get-current-state states)
        new-state (diff/patch-state state diff)]
    (state/add-history-state states new-state)))

(defn diff-msg [shadow state]
  (let [diff (diff/diff-states shadow state)
        hash (hasch/uuid shadow)]
    (message/diff-msg diff hash)))

(defmulti handle-event (fn [event] (:type event)))

(defmethod handle-event :diff [{:keys [hash diff states shadow client?]}]
  (let [history-shadow (state/get-history-state @states hash)]
    (if history-shadow
      (let [new-states (swap! states update-states diff)
            new-state (state/get-current-state new-states)
            new-shadow (diff/patch-state history-shadow diff)]
        {:out-event (when-not (state/empty-diff? diff)
                      (diff-msg new-shadow new-state))
         :new-states new-states
         :new-shadow new-shadow})
      (if client?
        {:out-event message/full-sync-request
         :new-shadow shadow}
        (let [state (state/get-current-state states)]
          {:out-event (message/full-sync state)
           :new-shadow state})))))

(defmethod handle-event :full-sync-request [{:keys [states]}]
  (let [state (state/get-current-state @states)]
    {:new-shadow state
     :out-event (message/full-sync state)}))

(defmethod handle-event :full-sync [{:keys [full-state states]}]
  (reset! states (state/new-state full-state))
  {:new-shadow full-state})

(defmethod handle-event :default [msg]
  #+cljs (logs "Unhandled message:" msg)
  #+clj (println "Unhandled message:" msg)
  {})

(defn make-agent 
  ([client? >remote events new-states states]
     (make-agent client? >remote events new-states states state/empty-state))
  ([client? >remote events new-states states initial-shadow]
     (go (loop [shadow initial-shadow]
           (let [[v c] (a/alts! [new-states events] :priority true)]
             (cond (nil? v) nil ;; drop out of loop
                   (= c new-states)
                   (do (when-not (= shadow v)
                         (swap! states state/add-history-state v)
                         (>! >remote (diff-msg shadow v)))
                       (recur shadow))
                   (= c events)
                   (let [event (assoc v 
                                 :states states 
                                 :client? client? 
                                 :shadow shadow)
                         {:keys [new-shadow out-event]} (handle-event event)]
                     (when out-event (a/put! >remote out-event))
                     (recur (if new-shadow new-shadow shadow)))))))))

(def make-server-agent (partial make-agent false))
(def make-client-agent (partial make-agent true))

#+clj
(defn sync-new-client! [>remote events new-states states]
  (make-server-agent >remote events new-states states))

#+cljs
(defn sync-client! [>remote events new-states states]
  (let [new-states* (chan)]
    (go (loop []
          (let [v (<! new-states)]
            (<! (a/timeout 1000))
            (jk>! new-states* v)
            (recur))))
    (make-client-agent >remote events new-states* states)
    (a/put! >remote message/full-sync-request)))
