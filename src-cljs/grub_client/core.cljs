(ns grub-client.core
  (:require [grub-client.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan do-chan! map-chan]]
            [grub-client.view :as view]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub-client.macros :refer [log go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(def websocket* (atom nil))

(defn connect-to-server []
  (reset! websocket* (js/WebSocket. "ws://localhost:3000/ws")))

(defn get-local-events []
  (let [added-events (view/get-added-events)
        completed-events (view/get-completed-events)]
    (fan-in [added-events completed-events])))

(defn get-remote-events []
  (let [out (chan)]
    (aset @websocket* "onmessage" (fn [event] 
                                    (log "event:" event)
                                    (let [grub-event (cljs.reader/read-string (.-data event))]
                                      (log "Received grub event:" (str grub-event))
                                      (go (>! out grub-event)))))
    out))

(defn send-to-server [event]
  (.send @websocket* event))

(defn handle-added [in]
  (->> in
       (filter-chan #(= (:event %) :create))
       (do-chan! view/add-grub)))

(defn handle-completed [in]
  (->> in
       (filter-chan #(= (:event %) :complete))
       (do-chan! view/complete-grub)))

(defn handle-uncompleted [in]
  (->> in
       (filter-chan #(= (:event %) :uncomplete))
       (do-chan! view/uncomplete-grub)))
  
(defn handle-grub-events []
  (let [local-events (get-local-events)
        [local-events' local-events''] (fan-out local-events 2)
        remote-events (get-remote-events)
        [remote-events' remote-events'' remote-events'''] (fan-out remote-events 3)
        events (fan-in [local-events' remote-events'])]
    (do-chan! send-to-server local-events'')
    (handle-added events)
    (handle-completed remote-events'')
    (handle-uncompleted remote-events''')))

(defn init []
  (view/render-body)
  (connect-to-server)
  (handle-grub-events))

(init)
