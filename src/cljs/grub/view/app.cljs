(ns grub.view.app
  (:require [grub.view.dom :as dom]
            [grub.view.grub-list :as grub-list]
            [grub.view.recipe-list :as recipe-list]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn app-view [props owner]
  (reify
    om/IRender
    (render [this]
      (html 
       [:div.container
        [:div.row
         [:div.col-sm-6.leftmost-column
          (om/build grub-list/view (:grubs props))]
         [:div.col-sm-6
          (om/build recipe-list/view (:recipes props))]]]))
    om/IWillMount
    (will-mount [_]
      (let [>events (om/get-shared owner :>events)]
        (dom/on-document-mousedown #(put! >events {:type :body-mousedown :event %}))
        (dom/on-window-scroll #(put! >events {:type :body-scroll :event %}))))))
    
(defn render-app [initial-state remote-states local-states]
  (let [state (atom initial-state)
        >events (chan)
        <events (a/pub >events :type)
        add-grubs-ch (chan)]
    (om/root app-view 
             state 
             {:target (.getElementById js/document "container")
              :shared {:>events >events
                       :<events <events
                       :add-grubs-ch add-grubs-ch}
              :tx-listen (fn [{:keys [new-state]} _] 
                           (put! local-states new-state))})
    (go (loop [] (when-let [new-state (<! remote-states)]
                   (reset! state new-state)
                   (recur))))))
