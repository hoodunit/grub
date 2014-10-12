(ns grub.core
  (:require [grub.websocket :as ws]
            [grub.db :as db]
            [grub.test.integration.core :as integration-test]
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

(def prod-config
  {:index prod-index-page
   :database-name "grub"})

(def dev-config
  {:index dev-index-page
   :database-name "grub-dev"})

(defn handle-websocket [handler]
  (fn [{:keys [websocket?] :as request}]
    (if websocket?
      (httpkit/with-channel request ws-channel
        (let [to-client (chan)
              from-client (chan)]
          (ws/add-connected-client! ws-channel to-client from-client)
          (state/sync-new-client! to-client from-client)))
      (handler request))))

(defn handle-root [handler index]
  (fn [{:keys [uri] :as request}]
    (if (= uri "/")
      (resp/response index)
      (handler request))))

(defn make-handler [{:keys [index]}]
  (-> (fn [req] "Not found")
      (file/wrap-file "public")
      (content-type/wrap-content-type)
      (handle-root index)
      (handle-websocket)))

(defn start [{:keys [port database-name] :as config}]
  (let [to-db (chan)
        db (db/connect-and-handle-events to-db database-name)]
    (state/init-server to-db (db/get-current-state db))
    (println "Starting server on localhost:" port)
    (httpkit/run-server (make-handler config) {:port port})))

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
      "development" (start (merge dev-config options))
      "dev"         (start (merge dev-config options))
      "production"  (start (merge prod-config options))
      "prod"        (start (merge prod-config options))
      (exit 1 (usage summary)))))
