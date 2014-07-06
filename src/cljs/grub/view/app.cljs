(ns grub.view.app
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.dom :as dom]
            [grub.view.grub :as grub]
            [grub.view.recipe :as recipe])
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
          (om/build grub/grubs-view (:grubs props))]
         [:div.col-sm-6
          (om/build recipe/recipes-view (:recipes props))]]]))
    om/IWillMount
    (will-mount [_]
      (let [>events (om/get-shared owner :>events)]
        (dom/on-body-mousedown #(put! >events {:type :body-mousedown :event %}))))))
    
(defn render-app [state]
  (let [add (chan)
        update (chan)
        clear-all (chan)
        out (a/merge [add update clear-all])
        >events (chan)
        <events (a/pub >events :type)]
    (om/root app-view 
             state 
             {:target (.getElementById js/document "container")
              :shared {:add add
                       :update update
                       :clear-all clear-all
                       :>events >events
                       :<events <events}})
    out))
