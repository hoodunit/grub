(ns grub.view.app
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.dom :as dom]
            [grub.view.grub-list :as grub-list]
            [grub.view.recipe-list :as recipe-list])
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
    
(defn render-app [state]
  (let [>events (chan)
        <events (a/pub >events :type)
        add-grubs-ch (chan)]
    (om/root app-view 
             state 
             {:target (.getElementById js/document "container")
              :shared {:>events >events
                       :<events <events
                       :add-grubs-ch add-grubs-ch}})))
