(ns grub.db-test
  (:require [midje.sweet :refer [fact with-state-changes contains anything]]
            [monger.collection :as mc]
            [grub.db :as db]
            [clojure.core.async :refer [>!!]]))

(def test-db "grub-test")

(with-state-changes [(before :facts (do (db/connect-and-handle-events test-db)
                                        (mc/drop db/grub-collection)))]
  (fact 
   "Create grub"
   (fact "sending an event creates a grub"
         (let [test-grub "testgrub"
               test-id 12345]
           (>!! @db/incoming-events {:event :create :_id test-id :grub test-grub})
           (mc/find-one-as-map db/grub-collection {:_id test-id}) => 
           {:_id test-id :grub test-grub :completed false})))
  (fact "Complete grub"
   (fact "sending an event completes a grub"
         (let [test-grub {:_id 123456 :completed false}]
           (mc/insert db/grub-collection test-grub)
           (>!! @db/incoming-events {:event :complete :_id (:_id test-grub)})
           (mc/find-one-as-map db/grub-collection {:_id (:_id test-grub)}) => 
           {:_id (:_id test-grub) :completed true})))
  (fact "Uncomplete grub"
   (fact "sending an event uncompletes a grub"
         (let [test-grub {:_id 123456 :completed true}]
           (mc/insert db/grub-collection test-grub)
           (>!! @db/incoming-events {:event :uncomplete :_id (:_id test-grub)})
           (mc/find-one-as-map db/grub-collection {:_id (:_id test-grub)}) => 
           {:_id (:_id test-grub) :completed false})))
  (fact "Delete grub"
   (fact "sending an event deletes a grub"
         (let [test-grub {:_id 123456 :completed true}]
           (mc/insert db/grub-collection test-grub)
           (>!! @db/incoming-events {:event :delete :_id (:_id test-grub)})
           (mc/find-one-as-map db/grub-collection {:_id (:_id test-grub)}) => 
           nil))))
