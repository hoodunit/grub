(ns grub.core
  (:require [grub.state :as state]
            [grub.websocket :as ws]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]))

(defn init-app []
  (let [local-states (chan)
        remote-states (chan)
        to-remote (chan)
        from-remote (chan)]
    (view/render-app state/empty-state remote-states local-states)
    (ws/connect-client! to-remote from-remote)
    (state/init-client from-remote to-remote local-states remote-states)))

(init-app)
