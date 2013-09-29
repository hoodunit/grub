(ns grub.view
  (:require [grub.state :as state]
            [grub.dom :as dom]
            [dommy.core :as dommy]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs do-chan]]
                   [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn re-render-when-state-changes []
  (add-watch state/grubs 
             :grub-add-watch
             (fn [key ref old new]
               (if (empty? new)
                 (dom/-hide! dom/clear-all-btn)
                 (dom/-show! dom/clear-all-btn))
               (dom/render-grub-list new))))

(defn get-grubs-from-clicks []
  (->> (:chan (dom/listen dom/add-grub-btn :click))
       (a/map< #(dom/get-add-grub-text))))

(defn get-grubs-from-enter []
  (->> (:chan (dom/listen dom/add-grub-text :keyup))
       (a/filter< #(= (.-keyIdentifier %) "Enter"))
       (a/map< #(dom/get-add-grub-text))))

(defn get-created-events []
  (let [grubs (a/merge [(get-grubs-from-clicks)
                        (get-grubs-from-enter)])]
    (->> grubs
         (a/filter< #(not (empty? %)))
         (a/map< (fn [g] {:grub g})))))

(defn get-clear-all-events []
  (:chan (dom/listen dom/clear-all-btn :click)))

(defn get-grub-mousedown-events []
  (:chan (dom/listen (dom/grubs-selector) :mousedown)))

(defn get-grub-mouseup-events []
  (:chan (dom/listen (dom/grubs-selector) :mouseup)))

(defn get-grub-mouseleave-events []
  (:chan (dom/listen (dom/grubs-selector) :mouseleave)))

(defn get-body-clicks []
  (:chan (dom/listen (sel1 :body) :click)))

(defn get-enters []
  (->> (:chan (dom/listen (sel1 :body) :keyup))
       (a/filter< #(= (.-keyIdentifier %) "Enter"))))

(defn get-new-recipe-clicks []
  (:chan (dom/listen dom/new-recipe :click)))

(defn get-edit-recipe-clicks []
  (->> (:chan (dom/listen (dom/recipes-selector) :click))
      (a/map< (fn [e] {:elem (.-selectedTarget e)}))))

(defn get-recipe-done-btn-clicks []
  (->> (:chan (dom/listen (dom/recipe-done-btns-selector) :click))
       (a/map< (fn [e] (log "done button click:" (.-selectedTarget e)) 
                 {:elem (.-selectedTarget e)}))))

(defn parse-completed-event [event]
  (let [target (.-selectedTarget event)
        id (.-id target)
        completed (dommy/has-class? target "completed")
        event-type (if completed :uncomplete :complete)]
    {:_id id :event event-type}))

(defmulti enter-state
  (fn [old-state new-state-name args]
    new-state-name)
  :default :unhandled)

(defmethod enter-state :unhandled [old-state new-state-name args]
  (logs "Unhandled enter transition from " (:name old-state) "to" new-state-name)
  old-state)

(defmulti exit-state
  (fn [state]
    (:name state))
  :default :unhandled)

(defmethod exit-state :unhandled [state]
  (logs "Unhandled exit transition from " (:name state))
  state)

(defn transition [state new-state-name & args]
  (logs "transition from" (:name state) "to" new-state-name)
  (-> state
      (exit-state)
      (enter-state new-state-name args)
      (assoc :name new-state-name)))

(defmulti handle-event
  (fn [state event]
    [(:name state) (:event event)])
  :default [:unhandled-state :unhandled-event])

(defmethod handle-event [:unhandled-state :unhandled-event] [state event]
  (logs "Unhandled event [" (:name state) (:event event) "]")
  state)




(defmethod handle-event [:default :created] [state event]
  (let [add-event (-> event
                      (assoc :event :add)
                      (assoc :_id (str "grub-" (.now js/Date)))
                      (assoc :completed false))]
    (go (>! (:out state) add-event))
    (dom/clear-add-grub-text)
    state))

(defmethod handle-event [:default :clear-all] [state event]
  (go (>! (:out state) {:event :clear-all}))
  state)

(defmethod handle-event [:default :mousedown] [state event]
  (let [mouseevent (:data event)]
    (dom/-activate! (.-selectedTarget mouseevent))
    (let [now (.now js/Date)
          new-state (assoc state :mousedown-time now)]
      (go (<! (a/timeout 500))
          (>! (:edit (:channels state))
              {:mousedown-time now :elem (.-selectedTarget mouseevent)}))
      new-state)))

(defmethod handle-event [:default :mouseup] [state event]
  (dom/-deactivate! (.-selectedTarget (:data event)))
  (go (>! (:out state) (parse-completed-event (:data event))))
  (let [new-state (assoc state :mousedown-time nil)]
    new-state))

(defmethod handle-event [:default :mouseleave] [state event]
  (dom/-deactivate! (.-selectedTarget (:data event)))
  state)

(defmethod handle-event [:default :edit] [state event]
  (if (and (:mousedown-time state)
           (= (:mousedown-time event)
              (:mousedown-time state)))
    (transition state :edit-grub (:elem event))
    state))

(defmethod handle-event [:default :new-recipe-click] [state event]
  (transition state :new-recipe))

(defmethod handle-event [:default :edit-recipe-click] [state event]
  (transition state :edit-recipe (:elem event)))

(defmethod handle-event [:default :add-recipe] [state event]
  (log "handle event add-recipe")
  (dom/add-new-recipe (:_id event) (:name event) (:steps event))
  state)




(defmethod enter-state :edit-grub [old-state new-state-name [edit-elem]]
  (dom/-set-editing! edit-elem)
  (assoc old-state :edit-elem edit-elem))

(defmethod exit-state :edit-grub [state]
  (let [edit-elem (:edit-elem state)]
    (dom/-unset-editing! edit-elem)
    (let [grub-text (.-value (sel1 edit-elem :.grub-input))
          id (.-id edit-elem)
          update-event {:event :update :grub grub-text :_id id}
          new-state (dissoc state :edit-elem)]
      (go (>! (:out state) update-event))
      new-state)))

(defmethod handle-event [:edit-grub :body-click] [state event]
  (let [clicked-elem (.-target (:data event))
        edit-elem (:edit-elem state)]
    (if (dommy/descendant? clicked-elem edit-elem)
      state
      (transition state :default))))

(defmethod handle-event [:edit-grub :enter] [state event]
  (transition state :default))




(defmethod enter-state :new-recipe [old-state new-state-name args]
  (dom/-expand! dom/new-recipe)
  old-state)

(defn get-new-recipe-info []
  (let [name (.-value (sel1 dom/new-recipe "#recipe-name"))
        steps (.-value (sel1 dom/new-recipe "#recipe-steps"))]
    (when (not (or (empty? name) (empty? steps)))
      (let [id (str "recipe-" (.now js/Date))]
        {:name name :steps steps :_id id}))))

(defmethod exit-state :new-recipe [state]
  (dom/-unexpand! dom/new-recipe)
  (let [recipe-info (get-new-recipe-info)]
    (if recipe-info
      (let [recipe-node (dom/add-new-recipe (:_id recipe-info)
                                            (:name recipe-info)
                                            (:steps recipe-info))]
        (log "new recipe name:" (:name recipe-info) "steps" (:steps recipe-info))
        (dom/-clear! dom/new-recipe)
        (go (>! (:out state) (assoc recipe-info :event :add-recipe)))
        (assoc state 
          :recipes (assoc (:recipes state) (.-id recipe-node) recipe-node)))
      state)))

(defmethod handle-event [:new-recipe :body-click] [state event]
  (let [clicked-elem (.-target (:data event))]
    (if (dommy/descendant? clicked-elem dom/new-recipe)
      state
      (transition state :default))))

(defmethod handle-event [:new-recipe :recipe-done-btn-click] [state event]
  (log "handle new recipe done btn click")
  (if (dommy/descendant? (:elem event) dom/new-recipe)
    (transition state :default)
    state))


(defmethod enter-state :edit-recipe [old-state new-state-name [elem]]
  (dom/-expand! elem)
  (assoc old-state :edit-elem elem))

(defmethod exit-state :edit-recipe [state]
  (let [recipe-node (:edit-elem state)
        recipe-name (.-value (sel1 recipe-node "#recipe-name"))
        recipe-steps (.-value (sel1 recipe-node "#recipe-steps"))]
    (log "update recipe new name:" recipe-name "new steps:" recipe-steps)
    (dom/-unexpand! recipe-node)
    (-> state
        (dissoc :edit-elem))))

(defmethod handle-event [:edit-recipe :body-click] [state event]
  (log "edit-recipe body click")
  (let [clicked-elem (.-target (:data event))
        recipe-node (:edit-elem state)]
    (if (dommy/descendant? clicked-elem recipe-node)
      state
      (transition state :default))))

(defmethod handle-event [:edit-recipe :recipe-done-btn-click] [state event]
  (if (dommy/descendant? (:elem event) (:edit-elem state))
    (transition state :default)
    state))




(defn main-loop [channels]
  (let [out (chan)
        events (chan)
        event-mix (a/mix events)]
    (doseq [[name c] (seq channels)] (a/admix event-mix c))
    (go-loop [state {:name :default 
                     :channels channels 
                     :out out
                     :recipes {}}]
             (let [event (<! events)]
               (logs "handle event" (:name state) event)
               (recur (handle-event state event))))
    out))



(defn get-raw-view-channels []
  {:created (get-created-events)
   :clear-all (get-clear-all-events)
   :mousedown (get-grub-mousedown-events)
   :mouseup (get-grub-mouseup-events)
   :mouseleave (get-grub-mouseleave-events)
   :body-click (get-body-clicks)
   :edit (chan)
   :enter (get-enters)
   :new-recipe-click (get-new-recipe-clicks)
   :edit-recipe-click (get-edit-recipe-clicks)
   :recipe-done-btn-click (get-recipe-done-btn-clicks)})

(defn append-event-name-to-channel-events [channels]
  (into {} 
        (for [[name c] channels]
          [name (a/map< (fn [e] 
                          (if (map? e)
                            (assoc e :event name)
                            {:event name :data e})) 
                        c)])))

(defn get-named-channels [remote-channel]
  (let [raw-view-channels (get-raw-view-channels)
        named-view-channels (append-event-name-to-channel-events raw-view-channels)]
    (assoc named-view-channels :remote-channel remote-channel)))
  

(defn setup-and-get-view-events [remote-channel]
  (dom/render-body)
  (re-render-when-state-changes)
  (main-loop (get-named-channels remote-channel)))
