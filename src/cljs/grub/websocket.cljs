(ns grub.websocket
  (:require [grub.state :as state]
            [grub.sync :as sync]
            [cljs.core.async :as a :refer [<! >! chan]]
            [cljs.reader]
            goog.net.WebSocket
            goog.events.EventHandler
            goog.events.EventTarget
            [hasch.core :as hasch])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [grub.macros :refer [log logs]]))

(def server-url (str "ws://" (.-host (.-location js/document))))
(def pending-msg (atom nil))

(defn send-pending-msg [websocket]
  (when (and (.isOpen websocket)
             (not (nil? @pending-msg)))
    (.send websocket (pr-str @pending-msg))
    (reset! pending-msg nil)))

(defn on-connected [websocket event]
  (log "Connected:" event)
  (send-pending-msg websocket))

(defn on-message [from event]
  (let [msg (cljs.reader/read-string (.-message event))]
    (a/put! from msg)))

(defn connect-client! [to from]
  (let [handler (goog.events.EventHandler.)
        websocket (goog.net.WebSocket.)
        listen (fn [type fun] (.listen handler websocket type fun false))]
    (listen goog.net.WebSocket.EventType.OPENED (partial on-connected websocket))
    (listen goog.net.WebSocket.EventType.MESSAGE (partial on-message from))
    (listen goog.net.WebSocket.EventType.CLOSED #(log "Closed:" %))
    (listen goog.net.WebSocket.EventType.ERROR #(log "Error:" %))
    (go (loop [] 
            (when-let [msg (<! to)]
              (reset! pending-msg msg)
              (send-pending-msg websocket) 
              (recur))))
    (.open websocket server-url)))
