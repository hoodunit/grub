(ns grub.core
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [ring.middleware.reload :as reload]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [org.httpkit.server :as httpkit]
            [hiccup
             [page :refer [html5]]
             [page :refer [include-js include-css]]]))

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
  (GET "/ws" [] ws/websocket-handler)
  (GET "/" [] (index-page))
  (route/files "/")
  (route/not-found "<p>Page not found.</p>"))


(def app 
  (let [dev? true]
    (if dev?
      (reload/wrap-reload (handler/site #'routes) {:dirs ["src-clj"]})
      (handler/site routes))))

(defn -main [& args]
  (db/connect-and-handle-events)
  (httpkit/run-server app {:port 3000}))
