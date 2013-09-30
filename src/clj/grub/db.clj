(ns grub.db
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def grub-collection "grubs")
(def recipe-collection "recipes")

(defn clear-grubs [] 
  (mc/drop grub-collection))

(defmulti handle-event :event :default :unknown-event)

(defmethod handle-event :add-grub [event]
  (let [grub (-> event
                 (select-keys [:_id :grub :completed]))]
    (mc/insert grub-collection grub)))

(defmethod handle-event :complete-grub [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:completed true}}))

(defmethod handle-event :uncomplete-grub [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:completed false}}))

(defmethod handle-event :update-grub [event]
  (mc/update grub-collection 
             {:_id (:_id event)}
             {mo/$set {:grub (:grub event)}}))

(defmethod handle-event :clear-all-grubs [event]
  (clear-grubs))

(defmethod handle-event :add-recipe [event]
  (let [recipe (select-keys event [:_id :name :steps])]
    (mc/insert recipe-collection recipe)))

(defmethod handle-event :update-recipe [event]
  (mc/update recipe-collection 
             {:_id (:_id event)}
             {mo/$set {:name (:name event) :grubs (:grubs event)}}))

(defmethod handle-event :unknown-event [event]
  (println "Cannot handle unknown event:" event))

(defn get-current-grubs-as-events []
  (let [grubs (mc/find-maps grub-collection)
        sorted-grubs (sort-by :_id (vec grubs))
        events (map (fn [g] (-> g
                                (select-keys [:_id :grub :completed])
                                (assoc :event :add-grub)))
                    sorted-grubs)
        out (chan)]
    (a/onto-chan out events)
    out))

(defn get-current-recipes-as-events []
  (let [recipes (mc/find-maps recipe-collection)
        sorted-recipes (sort-by :_id (vec recipes))
        events (map (fn [r] (-> r
                                (select-keys [:_id :name :grubs])
                                (assoc :event :add-recipe)))
                    sorted-recipes)
        out (chan)]
    (a/onto-chan out events)
    out))

(def production-db "grub")
(def development-db "grub-dev")

(defn handle-incoming-events [in]
  (a/go-loop [] (let [event (<! in)]
                  (handle-event event)
                  (recur))))

(defn connect-and-handle-events [db-name]
  (let [in (chan)]
    (handle-incoming-events in)
    (m/connect!)
    (m/set-db! (m/get-db db-name))
    in))

(defn connect-production-database []
  (connect-and-handle-events production-db))

(defn connect-development-database []
  (connect-and-handle-events development-db))
