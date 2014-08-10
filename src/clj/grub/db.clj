(ns grub.db
  (:require [grub.util :as util]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def conn (atom nil))
(def db (atom nil))
(def grub-collection "grubs")
(def recipe-collection "recipes")
(def production-db "grub")
(def development-db "grub-dev")

(defn clear-grubs [] 
  (mc/drop @db grub-collection))

(defn clear-recipes [] 
  (mc/drop @db recipe-collection))

(defn clear-all []
  (clear-grubs)
  (clear-recipes))

(defn update-db! [{:keys [grubs recipes]}]
  (let [deleted-grubs (:deleted grubs)
        updated-grubs (->> (:updated grubs)
                           (seq)
                           (map (fn [[k v]] 
                                  (-> v
                                      (dissoc :id)
                                      (assoc :_id k)))))
        deleted-recipes (:deleted recipes)
        updated-recipes (->> (:updated recipes)
                             (seq)
                             (map (fn [[k v]] 
                                    (-> v
                                        (dissoc :id)
                                        (assoc :_id k)))))]
    (doseq [g deleted-grubs] 
      (mc/remove-by-id @db grub-collection g))
    (doseq [g updated-grubs] 
      (mc/update-by-id @db grub-collection (:_id g) g {:upsert true}))
    (doseq [r deleted-recipes]
      (mc/remove-by-id @db recipe-collection r))
    (doseq [r updated-recipes]
      (mc/update-by-id @db recipe-collection (:_id r) r {:upsert true}))))

(defn get-current-grubs []
  (->> (mc/find-maps @db grub-collection)
       (sort-by :_id)
       (map #(clojure.set/rename-keys % {:_id :id}))))

(defn get-current-recipes []
  (->> (mc/find-maps @db recipe-collection)
       (sort-by :_id)
       (map #(clojure.set/rename-keys % {:_id :id}))))

(defn connect! [db-name mongo-url]
  (if mongo-url
    (do (println "Connected to mongo via url:" mongo-url)
        (:conn (m/connect-via-uri mongo-url)))
    (do (println "Connected to mongo at localhost:" db-name)
        (m/connect))))

(defn connect-and-handle-events [to-db db-name & [mongo-url]]
  (a/go-loop []
             (if-let [diff (<! to-db)]
               (do (update-db! diff)
                   (recur))
               (println "Database disconnected")))
  (let [_conn (connect! db-name mongo-url)]
    (reset! conn _conn)
    (reset! db (m/get-db _conn db-name))))

(defn connect-production-database [to-db mongo-url]
  (connect-and-handle-events to-db production-db mongo-url))

(defn connect-development-database [to-db]
  (connect-and-handle-events to-db development-db))
