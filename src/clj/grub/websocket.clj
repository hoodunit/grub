(ns grub.websocket
  (:require [grub.db :as db]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn disconnected [status ws-channel to from]
  (println "Client disconnected:" (.toString ws-channel) "with status" status)
  (a/close! to)
  (a/close! from))

(defn write-msg [msg]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)]
    (t/write writer msg)
    (.toString out)))

(defn read-msg [msg]
  (let [in (ByteArrayInputStream. (.getBytes msg))
        reader (t/reader in :json)]
    (t/read reader)))

(defn add-connected-client! [ws-channel to from]
  (println "Client connected:" (.toString ws-channel))
  (a/go-loop [] (if-let [event (<! to)] 
                  (do (httpkit/send! ws-channel (write-msg event)) 
                      (recur))
                  (httpkit/close ws-channel)))
  (httpkit/on-receive ws-channel #(a/put! from (read-msg %)))
  (httpkit/on-close ws-channel #(disconnected % ws-channel to from)))
