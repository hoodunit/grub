(ns grub.websocket
  (:require [grub.async :refer [go-loop fan-out do-chan! copy-chan]]
            [grub.db :as db]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :refer [<! >! chan go]]))

(def incoming-events (chan))

(defn get-incoming-events []
  incoming-events)

(def ws-channels (atom []))
(def ws-channel-id-count (atom 0))

(defn push-event-to-others [orig-event]
  (let [my-ws-channel-id (:ws-channel orig-event)
        other-channels (fn [] (filter #(not (= (:id %) my-ws-channel-id)) @ws-channels))
        event (dissoc orig-event :ws-channel)]
    (go (doseq [{ch :channel} (other-channels)]
            (>! ch event)))))

(defn push-current-grubs-to-client [c ws-channel]
  (copy-chan c (db/get-current-grubs-as-events)))

(defn push-current-recipes-to-client [c ws-channel]
  (copy-chan c (db/get-current-recipes-as-events)))

(defn push-received-events-to-client [c ws-channel]
  (go-loop (let [event (<! c)
                 event-str (str event)]
             (println "Send to client" event-str)
             (httpkit/send! ws-channel event-str))))

(defn add-incoming-event [raw-event ws-channel-id]
  (let [parsed-event (read-string raw-event)
        event (assoc parsed-event :ws-channel ws-channel-id)]
    (println "Received event" event)
    (go (>! (get-incoming-events) event))))

(defn handle-incoming-events []
  (let [[incoming incoming'] (fan-out incoming-events 2)]
    (do-chan! push-event-to-others incoming)
    (go-loop (let [event (<! incoming')
                   parsed-event (dissoc event :ws-channel)]
               (>! @db/incoming-events event)))))

(defn websocket-handler [request]
  (httpkit/with-channel request ws-channel
    (let [ws-channel-id (swap! ws-channel-id-count inc)
          c (chan)]
      (swap! ws-channels conj {:id ws-channel-id :channel c})
      (println "Channel connected:" (.toString ws-channel))
      (println "Request:" request)
      (httpkit/on-receive ws-channel #(add-incoming-event % ws-channel-id))
      (push-current-grubs-to-client c ws-channel)
      (push-current-recipes-to-client c ws-channel)
      (push-received-events-to-client c ws-channel))))
      
(handle-incoming-events)
