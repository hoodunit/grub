(ns grub-client.core
  (:require [grub-client.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [grub-client.view :as view]
            [grub-client.websocket :as ws]
            [grub-client.state :as state]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub-client.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn handle-grub-events []
  (let [local-events (view/get-local-events)
        [local-events' local-events''] (fan-out local-events 2)
        remote-events (ws/get-remote-events)
        events (fan-in [local-events' remote-events])]
    (do-chan! ws/send-to-server local-events'')
    (go-loop (state/handle-event (<! events)))))

(defn init []
  (view/render-body)
  (ws/connect-to-server)
  (handle-grub-events))

(init)
