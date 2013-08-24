(ns grub.core
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.integration-test :as integration-test]
            [ring.middleware.reload :as reload]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [org.httpkit.server :as httpkit]
            [hiccup
             [page :refer [html5]]
             [page :refer [include-js include-css]]]))

(def js-file (atom "/js/grub_dev.js"))

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
    (include-js @js-file)]))

(defroutes routes
  (GET "/ws" [] ws/websocket-handler)
  (GET "/" [] (index-page))
  (route/files "/")
  (route/not-found "<p>Page not found.</p>"))

(def app 
  (let [dev? true]
    (if dev?
      (reload/wrap-reload (handler/site #'routes) {:dirs ["src/clj"]})
      (handler/site routes))))

(def default-port 3000)
(def integration-test-port 3456)

(defn start-server [port]
  (println (str "Starting server on localhost:" port))
  (httpkit/run-server app {:port port}))

(defn run-integration-test []
  (let [stop-server (start-server integration-test-port)]
    (integration-test/run integration-test-port)
    (stop-server)))

(defn -main [& args]
  (cond
   (some #(= % "integration") args) (run-integration-test)
   (some #(= % "production") args) (do (reset! js-file "/js/grub.js")
                                       (start-server default-port))
   :else (do (db/connect-and-handle-events "grub-dev")
             (start-server default-port))))
