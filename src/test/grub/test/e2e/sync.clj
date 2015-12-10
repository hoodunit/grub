(ns grub.test.e2e.sync
  (:require [grub.db :as db]
            [grub.util :as util]
            [clj-webdriver.taxi :as taxi]
            [clj-webdriver.core :refer [new-driver]]))

(defn set-chromedriver-path! []
  (System/setProperty "webdriver.chrome.driver" "bin/chromedriver"))

(defn start-client [server-url]
  (let [driver (new-driver {:browser :chrome})]
    (taxi/to driver server-url)
    driver))

(defn grub-text [driver elem]
  (let [input (taxi/find-element-under driver elem {:tag :input})]
    (taxi/value driver input)))

(defn grub-completed? [driver elem]
  (let [classes-str (taxi/attribute driver elem "class")
        classes (clojure.string/split classes-str #" ")]
    (not (nil? (first (filter #(= "completed" %) classes))))))

(defn grub-elem-data [driver elem]
  {:text (grub-text driver elem)
   :completed (grub-completed? driver elem)})

(defn get-grub-elems [driver]
  (taxi/elements driver ".grub-item"))

(defn get-grubs [driver]
  (let [elems (get-grub-elems driver)]
    (set (map (partial grub-elem-data driver) elems))))

(defn click-random-grub [driver]
  (let [elems (get-grub-elems driver)
        random-elem (nth elems (rand-int (count elems)))]
    (try (taxi/click driver random-elem)
         (catch Exception e (println "Click failed")))))

(defn add-grub-btn [driver]
  (taxi/element driver "#add-grub-btn"))

(defn add-grub-input [driver]
  (taxi/element driver "#add-grub-input"))

(defn add-grub [driver text]
  (taxi/input-text driver (add-grub-input driver) text)
  (taxi/click driver (add-grub-btn driver)))

(defn make-random-change-on-client [driver]
  (let [add-grub? (> 0.6 (rand))]
    (if add-grub?
      (add-grub driver (util/rand-str 10))
      (click-random-grub driver))))

(defn assert-all-clients-in-sync [clients db-grubs]
  (doseq [client clients]
    (let [client-grubs (get-grubs client)]
      (if (= client-grubs db-grubs)
        (println "Client is in sync")
        (println "Error: client is not in sync" "\nexpected:\n" db-grubs "\n  actual:\n" client-grubs)))))

(defn stop-client [driver]
  (taxi/close driver))

(defn get-db-state [uri]
  (db/get-current-state (db/connect uri)))

(defn get-db-grubs [uri]
  (->> (get-db-state uri)
       (:grubs)
       (vals)
       (map #(dissoc % :id))
       (set)))

(defn make-random-changes-on-clients [clients]
  (dotimes [_ 100]
    (let [client (nth clients (rand-int (count clients)))]
      (make-random-change-on-client client))))

(defn eventual-sync-test [db-uri server-url]
  (let [num-clients 4
        num-changes 100]
    (println "Starting" num-clients "clients")
    (let [clients (repeatedly 4 #(start-client server-url))]
      (println "Making" num-changes "random changes")
      (make-random-changes-on-clients clients)
      (if db-uri
        (do
          (println "Sleeping for a moment")
          (Thread/sleep 2000)
          (println "Verifying clients are in sync")
          (assert-all-clients-in-sync clients (get-db-grubs db-uri))
          (println "Closing clients")
          (doseq [client clients] (stop-client client)))
        (println "Verify clients are in sync")))))

(defn run-e2e-tests [db-uri server-url]
  (set-chromedriver-path!)
  (eventual-sync-test db-uri server-url))

(defn run-manual-e2e-tests [server-url]
  (set-chromedriver-path!)
  (eventual-sync-test nil server-url))
