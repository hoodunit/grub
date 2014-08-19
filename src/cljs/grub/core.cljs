(ns grub.core
  (:require [grub.state :as state]
            [grub.websocket :as ws]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]))

(defn init-app []
  (view/render-app state/app-state)
  (let [to-remote (chan)
        from-remote (chan)]
    (ws/connect-client! to-remote from-remote)
    (state/sync-state! from-remote to-remote)))

(init-app)
