(ns grub-client.view
  (:require [dommy.core :as dommy]
            [cljs.core.async :refer [<! >! chan]])
  (:require-macros [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go]]))

(deftemplate grub-template [grub]
  [:tr 
   [:td 
    [:div.checkbox [:label [:input {:type "checkbox"}] grub]]]])

(def add-grub-text 
  (node [:input.form-control {:type "text" :placeholder "2 grubs"}]))

(def add-grub-btn 
  (node [:button.btn.btn-default {:type "button"} "Add"]))

(deftemplate main-template [grubs]
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

(defn append-new-grub [grub]
  (dommy/append! (sel1 :#grubList) (grub-template grub)))

(defn push-new-grub [out]
  (let [new-grub (dommy/value add-grub-text)]
    (dommy/set-value! add-grub-text "")
    (go (>! out new-grub))))

(defn get-grubs-from-clicks []
  (let [out (chan)]
    (dommy/listen! add-grub-btn :click #(push-new-grub out))
    out))

(defn put-grubs-if-enter-pressed [out event]
  (when (= (.-keyIdentifier event) "Enter")
                (push-new-grub out)))

(defn get-grubs-from-enter []
  (let [out (chan)]
    (dommy/listen! add-grub-text 
                   :keyup 
                   (partial put-grubs-if-enter-pressed out))
    out))
