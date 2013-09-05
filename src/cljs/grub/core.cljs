(ns grub.core
  (:require [grub.view :as view]
            [grub.websocket :as ws]
            [grub.state :as state]
            [cljs.core.async :as a :refer [<! >! chan]]
            [cljs.reader])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn wire-channels-together []
  (let [to-remote (chan)
        to-state (chan)
        to-view (chan)
        from-remote (a/mult (ws/get-remote-chan to-remote))
        from-view (a/mult (view/setup-and-get-view-events to-view))]
    (state/handle-incoming-events to-state)
    (a/tap from-view to-state)
    (a/tap from-view to-remote)
    (a/tap from-remote to-state)
    (a/tap from-remote to-view)))

(wire-channels-together)
