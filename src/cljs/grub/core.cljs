(ns grub.core
  (:require [grub.view :as view]
            [grub.websocket :as ws]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn wire-channels-together []
  (let [to-remote (chan)
        to-view (chan)
        from-remote (ws/get-remote-chan to-remote)
        from-view (view/setup-and-get-view-events to-view)]
    (a/pipe from-remote to-view)
    (a/pipe from-view to-remote)))

(wire-channels-together)
