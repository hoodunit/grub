(ns grub.server-sync
  (:require [grub.diff :as diff]
            [grub.state :as state]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [grub.db :as db]
            [clojure.pprint :refer [pprint]]))

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
     :diff diff
     :tag (:tag state)
     :shadow-tag (:tag shadow)}))

(defmulti handle-event (fn [event] (:type event)))

(defn apply-diff [states diff shadow new-shadow-tag]
  (let [new-states (swap! states update-states diff)
        new-state (state/get-latest new-states)
        new-shadow (assoc (diff/patch-state shadow diff)
                     :tag new-shadow-tag)]
    {:new-shadow new-shadow
     ;; Workaround to send an "ACK" diff when there are no changes
     :out-event  (when (state/state= new-state new-shadow)
                   (diff-msg new-shadow new-state))}))

(defmethod handle-event :diff [{:keys [diff states shadow-tag tag]}]
  (let [history-shadow (state/get-tagged @states shadow-tag)]
    (if history-shadow
      (apply-diff states diff history-shadow tag)
      (let [state (state/get-latest @states)]
        {:out-event  (full-sync state)
         :new-shadow state}))))

(defmethod handle-event :full-sync-request [{:keys [states]}]
  (let [state (state/get-latest @states)]
    {:new-shadow state
     :out-event (full-sync state)}))

(defmethod handle-event :new-state [{:keys [shadow states new-state]}]
  (let [new-states (swap! states state/add new-state)
        latest-state (state/get-latest new-states)]
    {:out-event  (when-not (state/state= shadow latest-state)
                   (diff-msg shadow latest-state))
     :new-shadow (when-not (state/state= shadow latest-state)
                   (assoc latest-state :tag (inc (:tag shadow))))}))

(defmethod handle-event :default [msg]
  (println "Unhandled message:" msg)
  {})

(defn make-server-agent
  ([up down saved db-conn]
   (go (loop [shadow (db/get-current-state db-conn)]
         (let [[event c] (a/alts! [up saved] :priority true)]
           (println "Handling event:")
           (pprint event)
           (when-not (nil? event)
             (case (:type event)

               :diff
               (let [history-state (db/get-history-state db-conn (:shadow-tag event))
                     new-state (db/patch-state! db-conn (:diff event))
                     new-shadow (diff/patch-state history-state (:diff event))
                     return-diff (diff/diff-states new-shadow new-state)]
                 (>! down return-diff)
                 (recur new-shadow))

               :full-sync-request
               (do (println "full sync!")
                   (>! down (full-sync (db/get-current-state db-conn)))
                   (recur shadow))
               (do (println "Unhandled event")
                   (println event)
                   (recur shadow)))))))))
