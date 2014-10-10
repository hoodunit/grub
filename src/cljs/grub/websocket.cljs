(ns grub.websocket
  (:require [cljs.core.async :as a :refer [<! >! chan]]
            goog.net.WebSocket
            goog.events.EventHandler
            goog.events.EventTarget
            [cognitect.transit :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [grub.macros :refer [log logs]]))

(def server-url (str "ws://" (.-host (.-location js/document))))
(def pending-msg (atom nil))
(def reader (t/reader :json))
(def writer (t/writer :json))

(defn send-pending-msg [websocket]
  (when (and (.isOpen websocket)
             (not (nil? @pending-msg)))
    (.send websocket (t/write writer @pending-msg))
    (reset! pending-msg nil)))

(defn on-connected [websocket event]
  (log "Connected:" event)
  (send-pending-msg websocket))

(defn read-msg [msg]
  (t/read reader (.-message msg)))

(defn connect-client! [in out]
  (let [handler (goog.events.EventHandler.)
        websocket (goog.net.WebSocket.)
        listen (fn [type fun] (.listen handler websocket type fun false))]
    (listen goog.net.WebSocket.EventType.OPENED (partial on-connected websocket))
    (listen goog.net.WebSocket.EventType.MESSAGE #(a/put! out (read-msg %)))
    (listen goog.net.WebSocket.EventType.CLOSED #(log "Closed:" %))
    (listen goog.net.WebSocket.EventType.ERROR #(log "Error:" %))
    (go (loop [] 
            (when-let [msg (<! in)]
              (reset! pending-msg msg)
              (send-pending-msg websocket) 
              (recur))))
    (.open websocket server-url)))
