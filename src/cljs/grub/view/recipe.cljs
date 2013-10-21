(ns grub.view.recipe
  (:require [grub.view.dom :as dom]
            [dommy.core :as dommy]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs and-let]]
                   [dommy.macros :refer [sel1]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn wait-for-new-recipe-input-click []
  (:chan (dom/listen-once dom/new-recipe :click)))

(defn parse-new-recipe-event []
  (let [name (dom/-get-name dom/new-recipe)
        grubs (dom/-get-grubs-str dom/new-recipe)]
    (when (not (or (empty? name) (empty? grubs)))
      (let [id (str "recipe-" (.now js/Date))]
        {:event :add-recipe 
         :name name 
         :grubs grubs 
         :id id}))))

(defn wait-for-create-event []
  (let [out (chan)
        {ctrl-enters :chan
         ctrl-enters-unlisten :unlisten} (dom/get-ctrl-enters)
        {away-clicks :chan
         away-clicks-unlisten :unlisten} (dom/get-away-clicks dom/new-recipe)
        {done-clicks :chan
         done-clicks-unlisten :unlisten} (dom/listen
                                          (sel1 dom/new-recipe ".recipe-done-btn")
                                          :click)]
    (go (a/alts! [ctrl-enters away-clicks done-clicks])
        (ctrl-enters-unlisten)
        (away-clicks-unlisten)
        (done-clicks-unlisten)
        (when-let [event (parse-new-recipe-event)]
          (>! out event))
        (a/close! out))
    out))

(defn get-create-events []
  (let [out (chan)]
    (go-loop []
             (<! (wait-for-new-recipe-input-click))
             (dom/-expand! dom/new-recipe)
             (when-let [create-event (<! (wait-for-create-event))]
               (>! out create-event)
               (dom/-clear! dom/new-recipe))
             (dom/-unexpand! dom/new-recipe)
             (recur))
    out))

(defn wait-for-edit-recipe-input-click []
  (->> (:chan (dom/listen-once (dom/recipes-selector) :click))
       (a/filter< #(not (dommy/has-class? (.-selectedTarget %) :btn)))
       (a/map< #(.-selectedTarget %))))

(defn parse-update-recipe-event [elem]
  (let [id (.-id elem)
        name (dom/-get-name elem)
        grubs (dom/-get-grubs-str elem)]
    (when (not (or (empty? name) (empty? grubs)))
        {:event :update-recipe 
         :name name 
         :grubs grubs 
         :id id})))

(defn wait-for-update-event [elem]
  (let [out (chan)
        {ctrl-enters :chan
         ctrl-enters-unlisten :unlisten} (dom/get-ctrl-enters)
        {away-clicks :chan
         away-clicks-unlisten :unlisten} (dom/get-away-clicks elem)
        {done-clicks :chan
         done-clicks-unlisten :unlisten} (dom/listen
                                          (sel1 elem ".recipe-done-btn")
                                          :click)]
    (go (a/alts! [ctrl-enters away-clicks done-clicks])
        (ctrl-enters-unlisten)
        (away-clicks-unlisten)
        (done-clicks-unlisten)
        (when-let [event (parse-update-recipe-event elem)]
          (>! out event))
        (a/close! out))
    out))

(defn get-update-events []
  (let [out (chan)]
    (go-loop []
             (let [recipe-elem (<! (wait-for-edit-recipe-input-click))]
               (dom/-expand! recipe-elem)
               (when-let [update-event (<! (wait-for-update-event recipe-elem))]
                 (>! out update-event))
               (dom/-unexpand! recipe-elem)
               (recur)))
    out))

(defn get-add-grub-events []
  (let [out (chan)
        clicks (:chan (dom/listen (dom/recipe-add-grubs-btns-selector) :click))]
    (go-loop []
             (let [e (<! clicks)
                   elem (dommy/closest (.-selectedTarget e) :.recipe-panel)
                   id (.-id elem)
                   grub-texts (dom/-get-grubs elem)
                   grubs (map-indexed (fn [index g] {:id (str "grub-" (.now js/Date) index)
                                                     :grub g
                                                     :completed false})
                                      grub-texts)
                   event {:event :add-grub-list
                          :grubs grubs}]
               (>! out event))
             (recur))
    out))
  

(defmulti handle-event (fn [event recipes] (:event event))
  :default :unknown-event)

(defmethod handle-event :unknown-event [event recipes]
  ;(logs "Cannot handle unknown event:" event)
  recipes)

(defmethod handle-event :add-recipe [event recipes]
  (let [recipe (dom/add-new-recipe! (:id event)
                                   (:name event)
                                   (:grubs event))]
    (assoc recipes (:id recipe) recipe)))

(defn assoc-new-recipe! [current new]
  (assoc current (:id new)
    (dom/add-new-recipe! (:id new) (:name new) (:grubs new))))

(defn add-new-recipes! [recipe-events]
  (reduce assoc-new-recipe! {} recipe-events))

(defmethod handle-event :add-recipe-list [event recipes]
  (let [add-recipe-events (:recipes event)
        added-recipes (add-new-recipes! add-recipe-events)
        new-recipes (merge recipes added-recipes)]
    new-recipes))

(defmethod handle-event :update-recipe [event recipes]
  (let [recipe (get recipes (:id event))
        updated-recipe (-> recipe
                       (assoc :name (:name event))
                       (assoc :grubs (:grubs event)))]
    (dom/-update-recipe! updated-recipe)
    (assoc recipes (:id recipes) updated-recipe)))
  
(defn handle-recipes [remote-events]
  (let [out (chan)
        local-events [(get-create-events)
                      (get-update-events)]
        add-grub-events (get-add-grub-events)]
    (a/pipe add-grub-events out)
    (go-loop [recipes {}] 
             (let [[event c] (a/alts! (conj local-events remote-events))]
               (when-not (= c remote-events)
                 (>! out event))
               (recur (handle-event event recipes))))
    out))
