(ns grub.core
  (:gen-class)
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.state :as state]
            [grub.sync :as sync]
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
    (hiccup/include-js "./js/react-0.11.2-with-addons.js")
    (hiccup/include-js "./js/out/goog/base.js")
    (hiccup/include-js "./js/grub.js")
    [:script {:type "text/javascript"} "goog.require(\"grub.core\")"]]))

(def prod-system
  {:index prod-index-page
   :database-uri "datomic:mem://grub"
   :db-conn nil
   :port 3000
   :stop-server nil
   :states (atom nil)})

(def dev-system
  {:index dev-index-page
   :database-uri "datomic:mem://grub"
   :db-conn nil
   :port 3000
   :stop-server nil
   :states (atom nil)})

(defn handle-websocket [handler states new-states-pub]
  (fn [{:keys [websocket?] :as request}]
    (if websocket?
      (httpkit/with-channel request ws-channel
        (let [to-client (chan)
              from-client (chan)
              new-states (chan (a/sliding-buffer 1))
              on-close (fn []
                         (a/unsub new-states-pub :new-state new-states)
                         (a/close! new-states)
                         (a/close! from-client)
                         (a/close! to-client))]
          (a/sub new-states-pub :new-state new-states)
          (ws/add-connected-client! ws-channel to-client from-client on-close)
          (sync/make-server-agent to-client from-client new-states states)))
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

(defn make-handler [{:keys [index states]} new-states-pub]
  (-> (fn [req] (resp/not-found "Not found"))
      (resource/wrap-resource "public")
      (content-type/wrap-content-type)
      (handle-root index)
      (handle-websocket states new-states-pub)
      (wrap-bounce-favicon)))

(defn start [{:keys [port database-uri states] :as system}]
  (let [db-conn (db/connect database-uri)
        new-states (chan)
        new-states-pub (a/pub new-states (fn [_] :new-state))
        db-state (db/get-current-state db-conn)
        _ (reset! states (state/new-states (if db-state db-state state/empty-state)))
        stop-server (httpkit/run-server (make-handler system new-states-pub) {:port port})]
    (add-watch states :db (fn [_ _ old new] 
                            (when-not (= old new)
                              (let [new-state (state/get-latest new)]
                                (a/put! new-states new-state)
                                (db/update-db! db-conn new-state)))))
    (println "Started server on localhost:" port)
    (assoc system 
      :db-conn db-conn
      :stop-server stop-server
      :states states)))

(defn stop [{:keys [db-conn stop-server states] :as system}]
  (remove-watch states :db)
  (stop-server)
  (db/disconnect db-conn)
  (reset! states nil)
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
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (println "options:" options)
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 2) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    (case (first arguments)
      "development" (start (merge dev-system options))
      "dev"         (start (merge dev-system options))
      "production"  (start (merge prod-system options))
      "prod"        (start (merge prod-system options))
      (exit 1 (usage summary)))))
