(ns grub.db
  (:require [grub.async :refer [go-loop]]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as async :refer [<! >! >!! chan go close! timeout]]))

(def grub-collection "grubs")
(def recipe-collection "recipes")

(defn clear-grubs [] 
  (mc/drop grub-collection))

(def incoming-events (atom nil))

(defmulti handle-event :event :default :unknown-event)

(defmethod handle-event :add [event]
  (let [grub (-> event
                 (select-keys [:_id :grub :completed]))]
    (mc/insert grub-collection grub)))

(defmethod handle-event :complete [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:completed true}}))

(defmethod handle-event :uncomplete [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:completed false}}))

(defmethod handle-event :update [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:grub (:grub event)}}))

(defmethod handle-event :delete [event]
  (mc/remove grub-collection {:_id (:_id event)}))

(defmethod handle-event :clear-all [event]
  (clear-grubs))

(defmethod handle-event :add-recipe [event]
  (let [recipe (select-keys event [:_id :name :steps])]
    (mc/insert recipe-collection recipe)))

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

(defn get-current-recipes-as-events []
  (let [recipes (mc/find-maps recipe-collection)
        sorted-recipes (sort-by :_id (vec recipes))
        out (chan)]
    (go (doseq [recipe sorted-recipes]
          (let [recipe-event (-> recipe
                               (select-keys [:_id :name :steps])
                               (assoc :event :add-recipe))]
            (>! out recipe-event))))
    out))

(def default-db "grub")

(defn connect-and-handle-events
  ([] (connect-and-handle-events default-db))
  ([db-name]
     (handle-incoming-events!)
     (m/connect!)
     (m/set-db! (m/get-db db-name))))

(connect-and-handle-events default-db)
