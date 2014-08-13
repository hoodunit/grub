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

(def websocket* (atom nil))

(defn sync-local-changes []
  (when (and (.isOpen @websocket*)
             (not= @state/app-state @state/client-shadow))
    (let [app-state @state/app-state
          client-shadow @state/client-shadow
          diff (sync/diff-states client-shadow app-state)
          msg {:diff diff 
               :hash (hasch/uuid app-state)
               :shadow-hash (hasch/uuid client-shadow)}]
        (logs "Sync because:")
        (logs "Server = " client-shadow)
        (logs "Client = " app-state)
        (logs "Diff:" diff)
        (logs "Send" (hasch/uuid client-shadow) "->" (hasch/uuid app-state))
      ;; TODO: reset client shadow only if send succeeds
      (.send @websocket* msg)
      (reset! state/client-shadow app-state))))

(defn on-connected [event]
  (log "Connected:" event)
  (sync-local-changes))

(defn on-message-fn [out]
  (fn [event] 
    (let [msg (cljs.reader/read-string (.-message event))]
      (a/put! out msg))))

(defn get-remote-chan [to-remote]
  (let [server-url (str "ws://" (.-host (.-location js/document)))
        handler (goog.events.EventHandler.)
        remote-events (chan)]
    (reset! websocket* (goog.net.WebSocket.))
    (.listen handler @websocket* goog.net.WebSocket.EventType.OPENED on-connected false)
    (.listen handler @websocket* goog.net.WebSocket.EventType.MESSAGE (on-message-fn remote-events) false)
    (.listen handler @websocket* goog.net.WebSocket.EventType.CLOSED #(log "Closed:" %) false)
    (.listen handler @websocket* goog.net.WebSocket.EventType.ERROR #(log "Error:" %) false)
    (add-watch state/app-state :app-state #(sync-local-changes))
    (.open @websocket* server-url)
    remote-events))
