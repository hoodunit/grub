(ns grub.websocket
  (:require [grub.db :as db]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(defn add-client! [ws-channel to from]
  (println "Client connected:" (.toString ws-channel))
  (httpkit/on-close ws-channel 
                    (fn [status] 
                      (println "Client disconnected:" (.toString ws-channel) 
                               "with status" status)
                      (a/close! to)
                      (a/close! from)))
  (httpkit/on-receive ws-channel #(a/put! from (read-string %)))
  (a/go-loop []
             (if-let [event (<! to)]
               (do
                 (httpkit/send! ws-channel (str event))
                 (recur))
               (httpkit/close ws-channel))))
