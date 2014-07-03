(ns grub.view.app
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.grub :as grub]
            [grub.view.recipe :as recipe])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn app-view [state owner]
  (reify
    om/IRender
    (render [this]
      (html 
       [:div.container
        [:div.row
         [:div.col-sm-6.leftmost-column
          (om/build grub/grubs-view (:grubs state))]
         [:div.col-sm-6
          (om/build recipe/recipes-view (:recipes state))]]]))
    om/IWillMount
    (will-mount [_]
      (let [body-elem (aget (.getElementsByTagName js/document "body") 0)]
        (log "body:" body-elem)
        (.addEventListener body-elem "mousedown" 
                           #(put! (om/get-shared owner :events-in) {:type :body-mousedown
                                                                    :event %}))))))
    
(defn render-app [state]
  (let [add (chan)
        update (chan)
        clear-all (chan)
        out (a/merge [add update clear-all])
        events-in (chan)
        events (a/pub events-in :type)]
    (om/root app-view 
             state 
             {:target (.getElementById js/document "container")
              :shared {:add add
                       :update update
                       :clear-all clear-all
                       :events events
                       :events-in events-in}})
    out))
