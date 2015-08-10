(ns grub.db
  (:require [datomic.api :as d]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [grub.util :as util]
            [clojure.pprint :refer [pprint]]))

(def schema-tx [
  ;; grubs
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :grub/id
   :db/valueType          :db.type/keyword
   :db/cardinality        :db.cardinality/one
   :db/unique             :db.unique/identity
   :db/doc                "Grub ID"
   :db.install/_attribute :db.part/db}
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :grub/text
   :db/valueType          :db.type/string
   :db/cardinality        :db.cardinality/one
   :db/fulltext           true
   :db/doc                "The text of a grub item e.g. '3 apples'"
   :db.install/_attribute :db.part/db}
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :grub/completed
   :db/valueType          :db.type/boolean
   :db/cardinality        :db.cardinality/one
   :db/doc                "The status of a grub item (completed or not completed)"
   :db.install/_attribute :db.part/db}

  ;; recipes
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :recipe/id
   :db/valueType          :db.type/keyword
   :db/cardinality        :db.cardinality/one
   :db/unique             :db.unique/identity
   :db/doc                "Recipe ID"
   :db.install/_attribute :db.part/db}
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :recipe/name
   :db/valueType          :db.type/string
   :db/cardinality        :db.cardinality/one
   :db/fulltext           true
   :db/doc                "Recipe name"
   :db.install/_attribute :db.part/db}
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :recipe/grubs
   :db/valueType          :db.type/string
   :db/cardinality        :db.cardinality/one
   :db/fulltext           true
   :db/doc                "Recipe ingredients"
   :db.install/_attribute :db.part/db}
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :recipe/directions
   :db/valueType          :db.type/string
   :db/cardinality        :db.cardinality/one
   :db/fulltext           true
   :db/doc                "Directions for making a recipe"
   :db.install/_attribute :db.part/db}
  ])

(defn add-schema-to-db [uri]
  (d/transact (d/connect uri) schema-tx) )

(defn create-db-unless-exists [uri]
  (let [db-created? (d/create-database uri)]
    (when db-created? @(add-schema-to-db uri))))

(defn connect [uri]
  (create-db-unless-exists uri)
  (let [conn (d/connect uri)]
    (println "Connected to datomic at " uri)
    conn))

(defn remap-keys [key-maps coll]
  (reduce (fn [new-coll [key new-key]] (assoc new-coll new-key (get coll key))) {} key-maps))

(defn query-entities [db entity-key]
  (->> db
       (d/q [:find '?e :where ['?e entity-key]])
       ((fn [[id]] (d/entity db id)))))

(defn get-current-db-state [db]
  (let [grubs (->> (query-entities db :grub/id)
                   (map #(remap-keys {:grub/id :id :grub/text :text :grub/completed :completed} %))
                   vec
                   (util/map-by-key :id))
        recipes (->> (query-entities db :recipe/id)
                     (map #(remap-keys {:recipe/id :id :recipe/name :name :recipe/grubs :grubs :recipe/directions :directions} %))
                     vec
                     (util/map-by-key :id))]
    {:grubs grubs
     :recipes recipes}))

(defn get-current-state [conn]
  (get-current-db-state (d/db conn)))

(defn remove-keys-with-nil-vals [mapcoll]
  (->> mapcoll
       (remove (fn [[k v]] (nil? v)))
       (reduce (fn [cur [k v]] (assoc cur k v)) {})))

(defn upsert-grub-tx [grub]
  [(remove-keys-with-nil-vals {:db/id          (d/tempid :db.part/user)
                               :grub/id        (:id grub)
                               :grub/text      (:text grub)
                               :grub/completed (:completed grub)})])

(defn upsert-recipe-tx [recipe]
  [(remove-keys-with-nil-vals {:db/id             (d/tempid :db.part/user)
                               :recipe/id         (:id recipe)
                               :recipe/name       (:name recipe)
                               :recipe/grubs      (:grubs recipe)
                               :recipe/directions (:directions recipe)})])

(defn update-db! [conn state]
  (let [grubs-tx (->> state
                      :grubs
                      (vals)
                      (map upsert-grub-tx)
                      (flatten)
                      (vec))
        recipes-tx (->> state
                        :recipes
                        (vals)
                        (map upsert-recipe-tx)
                        (flatten)
                        (vec))
        tx (into grubs-tx recipes-tx)]
    @(d/transact conn tx)))

(defn disconnect [conn]
  (d/release conn))

(defn get-history-state [db-conn tag]
  (get-current-state db-conn))


(defn patch-map [state diff]
  (-> state
      (#(apply dissoc % (into [] (:- diff))))
      (#(merge-with merge % (:+ diff)))))

(defn patch-state [state diff]
  (->> state
       (keys)
       (map (fn [k] [k (patch-map (k state) (k diff))]))
       (into {})))


(def empty-diff {:grubs {:- #{} :+ nil}
                 :recipes {:- #{} :+ nil}})

(def added-diff
  {:grubs {:- #{}
           :+ {"grub-completed" {:completed true}
               "grub-updated" {:text "Ketchup"}
               "grub-added" {:completed false :text "Toothpaste"}}}
   :recipes {:- #{} :+ nil}})

(defn diff-tx [diff]
  (let [grubs-upsert-tx (->> diff
                             :grubs
                             :+
                             (map (fn [[k v]] (assoc v :id k)))
                             (map upsert-grub-tx)
                             (flatten)
                             (vec))
        grubs-retract-tx (->> diff
                              :grubs
                              :-
                              (map (fn [id] [:db.fn/retractEntity [:grub/id id]]))
                              (vec))
        recipes-upsert-tx (->> diff
                               :recipes
                               :+
                               (map (fn [[k v]] (assoc v :id k)))
                               (map upsert-recipe-tx)
                               (flatten)
                               (vec))
        recipes-retract-tx (->> diff
                              :recipes
                              :-
                              (map (fn [id] [:db.fn/retractEntity [:recipe/id id]]))
                              (vec))]
    (vec (concat grubs-upsert-tx grubs-retract-tx recipes-upsert-tx recipes-retract-tx))))

(defn patch-state! [conn diff]
  (pprint (diff-tx diff))
  @(d/transact conn (diff-tx diff)))
