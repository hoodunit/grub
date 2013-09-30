(ns grub.core
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.integration-test :as integration-test]
            [ring.middleware.reload :as reload]
            [ring.middleware.file :as file]
            [ring.util.response :as resp]
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
    (include-js "/js/jquery.js")
    (include-js "/js/bootstrap.js")
    (include-js @js-file)]))

(defroutes routes
  (GET "/ws" [] ws/websocket-handler)
  (GET "/" [] (index-page))
  (GET "*/src/cljs/grub/:file" [file] (resp/file-response file {:root "src/cljs/grub"}))
  (GET "/js/public/js/:file" [file] (resp/redirect (str "/js/" file)))
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

(defn start-production-server []
  (reset! js-file "/js/grub.js")
  (let [db-chan (db/connect-production-database)]
    (ws/pass-received-events-to-clients-and-db db-chan)
    (start-server default-port)))

(defn start-development-server []
  (let [db-chan (db/connect-development-database)]
    (ws/pass-received-events-to-clients-and-db db-chan)
    (start-server default-port)))

(defn -main [& args]
  (cond
   (some #(= % "integration") args) (run-integration-test)
   (some #(= % "production") args) (start-production-server)
   :else (start-development-server)))
