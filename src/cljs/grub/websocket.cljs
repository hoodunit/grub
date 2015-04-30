(ns grub.websocket
  (:require [cljs.core.async :as a :refer [<! >! chan]]
            goog.net.WebSocket
            goog.events.EventHandler
            goog.events.EventTarget
            [cognitect.transit :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [grub.macros :refer [log logs]]))

(def protocol (.-protocol (.-location js/document)))
(def ws-protocol (if (= protocol "http:") "ws://" "wss://"))
(def host (.-host (.-location js/document)))
(def server-url (str ws-protocol host))
(def reader (t/reader :json))
(def writer (t/writer :json))

(defn send-pending-msg [websocket pending-msg]
  (when (and (.isOpen websocket)
             (not (nil? @pending-msg)))
    (.send websocket (t/write writer @pending-msg))
    (reset! pending-msg nil)))

(defn on-connected [websocket pending-msg event]
  (log "Connected:" event)
  (send-pending-msg websocket pending-msg))

(defn read-msg [msg]
  (let [received (t/read reader (.-message msg))]
    received))

(defn connect [pending-msg in out]
  (let [ws (goog.net.WebSocket.)
        handler (goog.events.EventHandler.)
        listen (fn [type fun] (.listen handler ws type fun false))]
    (listen goog.net.WebSocket.EventType.OPENED (partial on-connected ws pending-msg))
    (listen goog.net.WebSocket.EventType.MESSAGE #(a/put! out (read-msg %)))
    (listen goog.net.WebSocket.EventType.CLOSED #(log "Closed:" %))
    (listen goog.net.WebSocket.EventType.ERROR #(log "Error:" %))
    (go (loop [] 
            (when-let [msg (<! in)]
              (reset! pending-msg msg)
              (send-pending-msg ws pending-msg) 
              (recur))))
    (.open ws server-url)
    ws))

(defn disconnect [ws]
  (.close ws))
