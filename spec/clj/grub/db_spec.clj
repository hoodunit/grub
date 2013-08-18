(ns grub.db-spec
  (:require [speclj.core :refer :all]
            [grub.db :as db]
            [monger.collection :as mc]
            [clojure.core.async :refer [>!! <!! timeout]])) 

(def test-db "grub-test")

(defn short-delay []
  (<!! (timeout 20)))

(defn get-test-grub []
  {:_id (str "grub" (rand-int 10000))
   :grub (str "testtext" (rand-int 1000))
   :completed ([true false] (rand-int 1))})

(describe 
 "grub.db"
 (before-all (db/connect-and-handle-events test-db))
 (before (db/clear-grubs))
 (describe "Add" 
           (it "should add a grub when an add event comes"
               (let [test-grub "testgrub"
                     test-id 12345]
                 (>!! @db/incoming-events {:event :add 
                                           :_id test-id 
                                           :grub test-grub
                                           :completed false})
                 (short-delay)
                 (should=
                  {:_id test-id :grub test-grub :completed false}
                  (mc/find-one-as-map db/grub-collection {:_id test-id})))))

 (describe "Complete"
           (it "should complete a grub when a complete event comes"
               (let [test-grub {:_id 123456 :completed false}]
                 (mc/insert db/grub-collection test-grub)
                 (>!! @db/incoming-events {:event :complete :_id (:_id test-grub)})
                 (short-delay)
                 (should=
                  {:_id (:_id test-grub) :completed true}
                  (mc/find-one-as-map db/grub-collection {:_id (:_id test-grub)})))))

 (describe "Uncomplete"
           (it "should uncomplete a grub when an uncomplete event comes"
               (let [test-grub {:_id 123456 :completed true}]
                 (mc/insert db/grub-collection test-grub)
                 (>!! @db/incoming-events {:event :uncomplete :_id (:_id test-grub)})
                 (short-delay)
                 (should= 
                  {:_id (:_id test-grub) :completed false}
                  (mc/find-one-as-map db/grub-collection {:_id (:_id test-grub)})))))

 (describe "Delete"
           (it "should delete a grub when a delete event comes"
               (let [test-grub {:_id 123456 :completed true}]
                 (mc/insert db/grub-collection test-grub)
                 (>!! @db/incoming-events {:event :delete :_id (:_id test-grub)})
                 (short-delay)
                 (should= 
                  nil
                  (mc/find-one-as-map db/grub-collection {:_id (:_id test-grub)}))))))
