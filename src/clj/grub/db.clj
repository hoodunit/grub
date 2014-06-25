(ns grub.db
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def conn (atom nil))
(def db (atom nil))
(def grub-collection "grubs")
(def recipe-collection "recipes")

(defn clear-grubs [] 
  (mc/drop @db grub-collection))

(defmulti handle-event :event :default :unknown-event)

(defn insert-grub [event]
  (let [grub (-> event
                 (select-keys [:id :grub :completed])
                 (clojure.set/rename-keys {:id :_id}))]
    (mc/insert @db grub-collection grub)))

(defmethod handle-event :add-grub [event]
  (insert-grub event))

(defmethod handle-event :add-grub-list [event]
  (doseq [grub-event (:grubs event)]
    (insert-grub grub-event)))

(defmethod handle-event :complete-grub [event]
  (mc/update @db grub-collection 
             {:_id (:id event)}
             {mo/$set {:completed true}}))

(defmethod handle-event :uncomplete-grub [event]
  (mc/update @db grub-collection 
             {:_id (:id event)}
             {mo/$set {:completed false}}))

(defmethod handle-event :update-grub [event]
  (mc/update @db grub-collection 
             {:_id (:id event)}
             {mo/$set {:grub (:grub event)}}))

(defmethod handle-event :clear-all-grubs [event]
  (clear-grubs))

(defmethod handle-event :add-recipe [event]
  (let [recipe (-> event
                   (select-keys [:id :name :grubs])
                   (clojure.set/rename-keys {:id :_id}))]
    (mc/insert @db recipe-collection recipe)))

(defmethod handle-event :update-recipe [event]
  (mc/update @db recipe-collection 
             {:_id (:id event)}
             {mo/$set {:name (:name event) :grubs (:grubs event)}}))

(defmethod handle-event :unknown-event [event]
  (println "Cannot handle unknown event:" event))

(defn get-current-grubs []
  (let [raw-grubs (mc/find-maps @db grub-collection)
        sorted-grubs (sort-by :_id (vec raw-grubs))
        grubs (map (fn [g] (-> g
                                (select-keys [:_id :grub :completed])
                                (clojure.set/rename-keys {:_id :id})))
                    sorted-grubs)]
    grubs))

(defn get-current-recipes []
  (let [raw-recipes (mc/find-maps @db recipe-collection)
        sorted-recipes (sort-by :_id (vec raw-recipes))
        recipes (map (fn [r] (-> r
                                (select-keys [:_id :name :grubs])
                                (clojure.set/rename-keys {:_id :id})))
                    sorted-recipes)]
    recipes))

(def production-db "grub")
(def development-db "grub-dev")

(defn handle-incoming-events [in]
  (a/go-loop [] (let [event (<! in)]
                  (handle-event event)
                  (recur))))

(defn connect! [db-name mongo-url]
  (if mongo-url
    (do (println "Connected to mongo via url:" mongo-url)
        (m/connect-via-uri mongo-url))
    (do (println "Connected to mongo at localhost:" db-name)
        (m/connect))))

(defn connect-and-handle-events [db-name & [mongo-url]]
  (let [in (chan)]
    (handle-incoming-events in)
    (let [_conn (connect! db-name mongo-url)]
      (reset! conn _conn)
      (reset! db (m/get-db _conn db-name)))
    in))

(defn connect-production-database [mongo-url]
  (connect-and-handle-events production-db mongo-url))

(defn connect-development-database []
  (connect-and-handle-events development-db))
