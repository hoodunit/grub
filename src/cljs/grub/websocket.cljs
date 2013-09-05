(ns grub.websocket
  (:require [cljs.core.async :as a :refer [<! >! chan]]
            [cljs.reader])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [grub.macros :refer [log logs]]))

(def websocket* (atom nil))

(defn send-outgoing-events [ch]
  (go-loop []
   (let [event (<! ch)]
     (.send @websocket* event)
     (recur))))

(defn receive-remote-events []
  (let [out (chan)]
    (aset @websocket* 
          "onmessage" 
          (fn [event] 
            (let [grub-event (cljs.reader/read-string (.-data event))]
              (go (>! out grub-event)))))
    out))

(defn get-remote-chan [to-remote]
  (let [server-url (str "ws://" (.-host (.-location js/document)) "/ws")]
    (reset! websocket* (js/WebSocket. server-url))
    (aset @websocket* "onopen" (fn [event] (log "Connected:" event)))
    (aset @websocket* "onclose" (fn [event] (log "Connection closed:" event)))
    (aset @websocket* "onerror" (fn [event] (log "Connection error:" event)))
    (send-outgoing-events to-remote)
    (receive-remote-events)))
