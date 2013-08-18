(ns grub.websocket
  (:require [grub.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(def incoming-events (chan))
(def outgoing-events (chan))

(def websocket* (atom nil))

(defn handle-incoming-events []
  (go-loop
   (let [event (<! incoming-events)]
     (.send @websocket* event))))

(defn handle-outgoing-events []
  (aset @websocket* "onmessage" (fn [event] 
                                  (let [grub-event (cljs.reader/read-string (.-data event))]
                                    (logs "Received:" grub-event)
                                    (go (>! outgoing-events grub-event))))))
  
(defn connect-to-server []
  (reset! websocket* (js/WebSocket. "ws://localhost:3000/ws"))
  (handle-incoming-events)
  (handle-outgoing-events))
