(ns grub.view.grub-list
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.dom :as dom]
            [grub.view.grub :as grub-view]
            [cljs-uuid.core :as uuid])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn get-grub-ingredient [grub]
  (when-not (nil? (:grub grub))
    (let [text (clojure.string/lower-case (:grub grub))
          match (re-find #"[a-z]{3}.*$" text)]
      match)))

(defn sort-grubs [grubs]
  (sort-by (juxt :completed get-grub-ingredient :grub) (vals grubs)))

(defn add-grub [add new-grub owner]
  (when (not (empty? new-grub))
    (om/set-state! owner :new-grub "")
    (put! add (grub-view/add-event new-grub))))

(defn view [props owner]
  (reify
    om/IInitState
    (init-state [_]
      {:new-grub ""})
    om/IRenderState
    (render-state [this {:keys [new-grub] :as state}]
      (let [add (om/get-shared owner :grub-add)]
        (html 
         [:div 
          [:h3 "Grub List"]
          [:div.input-group.add-grub-input-form
           [:span.input-group-btn
            [:input.form-control#add-grub-input 
             {:type "text" 
              :placeholder "What do you need?"
              :value new-grub
              :on-key-up #(when (dom/enter-pressed? %)
                            (add-grub add new-grub owner))
              :on-change #(om/set-state! owner :new-grub (dom/event-val %))}]]
           [:button.btn.btn-primary 
            {:id "add-grub-btn" 
             :type "button"
             :on-click #(add-grub add new-grub owner)}
            [:span.glyphicon.glyphicon-plus]]]
          [:ul#grub-list.list-group
           (for [grub (sort-grubs props)]
             (om/build grub-view/view grub {:key :id}))]
          [:button.btn.pull-right 
           {:id "clear-all-btn" 
            :class (when (empty? props) "hidden")
            :type "button"
            :on-click #(put! (om/get-shared owner :grub-clear-all)
                             {:event :clear-all-grubs})}
           "Clear all"]])))))
