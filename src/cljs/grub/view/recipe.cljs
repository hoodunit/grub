(ns grub.view.recipe
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.dom :as dom])
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
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:editing false
         :>local-events publisher
         :<local-events (a/pub publisher identity)}))

    om/IRenderState
    (render-state [this {:keys [editing >local-events]}]
      (html
       [:div
        [:h3.recipes-title "Recipes"]
        [:div.panel.panel-default.recipe-panel
         {:on-click #(put! >local-events :click)}
         [:div.panel-heading.recipe-header
          [:input.form-control.recipe-header-input 
           {:id "recipe-name"
            :type "text" 
            :placeholder "Grub pie"}]]
         [:div.panel-body.recipe-grubs
          {:class (when (not editing) "hidden")}
          [:textarea.form-control.recipe-grubs-input
           {:id "recipe-grubs"
            :rows 3 
            :placeholder "2 grubs"}]
          [:button.btn.btn-primary.hidden.pull-right.recipe-btn.recipe-done-btn
           {:type "button"} "Done"]]]
        [:ul#recipe-list.list-group.recipe-list
         (for [recipe (vals recipes)]
           (om/build recipe-view recipe {:key :id}))]]))
    
    om/IWillMount
    (will-mount [_]
      (let [<local-events (om/get-state owner :<local-events)
            <events (om/get-shared owner :<events)]
        (go-loop []
                 (log "waiting")
                 (let [subscriber (chan)]
                   (a/sub <local-events :click subscriber)
                   (<! subscriber)
                   (a/unsub <local-events :click subscriber)
                   (a/close! subscriber))
                 (om/set-state! owner :editing true)
                 (log "editing")
                 (let [subscriber (chan)]
                   (a/sub <events :body-mousedown subscriber)
                   (loop []
                     (let [event (<! subscriber)]
                       (when (and (= (:type event) :body-mousedown)
                                  (dom/click-on-self? (:event event) (om/get-node owner)))
                         (recur))))
                   (a/unsub <events :body-mousedown subscriber)
                   (a/close! subscriber))
                 (om/set-state! owner :editing false)
                 (recur))))))
