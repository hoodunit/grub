(ns grub.websocket
  (:require [grub.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(def websocket* (atom nil))

(defn connect-to-server []
  (reset! websocket* (js/WebSocket. "ws://localhost:3000/ws")))

(defn get-remote-events []
  (let [out (chan)]
    (aset @websocket* "onmessage" (fn [event] 
                                    (let [grub-event (cljs.reader/read-string (.-data event))]
                                      (logs "Received:" grub-event)
                                      (go (>! out grub-event)))))
    out))

(defn send-to-server [event]
  (.send @websocket* event))
