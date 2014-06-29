(ns grub.core
  (:require [grub.state :as state]
            [grub.websocket :as ws]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn wire-channels-together []
  (let [to-remote (chan)
        to-state (chan)
        from-remote (ws/get-remote-chan to-remote)
        from-state (state/update-state-and-render to-state)]
    (a/pipe from-remote to-state)
    (a/pipe from-state to-remote)))

(wire-channels-together)
