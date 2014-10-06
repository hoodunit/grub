(ns grub.db
  (:require [grub.util :as util]
            [grub.sync :as sync]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def conn (atom nil))
(def db (atom nil))
(def collection "grub-lists")
(def production-db "grub")
(def development-db "grub-dev")

(defn clear-all []
  (mc/drop @db collection))

(defn update-db! [state]
  (mc/drop @db collection)
  (mc/insert @db collection state))

(defn get-current-state []
  (let [state (first (mc/find-maps @db collection))]
    (if state
      (dissoc state :_id)
      sync/empty-state)))

(defn connect! [db-name mongo-url]
  (if mongo-url
    (do (println "Connected to mongo via url:" mongo-url)
        (:conn (m/connect-via-uri mongo-url)))
    (do (println "Connected to mongo at localhost:" db-name)
        (m/connect))))

(defn connect-and-handle-events [to-db db-name & [mongo-url]]
  (a/go-loop []
             (if-let [state (<! to-db)]
               (do (println "DB got new state")
                   (update-db! state)
                   (recur))
               (println "Database disconnected")))
  (let [_conn (connect! db-name mongo-url)]
    (reset! conn _conn)
    (reset! db (m/get-db _conn db-name))))

(defn connect-production-database [to-db mongo-url]
  (connect-and-handle-events to-db production-db mongo-url))

(defn connect-development-database [to-db]
  (connect-and-handle-events to-db development-db))
