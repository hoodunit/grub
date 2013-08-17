(ns grub.core
  (:require [grub.async-utils :as a]
            [grub.view :as view]
            [grub.websocket :as ws]
            [grub.state :as state]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn handle-grub-events []
  (let [local-events (view/get-local-events)
        [local-events' local-events''] (a/fan-out local-events 2)
        remote-events (ws/get-remote-events)
        events (a/fan-in [local-events' remote-events])]
    (a/do-chan! ws/send-to-server local-events'')
    (a/copy-chan state/incoming-events events)))

(defn init []
  (view/render-body)
  (ws/connect-to-server)
  (handle-grub-events))

(init)
