(ns grub.state
  (:require [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def app-state (atom {:grubs {}
                      :recipes {}}))

(defmulti handle-event (fn [event state] (:event event))
  :default :unknown-event)

(defmethod handle-event :unknown-event [event state]
  state)

(defn new-grub [id grub completed]
  {:id id :grub grub :completed completed})

(defmethod handle-event :add-grub [event state]
  (let [grub (new-grub (:id event) (:grub event) (:completed event))]
    (assoc-in state [:grubs (:id grub)] grub)))

(defn map-by-key [key coll]
  (->> coll
       (map (fn [a] [(get a key) a]))
       (into {})))

(defmethod handle-event :add-grub-list [event state]
  (->> event
       :grubs
       (map-by-key :id)
       (merge (:grubs state))
       (assoc state :grubs)))

(defmethod handle-event :update-grub [event state]
  (let [new-grub-info (dissoc event :event-type)
        orig-grub (get-in state [:grubs (:id event)])]
    (assoc-in state [:grubs (:id event)] (merge orig-grub new-grub-info))))

(defmethod handle-event :clear-all-grubs [event state]
  (assoc state :grubs {})) 

(defn new-recipe [id name grubs]
  {:id id :name name :grubs grubs})

(defmethod handle-event :add-recipe [event state]
  (let [recipe (new-recipe (:id event) (:name event) (:grubs event))]
    (assoc-in state [:recipes (:id recipe)] recipe)))

(defmethod handle-event :add-recipe-list [event state]
  (->> event
       :recipes
       (map-by-key :id)
       (merge (:recipes state))
       (assoc state :recipes)))

(defmethod handle-event :update-recipe [event state]
  (-> state
      (assoc-in [:recipes (:id event) :name] (:name event))
      (assoc-in [:recipes (:id event) :grubs] (:grubs event))))

(defn update-state-and-render [remote]
  (let [out (chan)
        view-events (view/render-app app-state)]
    (go-loop [] 
             (let [[event ch] (alts! [remote view-events])
                   new-state (handle-event event @app-state)]
               (reset! app-state new-state)
               (when (= ch view-events) 
                 (>! out event))
               (recur)))
    out))
