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

(def all-grubs-query
  [:find '?id '?text '?completed
   :where
   ['?e :grub/id '?id]
   ['?e :grub/text '?text]
   ['?e :grub/completed '?completed]])

(def all-recipes-query
  [:find '?id '?name '?grubs '?directions
   :where
   ['?e :recipe/id '?id]
   ['?e :recipe/name '?name]
   ['?e :recipe/grubs '?grubs]
   ['?e :recipe/directions '?directions]])

(defn grub-as-map [[id text completed]]
  {:id id :text text :completed completed})

(defn recipe-as-map [[id name grubs directions]]
  {:id id :name name :grubs grubs :directions directions})

(defn get-db-grubs [db]
  (->> (d/q all-grubs-query db)
       (map grub-as-map)
       vec
       (util/map-by-key :id)))

(defn get-db-recipes [db]
  (->> (d/q all-recipes-query db)
       (map recipe-as-map)
       vec
       (util/map-by-key :id)))

(defn get-current-db-state [db]
  {:grubs (get-db-grubs db)
   :recipes (get-db-recipes db)})

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

(defn disconnect [conn]
  (d/release conn))

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
  @(d/transact conn (diff-tx diff)))

(defn report-queue-channel [conn]
  (let [queue (d/tx-report-queue conn)
        changes (chan)
        pub (a/mult changes)]
    (go (loop []
          (let [report (.. queue take)]
            (>! changes report)
            (recur))))
    pub))

(defn report-queue-subscribe [report-ch]
  (let [reports (chan)
        report-buffer (chan (a/sliding-buffer 1))]
    (a/tap report-ch reports)
    (a/pipe reports report-buffer)
    {:report-queue report-buffer
     :tap report-ch}))

(defn report-queue-unsubscribe [report-ch tap]
  (a/untap report-ch tap))
