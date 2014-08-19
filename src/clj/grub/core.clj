(ns grub.core
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.test.integration.core :as integration-test]
            [grub.state :as state]
            [ring.middleware.file :as file]
            [ring.util.response :as resp]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [org.httpkit.server :as httpkit]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hiccup
             [page :refer [html5]]
             [page :refer [include-js include-css]]]
            [clojure.tools.cli :refer [parse-opts]]))

(def prod-index-page
  (html5
   [:head
    [:title "Grub"]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    (include-css "/css/bootstrap.min.css")
    (include-css "/css/styles.css")]
   [:body
    [:div#container]
    (include-js "/js/grub.min.js")]))

(def dev-index-page
  (html5
   [:head
    [:title "Grub"]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    (include-css "/css/bootstrap.css")
    (include-css "/css/styles.css")]
   [:body
    [:div#container]
    (include-js "/js/react-0.11.1.js")
    (include-js "/js/out/goog/base.js")
    (include-js "/js/grub.js")
    [:script {:type "text/javascript"} "goog.require(\"grub.core\")"]]))

(def index-page (atom dev-index-page))

(defn websocket-handler [request]
  (when (:websocket? request)
    (httpkit/with-channel request ws-channel
      (let [to-client (chan)
            from-client (chan)]
        (ws/add-connected-client! ws-channel to-client from-client)
        (state/sync-new-client! to-client from-client)))))

(defroutes routes
  (GET "/" [] websocket-handler)
  (GET "/" [] @index-page)
  (GET "*/src/cljs/grub/:file" [file] (resp/file-response file {:root "src/cljs/grub"}))
  (GET "/js/public/js/:file" [file] (resp/redirect (str "/js/" file)))
  (route/files "/")
  (route/not-found "<p>Page not found.</p>"))

(def default-port 3000)

(defn start-server [port]
  (httpkit/run-server (handler/site routes) {:port port}))

(defn run-integration-test []
  (let [stop-server (start-server integration-test/server-port)]
    (println "Starting integration test server on localhost:" integration-test/server-port)
    (integration-test/run)
    (stop-server)))

(defn start-production-server [{:keys [port mongo-url]}]
  (reset! index-page prod-index-page)
  (let [to-db (chan)]
    (db/connect-production-database to-db mongo-url)
    (state/init to-db (db/get-current-grubs) (db/get-current-recipes))
    (println "Starting production server on localhost:" port)
    (start-server port)))

(defn start-development-server [{:keys [port]}]
  (let [to-db (chan)]
    (db/connect-development-database to-db)
    (state/init to-db (db/get-current-grubs) (db/get-current-recipes))
    (println "Starting development server on localhost:" port)
    (start-server port)))

(defn usage [options-summary]
  (->> ["Usage: grub [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  dev[elopment]  Start development server"
        "  prod[uction]   Start production server"
        "  integration    Run integration tests"]
       (clojure.string/join \newline)))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default default-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-m" "--mongo-url URL"
    :default (System/getenv "MONGOHQ_URL")]
   ["-h" "--help"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (println "options:" options)
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "development" (start-development-server options)
      "dev"         (start-development-server options)
      "production"  (start-production-server options)
      "prod"        (start-production-server options)
      "integration" (run-integration-test)
      (exit 1 (usage summary)))))
