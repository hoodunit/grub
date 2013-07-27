(ns grub.core
  (:use [org.httpkit.server 
         :only [run-server with-channel on-receive send! websocket?]]
        [compojure.handler :only [site]]
        [compojure.core :only [defroutes GET POST]])
  (:require [ring.middleware.reload :as reload]
            [compojure.route :as route]
            [hiccup
             [page :refer [html5]]
             [page :refer [include-js include-css]]]))

(defn async-handler [request]
  (if-not (:websocket? request)
    {:status 200 :body "WebSocket server"}
    (with-channel request channel
      (on-receive channel (fn [data]
                            (send! channel data)))
      (send! channel {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body    "Long polling?"}))))
(defn index-page []
  (html5
   [:head
    [:title "Grub"]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    (include-css "/css/bootstrap.css")]
    [:body
     (include-js "http://code.jquery.com/jquery.js")
     (include-js "/js/bootstrap.js")
     (include-js "/js/main.js")]))

(defroutes routes
  (GET "/ws" [] async-handler)
  (GET "/" [] (index-page))
  (route/files "/")
  (route/not-found "<p>Page not found.</p>"))

(def app 
  (let [dev? true]
    (if dev?
      (reload/wrap-reload (site #'routes))
      (site routes))))

(defn -main [& args]
    (run-server app {:port 8080}))
