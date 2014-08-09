(ns grub.core
  (:require [grub.state :as state]
            [grub.websocket :as ws]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]))

(defn init-app []
  (view/render-app state/app-state)
  (let [to-remote (chan)
        to-state (chan)
        from-remote (ws/get-remote-chan to-remote)
        from-state (state/update-state-on-event! to-state)]
    (a/pipe from-remote to-state)
    (a/pipe from-state to-remote)))

(init-app)
