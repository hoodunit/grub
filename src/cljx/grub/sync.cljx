(ns grub.sync
  (:require [grub.diff :as diff]
            [grub.state :as state]
            #+clj [clojure.core.async :as a :refer [<! >! chan go]]
            #+cljs [cljs.core.async :as a :refer [<! >! chan]])
  #+cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]]))

(def full-sync-request {:type :full-sync-request})

(defn full-sync [state]
  {:type :full-sync
   :full-state state})

(def empty-state state/empty-state)

(defn update-states [states diff]
  (let [state (state/get-latest states)
        new-state (diff/patch-state state diff)]
    (state/add states new-state)))

(defn diff-msg [shadow state]
  (let [diff (diff/diff-states shadow state)]
    {:type :diff
     :diff diff}))

(defmulti handle-event (fn [event] (:type event)))

(defmethod handle-event :diff [{:keys [diff states shadow client?]}]
  (let [history-shadow (state/get-tagged @states (:shadow-tag diff))]
    (if history-shadow
      (let [new-states (swap! states update-states diff)
            new-state (state/get-latest new-states)
            new-shadow (diff/patch-state history-shadow diff true)]
        {:out-event (when-not (state/state= history-shadow new-state)
                      (diff-msg new-shadow new-state))
         :new-states new-states
         :new-shadow new-shadow})
      (if client?
        {:out-event full-sync-request
         :new-shadow shadow}
        (let [state (state/get-latest @states)]
          {:out-event (full-sync state)
           :new-shadow state})))))

(defmethod handle-event :full-sync-request [{:keys [states]}]
  (let [state (state/get-latest @states)]
    {:new-shadow state
     :out-event (full-sync state)}))

(defmethod handle-event :full-sync [{:keys [full-state states]}]
  (reset! states (state/new-states full-state))
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
                   (do (when-not (state/state= shadow v)
                         (swap! states state/add v)
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
            (>! new-states* v)
            (recur))))
    (make-client-agent >remote events new-states* states)
    (a/put! >remote full-sync-request)))
