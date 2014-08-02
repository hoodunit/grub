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

(defn clear-recipes [] 
  (mc/drop @db recipe-collection))

(defn clear-all []
  (clear-grubs)
  (clear-recipes))

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
  (let [orig (mc/find-one-as-map @db grub-collection {:_id (:id event)})
        new (dissoc event :event-type :id)]
    (mc/update-by-id @db grub-collection (:id event) (merge orig new))))

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
  (->> (mc/find-maps @db grub-collection)
       (sort-by :_id)
       (map #(select-keys % [:_id :grub :completed]))
       (map #(clojure.set/rename-keys % {:_id :id}))
       (vec)))

(defn get-current-recipes []
  (->> (mc/find-maps @db recipe-collection)
       (sort-by :_id)
       (map #(select-keys % [:_id :name :grubs]))
       (map #(clojure.set/rename-keys % {:_id :id}))
       (vec)))

(def production-db "grub")
(def development-db "grub-dev")

(defn handle-incoming-events [in]
  (a/go-loop [] (let [event (<! in)]
                  (handle-event event)
                  (recur))))

(defn connect! [db-name mongo-url]
  (if mongo-url
    (do (println "Connected to mongo via url:" mongo-url)
        (:conn (m/connect-via-uri mongo-url)))
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
