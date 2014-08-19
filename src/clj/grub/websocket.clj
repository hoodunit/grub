(ns grub.websocket
  (:require [grub.db :as db]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(defn disconnected [status ws-channel to from]
  (println "Client disconnected:" (.toString ws-channel) "with status" status)
  (a/close! to)
  (a/close! from))

(defn add-connected-client! [ws-channel to from]
  (println "Client connected:" (.toString ws-channel))
  (a/go-loop [] (if-let [event (<! to)] 
                  (do (httpkit/send! ws-channel (pr-str event)) 
                      (recur))
                  (httpkit/close ws-channel)))
  (httpkit/on-receive ws-channel #(a/put! from (read-string %)))
  (httpkit/on-close ws-channel #(disconnected % ws-channel to from)))
