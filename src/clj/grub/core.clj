(ns grub.core
  (:gen-class)
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.server-sync :as sync]
            [grub.test.e2e.sync :as e2e]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as resp]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hiccup.page :as hiccup]
            [clojure.tools.cli :as cli]))

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

(defn sync-client-with-db! [ws-channel db-conn db-reports]
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
    (sync/start-sync! to-client diffs full-sync-reqs db-conn report-queue)
    (go (loop [] (let [event (<! from-client)]
                   (cond
                     (nil? event) nil                       ;; drop out of loop
                     (= (:type event) :diff) (do (>! diffs event) (recur))
                     (= (:type event) :full-sync-request) (do (>! full-sync-reqs event) (recur))
                     :else (do (println "Unknown event:" event) (recur))))))))

(defn handle-websocket [handler db-conn db-reports]
  (fn [{:keys [websocket?] :as request}]
    (if websocket?
      (httpkit/with-channel request ws-channel (sync-client-with-db! ws-channel db-conn db-reports))
      (handler request))))

(defn handle-root [handler index]
  (fn [{:keys [uri] :as request}]
    (if (= uri "/")
      (resp/response index)
      (handler request))))

(defn wrap-bounce-favicon [handler]
  (fn [req]
    (if (= [:get "/favicon.ico"] [(:request-method req) (:uri req)])
      (resp/not-found "")
      (handler req))))

(defn make-handler [{:keys [index]} db-conn db-reports]
  (-> (fn [req] (resp/not-found "Not found"))
      (resource/wrap-resource "public")
      (content-type/wrap-content-type)
      (handle-root index)
      (handle-websocket db-conn db-reports)
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

(defn usage [options-summary]
  (->> ["Usage: <executable> [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  dev[elopment]  Start development server"
        "  prod[uction]   Start production server"]
       (clojure.string/join \newline)))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 3000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--help"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (println "options:" options)
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "development" (start (merge dev-system options))
      "dev"         (start (merge dev-system options))
      "e2e"         (let [system (start (merge dev-system options))]
                      (e2e/run-e2e-tests system)
                      (stop system))
      "production"  (start (merge prod-system options))
      "prod"        (start (merge prod-system options))
      (exit 1 (usage summary)))))
