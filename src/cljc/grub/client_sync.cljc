(ns grub.client-sync
  (:require [grub.diff :as diff]
            [grub.state :as state]
            #?(:cljs [cljs.core.async :as a :refer [<! >! chan]]
               :clj [clojure.core.async :as a :refer [<! >! chan go]]))
  #?(:cljs (:require-macros [grub.macros :refer [log logs]]
                          [cljs.core.async.macros :refer [go]])))

(def full-sync-request {:type :full-sync-request})

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
  (swap! states update-states diff)
  (let [new-shadow (assoc (diff/patch-state shadow diff) :tag new-shadow-tag)]
    {:new-shadow new-shadow :out-event nil}))

(defmethod handle-event :diff [{:keys [diff states shadow shadow-tag tag]}]
  (let [history-shadow (state/get-tagged @states shadow-tag)]
    (if history-shadow
      (apply-diff states diff history-shadow tag)
      {:out-event  full-sync-request
       :new-shadow shadow} )))

(defmethod handle-event :full-sync [{:keys [full-state states]}]
  (reset! states (state/new-states full-state))
  {:new-shadow full-state})

(defmethod handle-event :new-state [{:keys [shadow states new-state]}]
  (let [new-states (swap! states state/add new-state)
        latest-state (state/get-latest new-states)]
    {:out-event (when-not (state/state= shadow latest-state)
                  (diff-msg shadow latest-state))
     :new-shadow nil}))

(defmethod handle-event :default [msg]
  #?(:cljs (logs "Unhandled message:" msg))
  {})

(defn make-client-agent
  ([>remote events new-states states]
     (make-client-agent >remote events new-states states state/empty-state))
  ([>remote events new-states states initial-shadow]
     (go (loop [shadow initial-shadow
                out-event nil]
           (when out-event (>! >remote out-event))
           (let [timeout (a/timeout 1000)
                 [v c] (if out-event
                         (a/alts! [events timeout])
                         (a/alts! [new-states events] :priority true))]
             (cond (= c timeout) (recur shadow out-event)
                   (nil? v) nil ;; drop out of loop
                   (= c new-states)
                   (let [event {:type :new-state
                                :new-state v
                                :shadow shadow
                                :states states}
                         {:keys [out-event]} (handle-event event)]
                     (recur shadow out-event))
                   (= c events)
                   (let [event (assoc v 
                                 :states states 
                                 :shadow shadow)
                         {:keys [new-shadow out-event]} (handle-event event)]
                     (recur (if new-shadow new-shadow shadow) out-event))))))))

#?(:cljs
   (defn sync-client! [>remote events new-states states]
         (let [new-states* (chan (a/sliding-buffer 1))]
              (go (loop []
                        (let [v (<! new-states)]
                             (>! new-states* v)
                             (recur))))
              (make-client-agent >remote events new-states* states)
              (a/put! >remote full-sync-request))))
