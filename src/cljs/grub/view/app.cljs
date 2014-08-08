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
  (let [grub-add (chan)
        grub-update (chan)
        grub-clear-all (chan)
        grub-remove (chan)
        recipe-add (chan)
        recipe-add-grubs (chan)
        recipe-update (chan)
        out (a/merge [grub-add grub-update grub-clear-all grub-remove
                      recipe-add recipe-add-grubs recipe-update])
        >events (chan)
        <events (a/pub >events :type)]
    (om/root app-view 
             state 
             {:target (.getElementById js/document "container")
              :shared {:grub-add grub-add
                       :grub-update grub-update
                       :grub-clear-all grub-clear-all
                       :grub-remove grub-remove
                       :recipe-add recipe-add
                       :recipe-add-grubs recipe-add-grubs
                       :recipe-update recipe-update
                       :>events >events
                       :<events <events}})
    out))
