(ns grub.core
  (:gen-class)
  (:require [grub.server :as server]
            [grub.test.e2e.sync :as e2e]
            [clojure.tools.cli :as cli]))

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
      "development" (server/start (merge server/dev-system options))
      "dev"         (server/start (merge server/dev-system options))
      "e2e"         (let [system (server/start (merge server/dev-system options))]
                      (e2e/run-e2e-tests system)
                      (server/stop system))
      "production"  (server/start (merge server/prod-system options))
      "prod"        (server/start (merge server/prod-system options))
      (exit 1 (usage summary)))))
