(ns grub.view
  (:require [grub.async-utils :as a]
            [grub.state :as state]
            [grub.dom :as dom]
            [dommy.core :as dommy]
            [cljs.core.async :refer [<! >! chan timeout close!]])
  (:require-macros [grub.macros :refer [log logs go-loop do-chan]]
                   [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go]]))

(def outgoing-events (chan))

(defn get-grubs-from-clicks []
  (->> (:chan (dom/listen dom/add-grub-btn :click))
       (a/map #(dom/get-add-grub-text))))

(defn get-grubs-from-enter []
  (->> (:chan (dom/listen dom/add-grub-text :keyup))
       (a/filter #(= (.-keyIdentifier %) "Enter"))
       (a/map #(dom/get-add-grub-text))))

(defn get-created-events []
  (let [grubs (a/fan-in [(get-grubs-from-clicks)
                         (get-grubs-from-enter)])]
    (->> grubs
         (a/filter #(not (empty? %)))
         (a/map (fn [g] {:grub g})))))

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
       (a/filter #(= (.-keyIdentifier %) "Enter"))))

(defn parse-completed-event [event]
  (let [target (.-selectedTarget event)
        id (.-id target)
        completed (dommy/has-class? target "completed")
        event-type (if completed :uncomplete :complete)]
    {:_id id :event event-type}))

(defn re-render-when-state-changes []
  (add-watch state/grubs 
             :grub-add-watch
             (fn [key ref old new]
               (if (empty? new)
                 (dom/-hide! dom/clear-all-btn)
                 (dom/-show! dom/clear-all-btn))
               (dom/render-grub-list new))))

(defn event-loop []
  (let [out (chan)
        created (get-created-events)
        clear-all (get-clear-all-events)
        mousedown (get-grub-mousedown-events)
        mouseup (get-grub-mouseup-events)
        mouseleave (get-grub-mouseleave-events)
        body-click (get-body-clicks)
        edit (chan)
        enter (get-enters)]
    (go (loop [mousedown-time nil
               edit-elem nil]
          (let [[event c] (alts! [created clear-all mousedown 
                                  mouseup mouseleave edit body-click
                                  enter])]
            (if edit-elem
              (cond
               (or (and (= c body-click)
                        (not (dommy/descendant? (.-target event) edit-elem)))
                   (= c enter))
               (do (dom/-unset-editing! edit-elem)
                   (let [grub-text (.-value (sel1 edit-elem :.grub-input))
                         id (.-id edit-elem)]
                     (>! out {:event :update 
                              :grub grub-text
                              :_id id}))
                   (recur nil nil))
               
               :else (recur nil edit-elem))
              (cond
               (= c created)
               (let [add-event (-> event
                                   (assoc :event :add)
                                   (assoc :_id (str "grub-" (.now js/Date)))
                                   (assoc :completed false))]
                 (>! out add-event)
                 (dom/clear-add-grub-text)
                 (recur mousedown-time edit-elem))

               (= c clear-all)
               (do (>! out {:event :clear-all})
                   (recur mousedown-time edit-elem))

               (= c mousedown)
               (do (dom/-activate! (.-selectedTarget event))
                   (let [now (.now js/Date)]
                     (go (<! (timeout 500))
                         (>! edit {:mousedown-time now :elem (.-selectedTarget event)}))
                     (recur now edit-elem)))

               (= c mouseup)
               (do (dom/-deactivate! (.-selectedTarget event))
                   (>! out (parse-completed-event event))
                   (recur nil edit-elem))
               
               (= c mouseleave)
               (do (dom/-deactivate! (.-selectedTarget event))
                   (recur nil edit-elem))
               
               (= c edit)
               (if (and mousedown-time (= (:mousedown-time event) mousedown-time))
                 (do (dom/-set-editing! (:elem event))
                     (recur nil (:elem event)))
                 (recur nil edit-elem))

               :else (recur mousedown-time edit-elem))))))
    out))

(defn init []
  (dom/render-body)
  (re-render-when-state-changes)
  (a/copy outgoing-events (event-loop)))
