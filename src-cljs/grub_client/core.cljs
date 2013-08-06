(ns grub-client.core
  (:require [grub-client.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [grub-client.view :as view]
            [grub-client.websocket :as ws]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub-client.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn get-local-events []
  (fan-in [(view/get-added-events)
           (view/get-completed-events)
           (view/get-deleted-events)]))

(defmulti handle-event :event :default :unknown-event)

(defmethod handle-event :create [event]
  (view/add-grub event))

(defmethod handle-event :complete [event]
  (view/complete-grub event))

(defmethod handle-event :uncomplete [event]
  (view/uncomplete-grub event))

(defmethod handle-event :delete [event]
  (view/delete-grub event))

(defmethod handle-event :unknown-event [event]
  (logs "Cannot handle unknown event:" event))

(defn handle-grub-events []
  (let [local-events (get-local-events)
        [local-events' local-events''] (fan-out local-events 2)
        remote-events (ws/get-remote-events)
        events (fan-in [local-events' remote-events])]
    (do-chan! ws/send-to-server local-events'')
    (go-loop (handle-event (<! events)))))

(defn init []
  (view/render-body)
  (ws/connect-to-server)
  (handle-grub-events))

(init)
