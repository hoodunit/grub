(ns grub.view.recipe-list
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [cljs-uuid.core :as uuid]
            [grub.view.dom :as dom]
            [grub.view.grub :as grub-view]
            [grub.view.recipe :as recipe])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn add-recipe [ch name grubs owner]
  (when (and (not (empty? name))
             (not (empty? grubs)))
    (om/set-state! owner :new-recipe-name "")
    (om/set-state! owner :new-recipe-grubs "")
    (put! ch (recipe/add-event name grubs))))

(def transitions
  {:waiting {:click :editing}
   :editing {:body-mousedown :waiting
             :save :waiting}})

(defn transition-state [owner event]
  (let [current (om/get-state owner :edit-state)
        next (or (get-in transitions [current event]) current)]
    (condp = [current event next]
      [:editing :save :waiting] (let [add-ch (om/get-shared owner :recipe-add)
                                      name (om/get-state owner :new-recipe-name)
                                      grubs (om/get-state owner :new-recipe-grubs)]
                                  (add-recipe add-ch name grubs owner))
      nil)
    (om/set-state! owner :edit-state next)))

(defn new-recipe-view [_ owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:edit-state :waiting
         :new-recipe-name ""
         :new-recipe-grubs ""
         :unmounted false}))

    om/IRenderState
    (render-state [this {:keys [edit-state new-recipe-name new-recipe-grubs]}]
      (html
       [:div.panel.panel-default.recipe-panel
        {:on-click #(when (not (dom/click-on-elem? % (om/get-node owner :save-btn)))
                      (transition-state owner :click))}
        [:div.panel-heading.recipe-header
         [:input.form-control.recipe-header-input 
          {:id "new-recipe-name"
           :type "text" 
           :placeholder "New recipe"
           :value new-recipe-name
           :on-change #(om/set-state! owner :new-recipe-name (dom/event-val %))}]]
        [:div.panel-body.recipe-grubs
         {:class (when (= edit-state :waiting) "hidden")}
         [:textarea.form-control.recipe-grubs-input
          {:id "new-recipe-grubs"
           :rows (inc (recipe/num-newlines new-recipe-grubs))
           :placeholder "Recipe ingredients"
           :value new-recipe-grubs
           :on-change #(om/set-state! owner :new-recipe-grubs (dom/event-val %))}]
         [:button.btn.btn-primary.pull-right.recipe-done-btn
          {:type "button"
           :ref :save-btn
           :on-click #(transition-state owner :save)}
          "Save"]]]))

    om/IWillMount
    (will-mount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (chan)]
        (a/sub <events :body-mousedown subscriber)
        (om/set-state! owner :subscriber subscriber)
        (go-loop [] (let [event (<! subscriber)]
                      (when-not (or (nil? event)
                                    (om/get-state owner :unmounted))
                        (when-not (dom/click-on-self? (:event event) (om/get-node owner))
                          (transition-state owner :body-mousedown))
                        (recur))))))
    
    om/IWillUnmount
    (will-unmount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (om/get-state owner :subscriber)]
        (om/set-state! owner :unmounted true)
        (a/unsub <events :body-mousedown subscriber)
        (a/close! (om/get-state owner :subscriber))))))

(defn view [recipes owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div
        [:h3.recipes-title "Recipes"]
        (om/build new-recipe-view recipes)
        [:ul#recipe-list.list-group.recipe-list
         (for [recipe (vals recipes)]
           (om/build recipe/view recipe {:key :id}))]]))))
