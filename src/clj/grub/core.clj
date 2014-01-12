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
             [page :refer [include-js include-css]]]
            [clojure.tools.cli :refer [parse-opts]]))

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
  (httpkit/run-server app {:port port}))

(defn run-integration-test []
  (let [stop-server (start-server integration-test-port)]
    (integration-test/run integration-test-port)
    (stop-server)))

(defn start-production-server [{:keys [port mongo-url]}]
  (reset! js-file "/js/grub.js")
  (let [db-chan (db/connect-production-database mongo-url)]
    (ws/pass-received-events-to-clients-and-db db-chan)
    (println (str "Starting production server on localhost:" port))
    (start-server port)))

(defn start-development-server [{:keys [port]}]
  (let [db-chan (db/connect-development-database)]
    (ws/pass-received-events-to-clients-and-db db-chan)
    (println (str "Starting development server on localhost:" port))
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
  ;; An option with a required argument
  [["-p" "--port PORT" "Port number"
    :default default-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-m" "--mongo-url URL"
    :default (System/getenv "MONGOHQ_URL")]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (println "options:" options)
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "development" (start-development-server options)
      "dev"         (start-development-server options)
      "production"  (start-production-server options)
      "prod"        (start-production-server options)
      "integration" (run-integration-test)
      (exit 1 (usage summary)))))
