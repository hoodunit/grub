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
  (->> (:chan (dom/listen (sel1 :#clear-all-btn) :click))
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
