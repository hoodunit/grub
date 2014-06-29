(ns grub.state
  (:require [grub.view :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def default-app-state {:grubs {}
                        :recipes {}})

(defmulti handle-event (fn [event state] (:event event))
  :default :unknown-event)

(defmethod handle-event :unknown-event [event grubs]
  (logs "Cannot handle unknown event:" event)
  grubs)

(defn new-grub [id grub completed]
  {:id id :grub grub :completed completed})

(defmethod handle-event :add-grub [event grubs]
  (let [grub (new-grub (:id event) (:grub event) (:completed event))]
    (assoc grubs (:id grub) grub)))

(defn assoc-new-grub [current new]
  (assoc current (:id new)
    (new-grub (:id new) (:grub new) (:completed new))))

(defn make-add-grubs-map [grub-events]
  (reduce assoc-new-grub {} grub-events))

(defmethod handle-event :add-grub-list [event grubs]
  (let [add-grub-events (:grubs event)
        add-grubs (make-add-grubs-map add-grub-events)]
    (merge grubs add-grubs)))

(defmethod handle-event :complete-grub [event grubs]
  (assoc-in grubs [(:id event) :completed] true))

(defmethod handle-event :uncomplete-grub [event grubs]
  (assoc-in grubs [(:id event) :completed] false))

(defmethod handle-event :update-grub [event grubs]
  (assoc-in grubs [(:id event) :grub] (:grub event)))

(defmethod handle-event :clear-all-grubs [event grubs]
  {})

(defn update-state-and-render [remote-chan]
  (let [out (chan)]
    (go-loop [state default-app-state] 
             (let [event (<! remote-chan)
                   new-grubs (handle-event event (:grubs state))
                   new-state (assoc state :grubs new-grubs)]
               (logs "event:" event)
               (logs "new-state")
               (logs new-state)
               (view/render-body new-state)
               (recur new-state)))
    out))
