(ns grub.db
  (:require [datomic.api :as d]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [grub.util :as util]))

(def schema-tx (read-string (slurp "database_schema.edn")))

(defn create-db [uri]
  (d/create-database uri)
  (let [conn (d/connect uri)]
    @(d/transact conn schema-tx)))

(defn connect [uri]
  (create-db uri)
  (let [conn (d/connect uri)]
    (println "Connected to datomic at " uri)
    conn))

(defn map-keys [key-maps coll]
  (reduce (fn [new-coll [key new-key]] (assoc new-coll new-key (get coll key))) {} key-maps))

(defn get-current-state [conn]
  (let [db (d/db conn)
        get-entity (fn [[id]] (d/touch (d/entity db id)))
        grub-ids (d/q '[:find ?g :where [?g :grub/id]] (d/db conn))
        map-grub-keys #(map-keys {:grub/id :id :grub/text :text :grub/completed :completed} %)
        grubs (->> grub-ids
                   (map (comp map-grub-keys get-entity))
                   vec
                   (util/map-by-key :id))
        recipe-ids (d/q '[:find ?r :where [?r :recipe/id]] (d/db conn))
        map-recipe-keys #(map-keys {:recipe/id :id :recipe/name :name :recipe/grubs :grubs :recipe/directions :directions} %)
        recipes (->> recipe-ids
                     (map (comp map-recipe-keys get-entity))
                     vec
                     (util/map-by-key :id))]
    {:grubs grubs
     :recipes recipes}))

(defn grub-tx [grub]
  [{:db/id           (d/tempid :db.part/user)
    :grub/id         (:id grub)
    :grub/text       (:text grub)
    :grub/completed (:completed grub)}])

(defn recipe-tx [recipe]
  [{:db/id            (d/tempid :db.part/user)
    :recipe/id          (:id recipe)
    :recipe/name        (:name recipe)
    :recipe/grubs (:grubs recipe)
    :recipe/directions  (:directions recipe)}])

(defn update-db! [conn state]
  (let [grubs-tx (->> state
                      :grubs
                      (vals)
                      (map grub-tx)
                      (flatten)
                      (vec))
        recipes-tx (->> state
                        :recipes
                        (vals)
                        (map recipe-tx)
                        (flatten)
                        (vec))
        tx (into grubs-tx recipes-tx)]
    @(d/transact conn tx)))

(defn disconnect [conn]
  (d/release conn))

