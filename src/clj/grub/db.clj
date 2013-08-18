(ns grub.db
  (:require [grub.async :refer [go-loop]]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as async :refer [<! >! >!! chan go close! timeout]]))

(def grub-collection "grubs")

(def incoming-events (atom nil))

(defmulti handle-event :event :default :unknown-event)

(defmethod handle-event :create [event]
  (let [grub (-> event
                 (select-keys [:_id :grub])
                 (assoc :completed false))]
    (mc/insert grub-collection grub)))

(defmethod handle-event :complete [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:completed true}}))

(defmethod handle-event :uncomplete [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:completed false}}))

(defmethod handle-event :delete [event]
  (mc/remove grub-collection {:_id (:_id event)}))

(defmethod handle-event :unknown-event [event]
  (println "Cannot handle unknown event:" event))

(defn handle-incoming-events! []
  (reset! incoming-events (chan))
  (go-loop (let [event (<! @incoming-events)]
             (handle-event event))))

(defn get-current-grubs-as-events []
  (let [grubs (mc/find-maps grub-collection)
        sorted-grubs (sort-by :_id (vec grubs))
        out (chan)]
    (go (doseq [grub sorted-grubs]
          (let [grub-event (-> grub
                               (select-keys [:_id :grub :completed])
                               (assoc :event :add))]
            (>! out grub-event))))
    out))

(def default-db "grub")

(defn connect-and-handle-events
  ([] (connect-and-handle-events default-db))
  ([db-name]
     (handle-incoming-events!)
     (m/connect!)
     (m/set-db! (m/get-db db-name))))

(connect-and-handle-events default-db)
