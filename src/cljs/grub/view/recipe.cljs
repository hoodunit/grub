(ns grub.view.recipe
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn recipe-view [recipe owner]
  (reify
    om/IRender
    (render [this]
      (let [{:keys [id name grubs]} recipe]
        (html
         [:div.panel.panel-default.recipe-panel
          {:id id
           :key id}
          [:div.panel-heading.recipe-header
           [:input.form-control.recipe-header-input 
            {:id "recipe-name"
             :type "text" 
             :placeholder "Grub pie"
             :value name}]
           [:button.btn.btn-primary.btn-sm.recipe-add-grubs-btn 
            {:type "button"}
            "Add Grubs"]]
          [:div.panel-body.recipe-grubs.hidden
           [:textarea.form-control.recipe-grubs-input
            {:id "recipe-grubs"
             :rows 3 
             :placeholder "2 grubs"}
            grubs]
           [:button.btn.btn-primary.hidden.pull-right.recipe-btn.recipe-done-btn
            {:type "button"} "Done"]]])))))

(defn recipes-view [recipes owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div
        [:h3.recipes-title "Recipes"]
        [:div.panel.panel-default.recipe-panel
         [:div.panel-heading.recipe-header
          [:input.form-control.recipe-header-input 
           {:id "recipe-name"
            :type "text" 
            :placeholder "Grub pie"}]
          [:button.btn.btn-primary.btn-sm.recipe-add-grubs-btn {:type "button"} "Add Grubs"]]
         [:div.panel-body.recipe-grubs.hidden
          [:textarea.form-control.recipe-grubs-input
           {:id "recipe-grubs"
            :rows 3 
            :placeholder "2 grubs"}]
          [:button.btn.btn-primary.hidden.pull-right.recipe-btn.recipe-done-btn
           {:type "button"} "Done"]]]
        [:ul#recipe-list.list-group.recipe-list
         (for [recipe (vals recipes)]
           (om/build recipe-view recipe {:key :id}))]]))))
