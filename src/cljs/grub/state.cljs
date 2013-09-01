(ns grub.state
  (:require [grub.async-utils :as a]
            [cljs.core.async :refer [chan <!]])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(def incoming-events (chan))
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

(defmethod handle-event :update [event]
  (swap! grubs
         (fn [current]
           (let [[grub-index grub] (get-grub-with-index current (:_id event))
                 updated-grub (assoc grub :grub (:grub event))]
             (assoc current grub-index updated-grub)))))

(defmethod handle-event :delete [event]
  (swap! grubs 
         (fn [current] 
           (vec (remove #(= (:_id %) (:_id event)) current)))))

(defmethod handle-event :clear-all [event]
  (reset! grubs []))

(defmethod handle-event :unknown-event [event]
  (logs "Cannot handle unknown event:" event))

(defn handle-incoming-events []
  (go-loop (let [event (<! incoming-events)]
             (handle-event event))))

(defn init []
  (handle-incoming-events))
