(ns grub.db-test
  (:require [midje.sweet :refer [fact with-state-changes contains anything]]
            [monger.collection :as mc]
            [grub.db :as db]
            [clojure.core.async :refer [>!!]]))

(def test-db "grub-test")

(with-state-changes [(before :facts (do (mc/drop db/grub-collection)
                                        (db/connect-and-handle-events test-db)))]
  (fact 
   "Add grub"
   (fact "sending an event creates a grub"
         (let [test-grub "testgrub"
               test-id 12345]
           (>!! @db/incoming-events {:event :create :_id test-id :grub test-grub})
           (mc/find-one-as-map db/grub-collection {:_id test-id}) => 
           {:_id test-id :grub test-grub :completed false}))
   (fact "missing an id means grub is not created"
         (>!! @db/incoming-events {:event :create :grub "testgrub"})
         (mc/find-maps db/grub-collection) => 
         empty?)
   (fact "missing grub text means grub is not created"
         (>!! @db/incoming-events {:event :create :_id 12345})
         (mc/find-maps db/grub-collection) => 
         empty?)))
