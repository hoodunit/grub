(ns grub-client.core
  (:require [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [grub-client.async-utils 
             :refer [fan-in fan-out event-chan filter-chan do-chan!]]
            [grub-client.view :as view])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [grub-client.macros :refer [log go-loop]]))

(def websocket* (atom nil))

(defn connect-to-server []
  (reset! websocket* (js/WebSocket. "ws://localhost:3000/ws")))

(defn get-local-added-grubs []
  (let [grubs (fan-in [(view/get-grubs-from-clicks) (view/get-grubs-from-enter)])]
    (filter-chan #(not (empty? %)) grubs)))

(defn push-grubs-to-server [in]
  (do-chan! #(.send @websocket* %) in))

(defn get-remote-added-grubs []
  (let [out (chan)]
    (aset @websocket* "onmessage" (fn [event] 
                                    (let [grub (.-data event)]
                                      (log "Received grub:" grub)
                                      (go (>! out grub)))))
    out))

(defn add-new-grubs-as-they-come []
  (let [added-local (get-local-added-grubs)
        [added-local' added-local''] (fan-out added-local 2)
        added-remote (get-remote-added-grubs)
        added (fan-in [added-local' added-remote])]
    (do-chan! view/append-new-grub added)
    (push-grubs-to-server added-local'')))

(defn init []
  (view/render-body)
  (connect-to-server)
  (add-new-grubs-as-they-come))

(init)
