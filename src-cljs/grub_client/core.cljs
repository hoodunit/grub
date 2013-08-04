(ns grub-client.core
  (:require [grub-client.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [grub-client.view :as view]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub-client.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(def websocket* (atom nil))

(defn connect-to-server []
  (reset! websocket* (js/WebSocket. "ws://localhost:3000/ws")))

(defn get-local-events []
  (fan-in [(view/get-added-events)
           (view/get-completed-events)
           (view/get-deleted-events)]))

(defn get-remote-events []
  (let [out (chan)]
    (aset @websocket* "onmessage" (fn [event] 
                                    (let [grub-event (cljs.reader/read-string (.-data event))]
                                      (logs "Received:" grub-event)
                                      (go (>! out grub-event)))))
    out))

(defn send-to-server [event]
  (.send @websocket* event))

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
        remote-events (get-remote-events)
        events (fan-in [local-events' remote-events])]
    (do-chan! send-to-server local-events'')
    (go-loop (handle-event (<! events)))))

(defn init []
  (view/render-body)
  (connect-to-server)
  (handle-grub-events))

(init)
