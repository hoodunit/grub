(ns grub.view
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def add (chan))
(def out add)

(defn recipe-view [recipe owner]
  (reify
    om/IRender
    (render [this]
      (let [{:keys [id name grubs]} recipe]
        (html
         [:div.panel.panel-default.recipe-panel
          {:id id}
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
           (om/build recipe-view recipe))]]))))

(defn grub-view [grub-state owner]
  (reify
    om/IRender
    (render [this]
      (let [{:keys [id grub completed]} grub-state]
        (html
         [:li.list-group-item.grub-item 
          {:id id
           :class (when completed "completed")} 
          [:span.grub-static
           (if completed
             [:span.glyphicon.glyphicon-check]
             [:span.glyphicon.glyphicon-unchecked])
           [:span.grub-text grub]]
          [:input.grub-input {:type "text" :value grub}]])))))

(defn get-grub-ingredient [grub]
  (let [text (clojure.string/lower-case (:grub grub))
        match (re-find #"[a-z]{3}.*$" text)]
    match))

(defn sort-grubs [grubs]
  (sort-by (juxt :completed get-grub-ingredient) (vals grubs)))

(defn add-grub-event [grub]
  {:event :add-grub 
   :id (str "grub-" (.now js/Date))
   :grub grub
   :completed false})

(defn add-grub [add owner]
  (let [new-grub (.-value (om/get-node owner :new-grub))]
    (when (not (empty? new-grub))
      (put! add (add-grub-event new-grub)))))

(defn enter-pressed? [event]
  (let [enter-keycode 13]
    (= (.-which event) enter-keycode)))

(defn add-grub-on-enter [add owner event]
  (when (enter-pressed? event)
    (add-grub add owner)))

(defn grubs-view [grubs owner]
  (reify
    om/IRender
    (render [this]
      (let [add (:add (om/get-shared owner))]
        (html 
         [:div 
          [:h3 "Grub List"]
          [:div.input-group.add-grub-input-form
           [:span.input-group-btn
            [:input.form-control#add-grub-input 
             {:ref :new-grub
              :type "text" 
              :placeholder "2 grubs"
              :on-key-up #(add-grub-on-enter add owner %)}]]
           [:button.btn.btn-primary 
            {:id "add-grub-btn" 
             :type "button"
             :on-click #(add-grub add owner)}
            "Add"]]
          [:ul#grub-list.list-group
           (for [grub (sort-grubs grubs)]
             (om/build grub-view grub))]
          [:button.btn.hidden.pull-right 
           {:id "clear-all-btn" :type "button"}
           "Clear all"]])))))

(defn app-view [state owner]
  (reify
    om/IRender
    (render [this]
      (html 
       [:div.container
        [:div.row
         [:div.col-sm-6.leftmost-column
          (om/build grubs-view (:grubs state))]
         [:div.col-sm-6
          (om/build recipes-view (:recipes state))]]]))))
    
(defn render-app [state]
  (om/root app-view 
           state 
           {:target (.getElementById js/document "container")
            :shared {:add add}}))
