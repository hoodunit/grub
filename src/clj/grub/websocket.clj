(ns grub.websocket
  (:require [grub.db :as db]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def incoming-events (chan))

(def connected-clients (atom []))

(def ws-channel-id-count (atom 0))

(defn get-unique-ws-id []
  (swap! ws-channel-id-count inc))

(defn add-connected-client! [ws-channel]
  (let [ws-channel-id (get-unique-ws-id)
        client-chan (chan)]
    (swap! connected-clients 
           conj 
           {:id ws-channel-id :channel client-chan})
    [ws-channel-id client-chan]))


(defn add-event-to-incoming-channel [raw-event ws-channel-id]
  (let [parsed-event (read-string raw-event)
        event (assoc parsed-event :ws-channel ws-channel-id)]
    (println "Received event" event)
    (go (>! incoming-events event))))

(defn forward-client-events-to-others [ws-channel ws-channel-id]
  (httpkit/on-receive ws-channel 
                      #(add-event-to-incoming-channel % ws-channel-id)))

(defn forward-other-events-to-client [c ws-channel]
  (a/go-loop [] (let [event (<! c)
                      event-str (str event)]
                  (println "Send to client '" event-str "'")
                  (httpkit/send! ws-channel event-str))
             (recur)))


(defn send-current-grubs-and-recipes-to-client [client-chan]
  (a/pipe (db/get-current-grubs-as-events) client-chan false)
  (a/pipe (db/get-current-recipes-as-events) client-chan false))

(defn setup-new-connection [ws-channel]
  (let [[ws-channel-id client-chan] (add-connected-client! ws-channel)]
    (println "Channel connected:" (.toString ws-channel))
    (forward-client-events-to-others ws-channel ws-channel-id)
    (forward-other-events-to-client client-chan ws-channel)
    (send-current-grubs-and-recipes-to-client client-chan)))

(defn websocket-handler [request]
  (httpkit/with-channel request ws-channel (setup-new-connection ws-channel)))

(defn get-other-clients [my-ws-channel-id]
  (filter #(not (= (:id %) my-ws-channel-id))
          @connected-clients))

(defn push-event-to-others [orig-event]
  (let [my-ws-channel-id (:ws-channel orig-event)
        event (dissoc orig-event :ws-channel)]
    (go (doseq [{ch :channel} (get-other-clients my-ws-channel-id)]
            (>! ch event)))))

(defn pass-received-events-to-clients-and-db [db-chan]
  (let [in' (a/mult incoming-events)
        to-others (chan)
        to-database (chan)]
    (a/tap in' to-others)
    (a/tap in' to-database)
    (a/go-loop [] (let [event (<! to-others)]
                    (push-event-to-others event)
                    (recur)))
    (a/pipe to-database (a/map> #(dissoc % :ws-channel) db-chan))))
