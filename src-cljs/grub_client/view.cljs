(ns grub-client.view
  (:require [grub-client.async-utils 
             :refer [do-chan! do-chan event-chan map-chan fan-in filter-chan]]
            [dommy.core :as dommy]
            [cljs.core.async :refer [<! >! chan]])
  (:require-macros [grub-client.macros :refer [log go-loop]]
                   [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go]]))

(deftemplate grub-template [grub]
  [:tr 
   [:td 
    [:div.checkbox.grubCheckbox [:label {:id (:id grub)}
                                 [:input {:type "checkbox"}] 
                                 (:grub grub)]]]])

(def add-grub-text 
  (node [:input.form-control {:type "text" :placeholder "2 grubs"}]))

(def add-grub-btn 
  (node [:button.btn.btn-default {:type "button"} "Add"]))

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

(defn render-body []
  (dommy/prepend! (sel1 :body) (main-template)))

(defn add-grub-to-dom [grub-obj]
  (log "Adding" (str grub-obj))
  (dommy/append! (sel1 :#grubList) (grub-template grub-obj)))

(defn add-grub [grub]
  (add-grub-to-dom grub))

(defn complete-grub [grub]
  (log "complete-grub:" (str grub))
  (aset (sel1 [(str "#" (:id grub)) "input"]) "checked" true))

(defn uncomplete-grub [grub]
  (log "uncomplete-grub:" (str grub))
  (aset (sel1 [(str "#" (:id grub)) "input"]) "checked" false))

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
         (map-chan (fn [g] {:event :create :grub g :id (str "grub-" (.now js/Date))})))))

(defn get-completed-event [event]
  (let [target (.-target event)
        checked (.-checked target)
        event-type (if checked :complete :uncomplete)
        label (aget (.-labels (.-target event)) 0)
        grub (.-textContent label)
        id (.-id label)]
    {:grub grub :id id :event event-type}))

(defn get-completed-events []
  (let [events (:chan (event-chan (sel1 :#grubList) "change"))
        grubs (map-chan #(get-completed-event %) events)]
    grubs))
  
