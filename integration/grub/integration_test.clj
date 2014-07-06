(ns grub.integration-test
  (:require [grub.db :as db]
            [grub.websocket :as ws]
            [clj-webdriver.taxi :as taxi]
            [clj-webdriver.core :as webdriver]
            [clojure.test :as test]))

;; Hard-coded path to chromedriver
(defn set-chromedriver-path! []
  (System/setProperty "webdriver.chrome.driver" "bin/chromedriver"))

(defn add-grub [driver grub-text]
  (taxi/input-text driver "#add-grub-input" grub-text)
  (taxi/click driver {:text "Add"}))

(defn get-driver [url]
  (webdriver/start {:browser :chrome} url))

(defn get-rand-grub []
  (str "testgrub" (rand-int 10000)))

(defn test-adding-synced-grubs [url driver1 driver2]
  (taxi/to driver1 url)
  (taxi/to driver2 url)
  (let [grubs (repeatedly 4 get-rand-grub)]
    (doseq [grub grubs]
      (add-grub driver1 grub))
    (doseq [grub grubs]
      (test/is (taxi/find-element driver2 {:text grub})
               "Added grubs should appear in other browser")))
  (db/clear-grubs))

(defn test-grubs-are-stored-on-server [url driver]
  (taxi/to driver url)
  (let [grubs (repeatedly 4 get-rand-grub)]
    (doseq [grub grubs]
      (add-grub driver grub))
    (Thread/sleep 200)
    (taxi/refresh driver)
    (Thread/sleep 200)
    (doseq [grub grubs]
      (test/is (taxi/find-element driver {:text grub})
               "Previously added grubs should be loaded on refresh")))
  (db/clear-grubs))
  

(defn run [port]
  (set-chromedriver-path!)
  (let [db-chan (db/connect-and-handle-events "grub-integration-test")
        site-url (str "http://localhost:" port)]
    (println "Starting integration test")
    (ws/pass-received-events-to-clients-and-db db-chan)
    (let [driver1 (get-driver site-url)
          driver2 (get-driver site-url)]
      (test-grubs-are-stored-on-server site-url driver1)
      (test-adding-synced-grubs site-url driver1 driver2)
      (taxi/quit driver1)
      (taxi/quit driver2)))
  (db/clear-grubs))
