(ns grub.core
  (:use [org.httpkit.server :only [run-server]])
  (:require [ring.middleware.reload :as reload]
            [compojure.handler :only [site]]
            [compojure.core :only [defroutes GET POST]]))

(defroutes routes
  (GET "/" [] "handling-page")
  (GET "/save" [] handler)     ;; websocket
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args] ;; entry point, lein run will pick up and start from here
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'routes)) ;; only reload when dev
                  (site routes))]
    (run-server handler {:port 8080})))

(comment
(defn async-handler [ring-request]
  (with-channel ring-request channel
    (if (websocket? channel)
      (println "WebSocket channel")
      (println "HTTP channel"))
    (on-receive channel (fn [data]
                          (send! channel data)))
    (send! channel {:status 200
                    :headers {"Content-Type" "text/plain"}
                    :body    "Long polling?"})))

(run-server async-handler {:port 8080})
)
