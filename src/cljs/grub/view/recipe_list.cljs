(ns grub.view.recipe-list
  (:require [grub.view.dom :as dom]
            [grub.view.grub :as grub-view]
            [grub.view.recipe :as recipe]
            [grub.view.recipe-new :as recipe-new]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! chan]]
            [cljs-uuid.core :as uuid])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn view [recipes owner]
  (reify
    om/IInitState
    (init-state [_]
      {:add-recipe-ch (chan)
       :remove-recipe-ch (chan)})
    om/IRenderState
    (render-state [_ {:keys [remove-recipe-ch add-recipe-ch]}]
      (html
       [:div
        [:h3.recipes-title "Recipes"]
        (om/build recipe-new/view {} {:opts {:add-recipe-ch add-recipe-ch}})
        [:ul#recipe-list.list-group.recipe-list
         (for [recipe (vals recipes)]
           (om/build recipe/view 
                     recipe 
                     {:key :id :opts {:remove-recipe-ch remove-recipe-ch}}))]]))
    om/IWillMount
    (will-mount [_]
      (let [add-recipe-ch (om/get-state owner :add-recipe-ch)
            remove-recipe-ch (om/get-state owner :remove-recipe-ch)]
        (go (loop []
              (let [removed-id (<! remove-recipe-ch)]
                (when-not (nil? removed-id)
                  (om/transact! recipes nil #(dissoc % removed-id) :local))
                (recur))))
        (go (loop []
              (let [added (<! add-recipe-ch)]
                (om/transact! recipes nil #(assoc % (:id added) added) :local)
                (recur))))))))
