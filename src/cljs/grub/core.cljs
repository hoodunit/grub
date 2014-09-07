(ns grub.core
  (:require [grub.state :as state]
            [grub.websocket :as ws]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]))

(defn connect-to-server [reset? state-changes]
  (let [to-remote (chan)
        from-remote (chan)]
    (ws/connect-client! to-remote from-remote)
    (state/sync-state! from-remote to-remote reset? state-changes)))

(defn init-app []
  (let [state-changes (view/render-app state/state)]
    (connect-to-server true state-changes)))

(init-app)
