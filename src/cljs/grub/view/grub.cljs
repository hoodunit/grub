(ns grub.view.grub
  (:require [grub.view.dom :as dom]
            [dommy.core :as dommy]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs and-let]]
                   [dommy.macros :refer [sel1]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn get-add-grub-clicks []
  (:chan (dom/listen dom/add-grub-btn :click)))

(defn get-add-grub-enters []
  (->> (:chan (dom/listen dom/add-grub-text :keyup))
       (a/filter< #(= (.-keyIdentifier %) "Enter"))))

(defn get-create-events []
  (let [events (a/merge [(get-add-grub-clicks)
                         (get-add-grub-enters)])]
    (->> events
         (a/map< #(dom/get-add-grub-text))
         (a/filter< #(not (empty? %)))
         (a/map< (fn [g] 
                   {:event :add-grub 
                    :_id (str "grub-" (.now js/Date))
                    :grub g
                    :completed false})))))

(defn parse-complete-event [elem]
  (let [id (.-id elem)
        completed (dom/-completed? elem)
        event-type (if completed :uncomplete-grub :complete-grub)]
    {:_id id 
     :event event-type}))

(defn get-complete-events []
  (->> (:chan (dom/listen (dom/grubs-selector) :click))
       (a/map< #(.-selectedTarget %))
       (a/filter< #(not (dom/-editing? %)))
       (a/map< parse-complete-event)))

(defn get-clear-all-events []
  (->> (:chan (dom/listen dom/clear-all-btn :click))
       (a/map< (fn [e] {:event :clear-all-grubs}))))

(defn get-grub-mousedown-events []
  (let [{c :chan unlisten :unlisten} (dom/listen (dom/grubs-selector) :mousedown)]
    {:unlisten unlisten
     :chan (a/map< (fn [e] {:selected-grub (.-selectedTarget e)}) c)}))

(defn get-grub-mouseup-events [grub-elem]
  (dom/listen grub-elem :mouseup))

(defn get-grub-mouseleave-events [grub-elem]
  (dom/listen grub-elem :mouseleave))

(defn wait-for-mousedown-on-grub []
  (let [out (chan)]
    (go (let [{mousedown :chan unlisten :unlisten} (get-grub-mousedown-events)
               event (<! mousedown)
               selected-grub (:selected-grub event)]
          (unlisten)
          (>! out selected-grub)))
    out))

(defn wait-for-grub-mousedown-timeout [grub]
  (let [out (chan)]
    (dom/-activate! grub)
    (go (let [{mouseup :chan
               unlisten-mouseup :unlisten} (get-grub-mouseup-events grub)
               {mouseleave :chan 
                unlisten-mouseleave :unlisten } (get-grub-mouseleave-events grub)
                timeout (a/timeout 500)
                [_ c] (a/alts! [mouseup mouseleave timeout])]
          (unlisten-mouseleave)
          (unlisten-mouseup)
          (dom/-deactivate! grub)
          (>! out (= c timeout))))
    out))

(defn get-enters []
  (let [{c :chan unlisten :unlisten} (dom/listen (sel1 :body) :keyup)]
    {:unlisten unlisten
     :chan (a/filter< #(= (.-keyIdentifier %) "Enter") c)}))

(defn make-grub-update-event [grub-elem orig-grub-text]
  (let [grub-text (.-value (sel1 grub-elem :.grub-input))
        id (.-id grub-elem)]
    (when (not (= grub-text orig-grub-text))
      {:event :update-grub
       :grub grub-text 
       :_id id})))

(defn wait-for-update-event [grub]
  (let [out (chan)
        orig-grub (.-value (sel1 grub :.grub-input))]
    (go (let [{bodyclick :chan 
               unlisten-bodyclick :unlisten} (dom/get-away-clicks grub)
               {enter :chan
                unlisten-enter :unlisten} (get-enters)]
          (dom/-set-editing! grub)
          (a/alts! [bodyclick enter])
          (unlisten-bodyclick)
          (unlisten-enter)
          (dom/-unset-editing! grub)
          (if-let [update-event (make-grub-update-event grub orig-grub)]
            (>! out update-event)
            (a/close! out))))
    out))

(defn get-update-events []
  (let [out (chan)]
    (go-loop [] 
             (and-let [grub         (<! (wait-for-mousedown-on-grub))
                       timeout?     (<! (wait-for-grub-mousedown-timeout grub))
                       update-event (<! (wait-for-update-event grub))]
                      (>! out update-event))
             (recur))
    out))

(defn get-grub-with-index [grubs id]
  (let [grub-index (->> grubs
                        (map-indexed vector)
                        (filter #(= (:_id (second %)) id))
                        (first)
                        (first))
        grub (grubs grub-index)]
    [grub-index grub]))

(defmulti handle-event (fn [event grubs] (:event event))
  :default :unknown-event)

(defmethod handle-event :unknown-event [event grubs]
  ;(logs "Cannot handle unknown event:" event)
  grubs)

(defmethod handle-event :add-grub [event grubs]
  (let [grub (dom/add-new-grub (:_id event) (:grub event) (:completed event))]
    (dom/-show! dom/clear-all-btn)
    (assoc grubs (:id grub) grub)))

(defmethod handle-event :complete-grub [event grubs]
  (let [grub (get grubs (:_id event))]
    (dom/-complete! grub)
    (assoc-in grubs [(:_id event) :completed] true)))

(defmethod handle-event :uncomplete-grub [event grubs]
  (dom/-uncomplete! (get grubs (:_id event)))
  (assoc-in grubs [(:_id event) :completed] false))

(defmethod handle-event :update-grub [event grubs]
  (dom/-update-grub! (get grubs (:_id event)) (:grub event))
  (assoc-in grubs [(:_id event) :grub] (:grub event)))

(defmethod handle-event :clear-all-grubs [event grubs]
  (dom/-hide! dom/clear-all-btn)
  (dom/clear-grubs!)
  {})

(defn handle-grubs [remote-events]
  (let [out (chan)
        local-events [(get-create-events)
                      (get-complete-events)
                      (get-clear-all-events)
                      (get-update-events)]]
    (go-loop [grubs {}] 
             (let [[event c] (a/alts! (conj local-events remote-events))]
               (when-not (= c remote-events)
                 (>! out event))
               (recur (handle-event event grubs))))
    out))
