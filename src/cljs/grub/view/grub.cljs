(ns grub.view.grub
  (:require [grub.view.dom :as dom]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs and-let]]
                   [cljs.core.async.macros :refer [go go-loop]]
                   [dommy.macros :refer [sel1]]))

(defn get-add-grub-clicks []
  (:chan (dom/get-clicks (sel1 :#add-grub-btn))))

(defn get-add-grub-enters []
  (:chan (dom/get-enters (sel1 :#add-grub-input))))

(defn get-create-events []
  (let [events (a/merge [(get-add-grub-clicks)
                         (get-add-grub-enters)])]
    (->> events
         (a/map< #(dom/get-add-grub-text))
         (a/filter< #(not (empty? %)))
         (a/map< (fn [g] 
                   {:event :add-grub 
                    :id (str "grub-" (.now js/Date))
                    :grub g
                    :completed false})))))

(defn parse-complete-event [elem]
  (let [id (.-id elem)
        completed (dom/-completed? elem)
        event-type (if completed :uncomplete-grub :complete-grub)]
    {:id id 
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

(defn make-grub-update-event [grub-elem orig-grub-text]
  (let [grub-text (dom/-grub-text grub-elem)
        id (dom/-id grub-elem)]
    (when (not (= grub-text orig-grub-text))
      {:event :update-grub
       :grub grub-text 
       :id id})))

(defn wait-for-update-event [grub]
  (let [out (chan)
        orig-grub (dom/-grub-text grub)]
    (go (let [{bodyclick :chan 
               unlisten-bodyclick :unlisten} (dom/get-away-clicks grub)
               {enter :chan
                unlisten-enter :unlisten} (dom/get-body-enters)]
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
                        (filter #(= (:id (second %)) id))
                        (first)
                        (first))
        grub (grubs grub-index)]
    [grub-index grub]))

(defn get-grub-ingredient [grub]
  (let [text (clojure.string/lower-case (:grub grub))
        match (re-find #"[a-z]{3}.*$" text)]
    match))

(defn sort-and-render-grub-list! [grubs]
  (let [sorted-grubs (sort-by (juxt :completed get-grub-ingredient) (vals grubs))]
    (dom/render-grub-list sorted-grubs)))

(defmulti handle-event (fn [event grubs] (:event event))
  :default :unknown-event)

(defmethod handle-event :unknown-event [event grubs]
  ;(logs "Cannot handle unknown event:" event)
  grubs)

(defmethod handle-event :add-grub [event grubs]
  (let [grub (dom/make-new-grub (:id event) (:grub event) (:completed event))
        new-grubs (assoc grubs (:id grub) grub)]
    (dom/-show! dom/clear-all-btn)
    (sort-and-render-grub-list! new-grubs)
    (dom/clear-new-grub-input!)
    (dom/focus-new-grub-input!)
    new-grubs))

(defn assoc-new-grub [current new]
  (assoc current (:id new)
    (dom/make-new-grub (:id new) (:grub new) (:completed new))))

(defn make-add-grubs-map [grub-events]
  (reduce assoc-new-grub {} grub-events))

(defmethod handle-event :add-grub-list [event grubs]
  (let [add-grub-events (:grubs event)
        add-grubs (make-add-grubs-map add-grub-events)
        new-grubs (merge grubs add-grubs)]
    (dom/-show! dom/clear-all-btn)
    (sort-and-render-grub-list! new-grubs)
    new-grubs))

(defmethod handle-event :complete-grub [event grubs]
  (let [grub (get grubs (:id event))
        new-grubs (assoc-in grubs [(:id event) :completed] true)]
    (sort-and-render-grub-list! new-grubs)
    new-grubs))

(defmethod handle-event :uncomplete-grub [event grubs]
  (let [new-grubs (assoc-in grubs [(:id event) :completed] false)]
    (sort-and-render-grub-list! new-grubs)
    new-grubs))

(defmethod handle-event :update-grub [event grubs]
  (let [new-grubs (assoc-in grubs [(:id event) :grub] (:grub event))]
    (sort-and-render-grub-list! new-grubs)
    new-grubs))

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
