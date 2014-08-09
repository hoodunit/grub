(ns grub.test.integration.core
  (:require [grub.db :as db]
            [grub.websocket :as ws]
            [grub.state :as state]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [clj-webdriver.taxi :as taxi]
            [clj-webdriver.core :as webdriver]
            [clojure.test :as test]))

(def server-port 3456)
(def site-url (str "http://localhost:" server-port))

;; Hard-coded path to chromedriver
(defn set-chromedriver-path! []
  (System/setProperty "webdriver.chrome.driver" "bin/chromedriver"))

(defn get-driver [url]
  (webdriver/start {:browser :chrome} url))

(defn get-rand-grub []
  (str "testgrub" (rand-int 10000)))

(defn add-grub [driver grub-text]
  (taxi/input-text driver "#add-grub-input" grub-text)
  (taxi/click driver "#add-grub-btn"))

(defn test-grubs-saved-to-server [url driver]
  (taxi/to driver url)
  (let [grubs (repeatedly 4 get-rand-grub)]
    (doseq [grub grubs]
      (add-grub driver grub))
    (Thread/sleep 200)
    (taxi/refresh driver)
    (Thread/sleep 200)
    (doseq [grub grubs]
      (test/is (taxi/find-element driver {:value grub})
               "Previously added grubs should be loaded on refresh")))
  (db/clear-grubs))

(defn test-added-grubs-sync [url driver1 driver2]
  (taxi/to driver1 url)
  (taxi/to driver2 url)
  (let [grubs (repeatedly 4 get-rand-grub)]
    (doseq [grub grubs]
      (add-grub driver1 grub))
    (doseq [grub grubs]
      (test/is (taxi/find-element driver2 {:value grub})
               "Added grubs should appear in other browser"))))

(defn get-rand-recipe []
  {:name (str "recipe" (rand-int 10000))
   :grubs "grubs\nstuff\nmorestuff"})

(defn add-recipe [driver {:keys [name grubs]}]
  (taxi/click driver "#new-recipe-name")
  (taxi/input-text driver "#new-recipe-name" name)
  (taxi/input-text driver "#new-recipe-grubs" grubs)
  (taxi/click driver "#save-recipe-btn"))
 
(defn test-added-recipes-sync [url driver1 driver2]
  (taxi/to driver1 url)
  (taxi/to driver2 url)
  (let [recipes (repeatedly 4 get-rand-recipe )]
    (doseq [recipe recipes]
      (add-recipe driver1 recipe))
    (doseq [{:keys [name]} recipes]
      (test/is (taxi/find-element driver2 {:value name})
               "Added recipes should appear in other browser"))))

(defn run-tests [site-url driver1 driver2]
  (test-grubs-saved-to-server site-url driver1)
  (test-added-grubs-sync site-url driver1 driver2)
  (test-added-recipes-sync site-url driver1 driver2))

(defn start-db-and-websocket-server! []
  (let [to-db (chan)]
    (db/connect-and-handle-events to-db "grub-integration-test")
    (state/init to-db (db/get-current-grubs) (db/get-current-recipes))))

(defn run []
  (println "Starting integration test")
  (set-chromedriver-path!)
  (start-db-and-websocket-server!)
  (let [driver1 (get-driver site-url)
        driver2 (get-driver site-url)]
    (run-tests site-url driver1 driver2)
    (taxi/quit driver1)
    (taxi/quit driver2))
  (db/clear-all))
