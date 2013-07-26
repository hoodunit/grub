(ns grub-client.core
  (:use-macros [dommy.macros :only [deftemplate sel1 node]])
  (:require [dommy.core :as dommy]))

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

(defn onClicked []
  (log "onClicked"))

(dommy/prepend! (sel1 :body) (main-template test-grubs))

(dommy/listen! (sel1 :#addGrubButton) :click onClicked)
