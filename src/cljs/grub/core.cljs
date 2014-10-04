(ns grub.core
  (:require [grub.state :as state]
            [grub.websocket :as ws]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]))

(defn init-app []
  (let [current-state (atom state/empty-state)
        state-changes (view/render-app current-state)
        to-remote (chan)
        from-remote (chan)]
    (ws/connect-client! to-remote from-remote)
    (state/init-client from-remote to-remote state-changes current-state)))

(init-app)
