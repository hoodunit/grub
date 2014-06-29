(ns grub.state
  (:require [grub.view :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def default-app-state {:grubs {}
                        :recipes {}})

(defmulti handle-grub-event (fn [event state] (:event event))
  :default :unknown-event)

(defmethod handle-grub-event :unknown-event [event grubs]
  grubs)

(defn new-grub [id grub completed]
  {:id id :grub grub :completed completed})

(defmethod handle-grub-event :add-grub [event grubs]
  (let [grub (new-grub (:id event) (:grub event) (:completed event))]
    (assoc grubs (:id grub) grub)))

(defn assoc-new-grub [current new]
  (assoc current (:id new)
    (new-grub (:id new) (:grub new) (:completed new))))

(defn make-add-grubs-map [grub-events]
  (reduce assoc-new-grub {} grub-events))

(defmethod handle-grub-event :add-grub-list [event grubs]
  (let [add-grub-events (:grubs event)
        add-grubs (make-add-grubs-map add-grub-events)]
    (merge grubs add-grubs)))

(defmethod handle-grub-event :complete-grub [event grubs]
  (assoc-in grubs [(:id event) :completed] true))

(defmethod handle-grub-event :uncomplete-grub [event grubs]
  (assoc-in grubs [(:id event) :completed] false))

(defmethod handle-grub-event :update-grub [event grubs]
  (assoc-in grubs [(:id event) :grub] (:grub event)))

(defmethod handle-grub-event :clear-all-grubs [event grubs]
  {})

(defmulti handle-recipe-event (fn [event recipes] (:event event))
  :default :unknown-event)

(defmethod handle-recipe-event :unknown-event [event recipes]
  recipes)

(defn new-recipe [id name grubs]
  {:id id :name name :grubs grubs})

(defmethod handle-recipe-event :add-recipe [event recipes]
  (let [recipe (new-recipe (:id event) (:name event) (:grubs event))]
    (assoc recipes (:id recipe) recipe)))

(defmethod handle-recipe-event :add-recipe-list [event recipes]
  (->> (:recipes event)
       (map #(new-recipe (:id %) (:name %) (:grubs %)))
       (reduce (fn [recipes r] (assoc recipes (:id r) r)) recipes)))

(defmethod handle-recipe-event :update-recipe [event recipes]
  (->> recipes
       (assoc-in [(:id event) :name] (:name event))
       (assoc-in [(:id event) :grubs] (:grubs event))))

(defn update-state-and-render [remote]
  (go-loop [state default-app-state] 
           (let [event (<! (a/merge [remote view/out]))
                 new-grubs (handle-grub-event event (:grubs state))
                 new-recipes (handle-recipe-event event (:recipes state))
                 new-state (assoc state 
                             :grubs new-grubs
                             :recipes new-recipes)]
             (view/render-app new-state)
             (recur new-state)))
  view/out)
