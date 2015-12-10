(ns grub.view.recipe-new
  (:require [grub.view.dom :as dom]
            [grub.view.recipe :as recipe]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.async :as a :refer [<! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn add-recipe [owner name grubs directions]
  (when (and (not (empty? name))
             (not (empty? grubs)))
    (let [new-recipe (recipe/new-recipe name grubs directions)
          add-recipe-ch (om/get-state owner :add-recipe-ch)]
      (om/set-state! owner :new-recipe-name "")
      (om/set-state! owner :new-recipe-grubs "")
      (om/set-state! owner :new-recipe-directions "")
      (a/put! add-recipe-ch new-recipe))))

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
                                      grubs (om/get-state owner :new-recipe-grubs)
                                      directions (om/get-state owner :new-recipe-directions)]
                                  (add-recipe owner name grubs directions))
      nil)
    (om/set-state! owner :edit-state next)))

(defn view [_ owner {:keys [add-recipe-ch]}]
  (reify
    om/IInitState
    (init-state [_]
      {:add-recipe-ch add-recipe-ch
       :edit-state :waiting
       :new-recipe-name ""
       :new-recipe-grubs ""
       :new-recipe-directions ""
       :unmounted false})

    om/IRenderState
    (render-state [this {:keys [edit-state new-recipe-name new-recipe-grubs new-recipe-directions]}]
      (html
       [:div.panel.panel-default.recipe-panel
        {:on-click #(when (not (dom/click-on-elem? % (om/get-node owner "save-btn")))
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
           :placeholder "Ingredients"
           :value new-recipe-grubs
           :on-change #(om/set-state! owner :new-recipe-grubs (dom/event-val %))}]
         [:textarea.form-control.recipe-grubs-input
          {:ref :textarea
           :rows 8
           :value new-recipe-directions
           :placeholder "Directions"
           :on-change #(om/set-state! owner :new-recipe-directions (dom/event-val %))}]
         [:button.btn.btn-primary.pull-right.recipe-done-btn
          {:type     "button"
           :ref      "save-btn"
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
