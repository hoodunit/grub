(ns grub.view
  (:require [grub.async-utils 
             :refer [do-chan! do-chan event-chan map-chan fan-in filter-chan]]
            [grub.state :as state]
            [dommy.core :as dommy]
            [cljs.core.async :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go]]))

(def outgoing-events (chan))

(def add-grub-text 
  (node [:input.form-control {:id "add-grub-input" :type "text" :placeholder "2 grubs"}]))

(def add-grub-btn 
  (node [:button.btn.btn-default {:id "add-grub-btn" :type "button"} "Add"]))

(deftemplate main-template []
  [:div.container
   [:div.row.show-grid
    [:div.col-lg-4]
    [:div.col-lg-4
     [:h3 "Grub List"]
     [:div.input-group 
      add-grub-text
      [:span.input-group-btn
       add-grub-btn]]
     [:table.table.table-condensed
      [:tbody#grubList]]]
    [:div.col-lg-4]]])

(deftemplate grub-template [grub]
  [:tr {:id (:_id grub)}
   [:td 
    [:div.checkbox.grubCheckbox [:label 
                                 [:input {:type "checkbox"
                                          :checked (:completed grub)}] 
                                 (:grub grub)]]]
   [:td
    [:button.grub-close.close {:type "button"} "×"]]])

(defn render-body []
  (dommy/prepend! (sel1 :body) (main-template)))

(defn get-add-grub-text []
  (let [text (dommy/value add-grub-text)]
    (dommy/set-value! add-grub-text "")
    text))

(defn get-grubs-from-clicks []
  (let [out (chan)]
    (dommy/listen! add-grub-btn :click #(go (>! out (get-add-grub-text))))
    out))

(defn put-grubs-if-enter-pressed [out event]
  (when (= (.-keyIdentifier event) "Enter")
    (go (>! out (get-add-grub-text)))))

(defn get-grubs-from-enter []
  (let [out (chan)]
    (dommy/listen! add-grub-text 
                   :keyup 
                   (partial put-grubs-if-enter-pressed out))
    out))

(defn get-added-events []
  (let [grubs (fan-in [(get-grubs-from-clicks)
                       (get-grubs-from-enter)])]
    (->> grubs
         (filter-chan #(not (empty? %)))
         (map-chan (fn [g] {:event :create :grub g})))))

(defn get-completed-event [event]
  (let [target (.-target event)
        checked (.-checked target)
        event-type (if checked :complete :uncomplete)
        label (aget (.-labels (.-target event)) 0)
        grub (.-textContent label)
        id (.-id (.-parentNode (.-parentNode (.-parentNode (.-parentNode target)))))]
    {:grub grub :_id id :event event-type}))

(defn get-completed-events []
  (let [events (:chan (event-chan (sel1 :#grubList) "change"))
        grubs (map-chan #(get-completed-event %) events)]
    grubs))
  
(defn get-deleted-events []
  (let [click-events (chan)]
    (dommy/listen! [(sel1 :#grubList) ".close"] 
                   :click 
                   #(go (>! click-events %)))
    (let [ids (map-chan #(.-id (.-parentNode (.-parentNode (.-target %)))) click-events)
          grub-events (map-chan (fn [id] {:event :delete :_id id}) ids)]
      grub-events)))


(defn render-grub-list [grubs]
  (let [grub-list (sel1 :#grubList)
        sorted-grubs (sort-by :_id grubs)]
    (aset grub-list "innerHTML" "")
    (doseq [grub sorted-grubs]
      (dommy/append! grub-list (grub-template grub)))))

(defn push-outgoing-events []
  (fan-in outgoing-events [(get-added-events)
                           (get-completed-events)
                           (get-deleted-events)]))

(defn watch-for-state-changes []
  (add-watch state/grubs 
             :grub-add-watch
             (fn [key ref old new]
               (render-grub-list new))))

(defn init []
  (render-body)
  (watch-for-state-changes)
  (push-outgoing-events))
