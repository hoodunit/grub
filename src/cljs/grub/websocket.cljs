(ns grub.websocket
  (:require [cljs.core.async :as a :refer [<! >! chan]]
            [cljs.reader]
            goog.net.WebSocket
            goog.events.EventHandler
            goog.events.EventTarget)
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [grub.macros :refer [log logs]]))

(def websocket* (atom nil))
(def pending-events (atom []))

(defn on-connected [event]
  (log "Connected:" event)
  (when (> (count @pending-events))
    (doseq [event @pending-events] (.send @websocket* event))
    (reset! pending-events [])))

(defn send-outgoing-events [ch]
  (go-loop []
           (let [event (<! ch)]
             (if (.isOpen @websocket*)
               (.send @websocket* event)
               (swap! pending-events conj event))
             (recur))))

(defn on-message-fn [out]
  (fn [event] 
    (let [grub-event (cljs.reader/read-string (.-message event))]
      (go (>! out grub-event)))))


(defn get-remote-chan [to-remote]
  (let [server-url (str "ws://" (.-host (.-location js/document)) "/ws")
        handler (goog.events.EventHandler.)
        remote-events (chan)]
    (reset! websocket* (goog.net.WebSocket.))
    (.listen handler @websocket* goog.net.WebSocket.EventType.OPENED on-connected false)
    (.listen handler @websocket* goog.net.WebSocket.EventType.MESSAGE (on-message-fn remote-events) false)
    (.listen handler @websocket* goog.net.WebSocket.EventType.CLOSED #(log "Closed:" %) false)
    (.listen handler @websocket* goog.net.WebSocket.EventType.ERROR #(log "Error:" %) false)
    (send-outgoing-events to-remote)
    (go (>! to-remote {:event :send-all-items}))
    (.open @websocket* server-url)
    remote-events))
