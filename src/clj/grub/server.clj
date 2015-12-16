(ns grub.server
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.server-sync :as sync]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.util.response :as resp]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hiccup.page :as hiccup]
            [grub.util :as util]))

(def prod-index-page
  (hiccup/html5
    [:head
     [:title "Grub"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     (hiccup/include-css "./css/bootstrap.min.css")
     (hiccup/include-css "./css/styles.css")]
    [:body
     [:div#container]
     (hiccup/include-js "./js/grub.min.js")]))

(def dev-index-page
  (hiccup/html5
    [:head
     [:title "Grub"]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     (hiccup/include-css "./css/bootstrap.css")
     (hiccup/include-css "./css/styles.css")]
    [:body
     [:div#container]
     (hiccup/include-js "./js/out/goog/base.js")
     (hiccup/include-js "./js/grub.js")
     [:script {:type "text/javascript"} "goog.require(\"grub.core\")"]]))

(def prod-system
  {:index prod-index-page
   :database-uri (System/getenv "GRUB_DATABASE_URI")
   :db-conn nil
   :port 3000
   :stop-server nil})

(def dev-system
  {:index        dev-index-page
   :database-uri (or (System/getenv "GRUB_DATABASE_URI") "datomic:mem://grub")
   :db-conn      nil
   :port         3000
   :stop-server  nil})

(defn sync-client-with-db! [ws-channel list-name db-conn db-reports]
  (let [from-client (chan)
        to-client (chan)
        diffs (chan)
        full-sync-reqs (chan)
        {:keys [report-queue tap]} (db/report-queue-subscribe db-reports)
        on-close (fn []
                   (db/report-queue-unsubscribe db-reports tap)
                   (a/close! from-client)
                   (a/close! to-client)
                   (a/close! diffs)
                   (a/close! full-sync-reqs)
                   )]
    (ws/add-connected-client! ws-channel to-client from-client on-close)
    (db/create-list-unless-exists db-conn list-name)
    (sync/start-sync! list-name to-client diffs full-sync-reqs db-conn report-queue)
    (go (loop [] (let [event (<! from-client)]
                   (cond
                     (nil? event) nil                       ;; drop out of loop
                     (= (:type event) :diff) (do (>! diffs event) (recur))
                     (= (:type event) :full-sync-request) (do (>! full-sync-reqs event) (recur))
                     :else (do (println "Unknown event:" event) (recur))))))))

(defn handle-websocket [handler db-conn db-reports]
  (fn [{:keys [websocket? uri] :as request}]
    (if websocket?
      (let [list-name (last (clojure.string/split uri #"/"))]
        (httpkit/with-channel request ws-channel (sync-client-with-db! ws-channel list-name db-conn db-reports)))
      (handler request))))

(defn random-list-name []
  (util/rand-str 10))

(defn redirect-grub-list [{:keys [cookies]}]
  (let [last-list (:value (get cookies "list"))]
    (or last-list (random-list-name))))

(defn handle-routes [handler index]
  (fn [{:keys [uri] :as request}]
    (cond
      (= uri "/") (resp/redirect (str "/" (redirect-grub-list request)))
      (re-matches #"/[\w]+" uri) (resp/response index)
      :else (handler request))))

(defn wrap-bounce-favicon [handler]
  (fn [req]
    (if (= [:get "/favicon.ico"] [(:request-method req) (:uri req)])
      (resp/not-found "")
      (handler req))))

(defn make-handler [{:keys [index]} db-conn db-reports]
  (-> (fn [req] (resp/not-found "Not found"))
      (resource/wrap-resource "public")
      (content-type/wrap-content-type)
      (handle-routes index)
      (handle-websocket db-conn db-reports)
      (wrap-cookies)
      (wrap-bounce-favicon)))

(defn start [{:keys [port database-uri] :as system}]
  (let [db-conn (db/connect database-uri)
        db-reports (db/report-queue-channel db-conn)
        stop-server (httpkit/run-server (make-handler system db-conn db-reports) {:port port})]
    (println "Started server on localhost:" port)
    (assoc system
      :db-conn db-conn
      :db-reports db-reports
      :stop-server stop-server)))

(defn stop [{:keys [db-conn stop-server] :as system}]
  (stop-server)
  (db/disconnect db-conn)
  system)

(defn start-dev [system]
  (atom (start system)))

(defn restart-dev [system-atom]
  (swap! system-atom stop)
  (swap! system-atom start))

