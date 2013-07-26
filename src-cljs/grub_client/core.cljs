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

(deftemplate main-template [grubs]
  [:div.container
   [:div.row-fluid
    [:div.span8.offset2
     [:h2 "Grub List"]
     [:table.table 
      [:tbody
       (for [grub grubs] (grub-template grub))]]
     [:div.input-append
      [:input.span2#addGrubText {:type "text"}]
      [:button.btn#addGrubButton {:type "button"} "Add"]]]]])

(def add-grub-chan (chan))

(defn on-add-grub-clicked [& args]
  (let [new-grub (dommy/value (sel1 :#addGrubText))]
    (go (>! add-grub-chan new-grub))))

(dommy/prepend! (sel1 :body) (main-template test-grubs))
(dommy/listen! (sel1 :#addGrubButton) :click on-add-grub-clicked)

(go (while true (log (<! add-grub-chan))))
