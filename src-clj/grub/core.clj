(ns grub.core
  (:require [ring.middleware.reload :as reload]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [org.httpkit.server :as httpkit]
            [hiccup
             [page :refer [html5]]
             [page :refer [include-js include-css]]]
            [clojure.core.async :as async :refer [<! >! >!! chan go close! timeout]]))

(def out-channels (atom []))
(def channel-id-count (atom 0))

(defn push-grub-to-others [grub my-channel-id]
  (let [other-channels (fn [] (filter #(not (= (:id %) my-channel-id)) @out-channels))]
    (go (doseq [{ch :channel} (other-channels)]
            (>! ch grub)))))

(defn push-new-grubs-to-client [c ws-channel]
  (go (while true
        (let [grub (<! c)]
          (httpkit/send! ws-channel grub)))))

(defn websocket-handler [request]
  (httpkit/with-channel request ws-channel
    (let [channel-id (swap! channel-id-count inc)
          c (chan)]
      (swap! out-channels conj {:id channel-id :channel c})
      (println "channel connected:" (.toString ws-channel))
      (httpkit/on-receive ws-channel #(push-grub-to-others % channel-id))
      (push-new-grubs-to-client c ws-channel))))
      
(defn index-page []
  (html5
   [:head
    [:title "Grub"]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    (include-css "/css/bootstrap.css")
    (include-css "/css/styles.css")]
    [:body
     (include-js "http://code.jquery.com/jquery.js")
     (include-js "/js/bootstrap.js")
     (include-js "/js/main.js")]))

(defroutes routes
  (GET "/ws" [] websocket-handler)
  (GET "/" [] (index-page))
  (route/files "/")
  (route/not-found "<p>Page not found.</p>"))

(def app 
  (let [dev? true]
    (if dev?
      (reload/wrap-reload (handler/site #'routes))
      (handler/site routes))))

(defn -main [& args]
    (httpkit/run-server app {:port 3000}))
