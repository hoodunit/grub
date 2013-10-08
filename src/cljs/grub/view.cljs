(ns grub.view
  (:require [grub.view.dom :as dom]
            [grub.view.grub :as grub-view]
            [grub.view.recipe :as recipe-view]
            [dommy.core :as dommy]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs and-let]]
                   [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn setup-and-get-view-events [remote-channel]
  (dom/render-body)
  (let [out (chan)
        remote (a/mult remote-channel)
        to-grubs (chan)
        to-recipes (chan)
        from-grubs (grub-view/handle-grubs to-grubs)
        from-recipes (a/mult (recipe-view/handle-recipes to-recipes))]
    (a/tap remote to-grubs)
    (a/tap remote to-recipes)
    (a/tap from-recipes to-grubs)
    (a/tap from-recipes out)
    (a/pipe from-grubs out)
    out))
