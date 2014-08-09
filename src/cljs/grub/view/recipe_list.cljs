(ns grub.view.recipe-list
  (:require [grub.view.dom :as dom]
            [grub.view.grub :as grub-view]
            [grub.view.recipe :as recipe]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! chan]]
            [cljs-uuid.core :as uuid])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn add-recipe [owner name grubs]
  (when (and (not (empty? name))
             (not (empty? grubs)))
    (let [recipes (om/get-props owner)
          new-recipe (recipe/new-recipe name grubs)]
      (om/set-state! owner :new-recipe-name "")
      (om/set-state! owner :new-recipe-grubs "")
      (om/transact! recipes #(assoc % (:id new-recipe) new-recipe)))))

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
                                  (add-recipe owner name grubs))
      nil)
    (om/set-state! owner :edit-state next)))

(defn new-recipe-view [_ owner]
  (reify
    om/IInitState
    (init-state [_]
      {:edit-state :waiting
       :new-recipe-name ""
       :new-recipe-grubs ""
       :unmounted false})

    om/IRenderState
    (render-state [this {:keys [edit-state new-recipe-name new-recipe-grubs]}]
      (html
       [:div.panel.panel-default.recipe-panel
        {:on-click #(when (not (dom/click-on-elem? % (om/get-node owner :save-btn)))
                      (transition-state owner :click))}
        [:div.panel-heading.recipe-header.new
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
          [:span.glyphicon.glyphicon-ok#save-recipe-btn]]]]))

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
    om/IInitState
    (init-state [_]
      {:remove-recipe-ch (chan)})
    om/IRenderState
    (render-state [_ {:keys [remove-recipe-ch]}]
      (html
       [:div
        [:h3.recipes-title "Recipes"]
        (om/build new-recipe-view recipes)
        [:ul#recipe-list.list-group.recipe-list
         (for [recipe (vals recipes)]
           (om/build recipe/view 
                     recipe 
                     {:key :id :opts {:remove-recipe-ch remove-recipe-ch}}))]]))
    om/IWillMount
    (will-mount [_]
      (let [remove-recipe-ch (om/get-state owner :remove-recipe-ch)]
        (go-loop []
                 (let [removed-id (<! remove-recipe-ch)]
                   (when-not (nil? removed-id)
                     (om/transact! recipes #(dissoc % removed-id)))))))))
