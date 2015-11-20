(ns grub.websocket
  (:require [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def DEBUG true)

(defn write-msg [msg]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)]
    (t/write writer msg)
    (.toString out)))

(defn read-msg [msg]
  (let [in (ByteArrayInputStream. (.getBytes msg))
        reader (t/reader in :json)
        received (t/read reader)]
    received))

(defn add-connected-client! [ws-channel to from on-close]
  (println "Client connected:" (.toString ws-channel))
  (a/go-loop [] (if-let [event (<! to)]
                  (do (when DEBUG (println "DOWN" event "\n"))
                      (httpkit/send! ws-channel (write-msg event))
                      (recur))
                  (httpkit/close ws-channel)))
  (httpkit/on-receive ws-channel #(let [msg (read-msg %)]
                                   (when DEBUG (println "UP" msg "\n"))
                                   (a/put! from msg)))
  (httpkit/on-close ws-channel (fn [status]
                                 (println "Client disconnected:"
                                          (.toString ws-channel)
                                          "with status" status)
                                 (on-close))))
