(ns grub.db
  (:require [datomic.api :as d]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [grub.util :as util]
            [clojure.pprint :refer [pprint]]))

(def schema-tx [
  ;; list
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :list/name
   :db/valueType          :db.type/string
   :db/cardinality        :db.cardinality/one
   :db/unique             :db.unique/identity
   :db/doc                "List name (external identifier)"
   :db.install/_attribute :db.part/db}

  ;; grubs
  {:db/id                 #db/id[:db.part/db]
   :db/ident              :grub/listid
   :db/valueType          :db.type/ref
   :db/cardinality        :db.cardinality/one
   :db/doc                "Grub list entity ID"
   :db.install/_attribute :db.part/db}
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
   :db/ident              :recipe/listid
   :db/valueType          :db.type/ref
   :db/cardinality        :db.cardinality/one
   :db/doc                "Recipe list entity ID"
   :db.install/_attribute :db.part/db}
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

(defn add-schema-to-db [uri schema-tx]
  (d/transact (d/connect uri) schema-tx) )

(defn create-db-unless-exists [uri]
  (let [db-created? (d/create-database uri)]
    (when db-created? @(add-schema-to-db uri schema-tx))))

(defn connect [uri]
  (create-db-unless-exists uri)
  (d/connect uri))

(defn add-list-tx [list-name]
  {:db/id             (d/tempid :db.part/user)
   :list/name         list-name})

(defn create-list-unless-exists [conn list-name]
  (d/transact conn [(add-list-tx list-name)]))

(defn all-grubs-query [list-name]
  [:find '?id '?text '?completed
   :where
   ['?e :grub/listid [:list/name list-name]]
   ['?e :grub/id '?id]
   ['?e :grub/text '?text]
   ['?e :grub/completed '?completed]])

(defn all-recipes-query [list-name]
  [:find '?id '?name '?grubs '?directions
   :where
   ['?e :recipe/listid [:list/name list-name]]
   ['?e :recipe/id '?id]
   ['?e :recipe/name '?name]
   ['?e :recipe/grubs '?grubs]
   ['?e :recipe/directions '?directions]])

(defn grub-as-map [[id text completed]]
  {:id id :text text :completed completed})

(defn recipe-as-map [[id name grubs directions]]
  {:id id :name name :grubs grubs :directions directions})

(defn get-db-grubs [db list-name]
  (->> (d/q (all-grubs-query list-name) db)
       (map grub-as-map)
       vec
       (util/map-by-key :id)))

(defn get-db-recipes [db list-name]
  (->> (d/q (all-recipes-query list-name) db)
       (map recipe-as-map)
       vec
       (util/map-by-key :id)))

(defn get-current-db-state [db list-name]
  {:grubs (get-db-grubs db list-name)
   :recipes (get-db-recipes db list-name)})

(defn get-current-state [conn list-name]
  (get-current-db-state (d/db conn) list-name))

(defn disconnect [conn]
  (d/release conn))

(defn remove-keys-with-nil-vals [mapcoll]
  (->> mapcoll
       (remove (fn [[k v]] (nil? v)))
       (reduce (fn [cur [k v]] (assoc cur k v)) {})))

(defn upsert-grub-tx [list-name grub]
  [(remove-keys-with-nil-vals {:db/id          (d/tempid :db.part/user)
                               :grub/listid    [:list/name list-name]
                               :grub/id        (:id grub)
                               :grub/text      (:text grub)
                               :grub/completed (:completed grub)})])

(defn upsert-recipe-tx [list-name recipe]
  [(remove-keys-with-nil-vals {:db/id             (d/tempid :db.part/user)
                               :recipe/listid     list-name
                               :recipe/id         (:id recipe)
                               :recipe/name       (:name recipe)
                               :recipe/grubs      (:grubs recipe)
                               :recipe/directions (:directions recipe)})])

(defn retract-grub-tx [id]
  [:db.fn/retractEntity [:grub/id id]])

(defn retract-recipe-tx [id]
  [:db.fn/retractEntity [:recipe/id id]])

(defn grubs-upsert-tx [list-name diff]
  (->> (:+ (:grubs diff))
       (map (fn [[k v]] (assoc v :id k)))
       (map (partial upsert-grub-tx list-name))
       (flatten)
       (vec)))

(defn grubs-retract-tx [diff]
  (->> (:- (:grubs diff))
       (map retract-grub-tx)
       (vec)))

(defn recipes-upsert-tx [list-name diff]
  (->> (:+ (:recipes diff))
       (map (fn [[k v]] (assoc v :id k)))
       (map (partial upsert-recipe-tx list-name))
       (flatten)
       (vec)))

(defn recipes-retract-tx [diff]
  (->> (:- (:recipes diff))
       (map retract-recipe-tx)
       (vec)))

(defn diff-tx [list-name diff]
    (vec (concat
           (grubs-upsert-tx list-name diff)
           (grubs-retract-tx diff)
           (recipes-upsert-tx list-name diff)
           (recipes-retract-tx diff))))

(defn patch-state! [conn list-name diff]
  (let [tx (diff-tx list-name diff)]
    @(d/transact conn tx)))

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
