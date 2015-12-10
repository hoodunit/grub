(ns grub.websocket
  (:require [cljs.core.async :as a :refer [<! >! chan]]
            goog.net.WebSocket
            goog.events.EventHandler
            goog.events.EventTarget
            [cognitect.transit :as t]
            [grub.event :as event])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def DEBUG false)

(def location (.-location js/document))
(def protocol (.-protocol location))
(def ws-protocol (if (= protocol "http:") "ws://" "wss://"))
(def host (.-host location))
(def path (str (.-pathname location) "ws"))
(def server-url (str ws-protocol host path))
(def reader (t/reader :json))
(def writer (t/writer :json))

(defn send-message [websocket msg]
  (when (.isOpen websocket)
    (when DEBUG (println "UP" msg))
    (.send websocket (t/write writer msg))))

(defn read-msg [msg]
  (let [received (t/read reader (.-message msg))]
    (when DEBUG (println "DOWN" received))
    received))

(defn connect [from-client to-client]
  (let [ws (goog.net.WebSocket.)
        handler (goog.events.EventHandler.)
        listen (fn [type fun] (.listen handler ws type fun false))]
    (listen goog.net.WebSocket.EventType.OPENED #(do (println "ws connected") (a/put! to-client (event/connected))))
    (listen goog.net.WebSocket.EventType.MESSAGE #(a/put! to-client (read-msg %)))
    (listen goog.net.WebSocket.EventType.CLOSED #(println "ws disconnected"))
    (listen goog.net.WebSocket.EventType.ERROR #(println "ws error:" %))
    (go (loop [] 
            (when-let [msg (<! from-client)]
              (send-message ws msg)
              (recur))))
    (.open ws server-url)
    ws))

(defn disconnect [ws]
  (.close ws))
