(ns grub.state
  (:require [cljs.core.async :refer [chan <!]])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(def incoming-events (chan))
(def incoming-view-events (chan))
(def outgoing-events (chan))

(def grubs (atom []))

(defn get-grub-with-index [grubs id]
  (let [grub-index (->> grubs
                        (map-indexed vector)
                        (filter #(= (:_id (second %)) id))
                        (first)
                        (first))
        grub (grubs grub-index)]
    [grub-index grub]))

(defmulti handle-event :event :default :unknown-event)

(defmethod handle-event :add [event]
  (let [grub (select-keys event [:_id :grub :completed])]
    (swap! grubs (fn [current] (conj current grub)))))

(defmethod handle-event :complete [event]
  (swap! grubs
         (fn [current]
           (let [[grub-index grub] (get-grub-with-index current (:_id event))
                 completed-grub (assoc grub :completed true)]
             (assoc current grub-index completed-grub)))))

(defmethod handle-event :uncomplete [event]
  (swap! grubs
         (fn [current]
           (let [[grub-index grub] (get-grub-with-index current (:_id event))
                 incomplete-grub (assoc grub :completed false)]
             (assoc current grub-index incomplete-grub)))))

(defmethod handle-event :delete [event]
  (swap! grubs 
         (fn [current] 
           (vec (remove #(= (:_id %) (:_id event)) current)))))

(defmethod handle-event :clear-all [event]
  (reset! grubs []))

(defmethod handle-event :unknown-event [event]
  (logs "Cannot handle unknown event:" event))



(defn pass-on-view-event [event]
    (go (>! incoming-events event))
    (go (>! outgoing-events event)))

(defmulti handle-view-event :event :default :unknown-event)

(defmethod handle-view-event :create [event]
  (let [create-event (-> event
                         (assoc :event :add)
                         (assoc :_id (str "grub-" (.now js/Date)))
                         (assoc :completed false))]
    (pass-on-view-event create-event)))

(defmethod handle-view-event :unknown-event [event]
  (pass-on-view-event event))


(defn handle-incoming-events []
    (go-loop (handle-event (<! incoming-events))))

(defn handle-incoming-view-events []
  (go-loop (handle-view-event (<! incoming-view-events))))
  
(defn init []
  (handle-incoming-events)
  (handle-incoming-view-events))

(init)
