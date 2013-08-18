(ns grub.integration-test
  (:require [clj-webdriver.taxi :as taxi]
            [clj-webdriver.core :as webdriver]
            [clojure.test :as test]))

(defn add-grub [driver grub-text]
  (taxi/input-text driver "#add-grub-input" grub-text)
  (taxi/click driver {:text "Add"}))

(defn get-driver []
  (webdriver/start {:browser :chrome} "http://localhost:3000"))

(defn get-rand-grub []
  (str "testgrub" (rand-int 10000)))

(defn test-adding-grubs [driver1 driver2]
  (let [grubs (repeatedly 4 get-rand-grub)]
    (doseq [grub grubs]
      (add-grub driver1 grub))
    (doseq [grub grubs]
      (test/is (taxi/find-element driver2 {:text grub})
               "Added grubs should appear in other browser"))))

(defn run []
  (println "Starting integration test")
  (let [driver1 (get-driver)
        driver2 (get-driver)]
    (test-adding-grubs driver1 driver2)
    (taxi/quit driver1)
    (taxi/quit driver2)))
