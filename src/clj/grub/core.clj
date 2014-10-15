(ns grub.core
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.state :as state]
            [ring.middleware.file :as file]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as resp]
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
    (include-js "/js/react-0.11.2-with-addons.js")
    (include-js "/js/out/goog/base.js")
    (include-js "/js/grub.js")
    [:script {:type "text/javascript"} "goog.require(\"grub.core\")"]]))

(def prod-system
  {:index prod-index-page
   :db-name "grub"
   :db nil
   :db-conn nil
   :port 3000
   :stop-server nil
   :state (atom nil)})

(def dev-system
  {:index dev-index-page
   :db-name "grub-dev"
   :db nil
   :db-conn nil
   :port 3000
   :stop-server nil
   :state (atom nil)})

(defn handle-websocket [handler state]
  (fn [{:keys [websocket?] :as request}]
    (if websocket?
      (httpkit/with-channel request ws-channel
        (let [to-client (chan)
              from-client (chan)]
          (ws/add-connected-client! ws-channel to-client from-client)
          (state/sync-new-client! to-client from-client state)))
      (handler request))))

(defn handle-root [handler index]
  (fn [{:keys [uri] :as request}]
    (if (= uri "/")
      (resp/response index)
      (handler request))))

(defn wrap-bounce-favicon [handler]
  (fn [req]
    (if (= [:get "/favicon.ico"] [(:request-method req) (:uri req)])
      {:status 404
       :headers {}
       :body ""}
      (handler req))))

(defn make-handler [{:keys [index state]}]
  (-> (fn [req] "Not found")
      (file/wrap-file "public")
      (content-type/wrap-content-type)
      (handle-root index)
      (handle-websocket state)
      (wrap-bounce-favicon)))

(defn start [{:keys [port db-name state] :as system}]
  (let [{:keys [db conn]} (db/connect db-name)
        _ (reset! state (db/get-current-state db))
        stop-server (httpkit/run-server (make-handler system) {:port port})]
    (add-watch state :db (fn [_ _ old new] 
                           (db/update-db! db new)))
    (println "Started server on localhost:" port)
    (assoc system 
      :db db
      :db-conn conn
      :stop-server stop-server
      :state state)))

(defn stop [{:keys [db-conn stop-server state] :as system}]
  (remove-watch state :db)
  (stop-server)
  (db/disconnect db-conn)
  (reset! state nil)
  system)

(defn usage [options-summary]
  (->> ["Usage: grub [options] action"
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
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (println "options:" options)
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "development" (start (merge dev-system options))
      "dev"         (start (merge dev-system options))
      "production"  (start (merge prod-system options))
      "prod"        (start (merge prod-system options))
      (exit 1 (usage summary)))))
