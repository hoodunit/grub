(ns grub.db
  (:require [grub.util :as util]
            [grub.sync :as sync]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.operators :as mo]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def collection "grub-lists")

(defn clear-all [db]
  (mc/drop db collection))

(defn update-db! [db state]
  (mc/drop db collection)
  (mc/insert db collection state))

(defn get-current-state [db]
  (let [state (first (mc/find-maps db collection))]
    (if state
      (dissoc state :_id)
      sync/empty-state)))

(defn connect-and-handle-events [to-db db-name]
  (println "Connected to mongo at localhost:" db-name)
  (let [conn (m/connect)
        db (m/get-db conn db-name)]
    (a/go-loop []
               (if-let [state (<! to-db)]
                 (do (update-db! db state)
                     (recur))
                 (println "Database disconnected")))
    db))
