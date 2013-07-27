(ns grub-client.core
  ;(:use-macros [dommy.macros :only [deftemplate sel1 node]])
  (:require [dommy.core :as dommy]
            [cljs.core.async :as async :refer [<! >! chan close! timeout]])
  (:require-macros [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :as m :refer [go alt!]]))

(defn log [& args]
  (apply #(.log js/console %) args))

(def test-grubs
  ["8 dl water"
   "8 whole peppercorns"
   "2 bay leaves"
   "1 - 2 (150 g) onions"
   "2 dl cream"
   "1 dl dill"])

(deftemplate grub-template [grub]
  [:tr 
   [:td 
    [:label.checkbox [:input {:type "checkbox"}] grub]]])

(def add-grub-text 
  (node [:input.input-medium {:type "text" :placeholder "2 grubs"}]))

(def add-grub-btn 
  (node [:button.btn {:type "button"} "Add"]))

(deftemplate main-template [grubs]
  [:div.container
   [:div.row-fluid
    [:div.span8.offset2
     [:h2 "Grub List"]
     [:table.table 
      [:tbody#grubList
       (for [grub grubs] (grub-template grub))]]
     [:div.input-append
      add-grub-text
      add-grub-btn]]]])

(def add-grub-chan (chan))

(defn on-add-grub-clicked [& args]
  (let [new-grub (dommy/value add-grub-text)]
    (dommy/set-value! add-grub-text "")
    (go (>! add-grub-chan new-grub))))

(defn add-grub [grub]
  (dommy/append! (sel1 :#grubList) (grub-template grub)))

(dommy/prepend! (sel1 :body) (main-template test-grubs))
(dommy/listen! add-grub-btn :click on-add-grub-clicked)

(go (while true 
      (let [new-grub (<! add-grub-chan)]
        (log new-grub)
        (add-grub new-grub))))
