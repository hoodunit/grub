(ns grub.websocket
  (:require [grub.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [grub.macros :refer [log logs go-loop]]))

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
                                    (go (>! outgoing-events grub-event))))))

(defn connect-to-server []
  (let [server-url (str "ws://" (.-host (.-location js/document)) "/ws")]
    (reset! websocket* (js/WebSocket. server-url))
    (aset @websocket* "onopen" (fn [event] (log "Connected:" event)))
    (aset @websocket* "onclose" (fn [event] (log "Connection closed:" event)))
    (aset @websocket* "onerror" (fn [event] (log "Connection error:" event)))
    (handle-incoming-events)
    (handle-outgoing-events)))
