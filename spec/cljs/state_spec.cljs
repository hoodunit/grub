(ns grub.state-spec 
  (:require [specljs.core]
            [grub.state :as state])
  (:require-macros [specljs.core :refer [describe it before 
                                         should= should-contain
                                         should-not-be-nil]] 
                   [grub.macros :refer [log logs]]))

(describe 
 "grub state"
 (before (reset! state/grubs []))

 (describe "Add grub" 
           (it "should add a grub to the state when an add event comes"
               (let [test-grub {:_id 12345 :grub "testgrub" :completed true}
                     add-event (assoc test-grub :event :add)]
                 (state/handle-event add-event)
                 (should-contain test-grub @state/grubs))))

 (describe "Create grub" 
           (it "should add a new grub to the state when a create event comes"
               (let [test-grub {:grub "testgrub"}
                     expected-grub (assoc test-grub :completed false)
                     create-event (assoc test-grub :event :create)]
                 (state/handle-event create-event)
                 (let [created-grub (first @state/grubs)]
                   (should= (:grub created-grub) (:grub test-grub)))))
           (it "should generate an _id for the new grub"
               (let [test-grub {:grub "testgrub"}
                     create-event (assoc test-grub :event :create)]
                 (state/handle-event create-event)
                 (let [added-grub (first (filter #(= (:grub %) (:grub test-grub)) 
                                                 @state/grubs))]
                   (should-not-be-nil (:_id added-grub))))))
 
 (describe "Complete grub" 
           (it "should complete a grub in the state when a complete event comes"
               (let [test-grub {:_id 234243 :grub "testgrub" :completed false}
                     expected-grub (assoc test-grub :completed true)
                     complete-event (-> (select-keys [:_id])
                                      (assoc :event :complete))]
                 (reset! state/grubs [test-grub])
                 (state/handle-event complete-event)
                 (should-contain expected-grub @state/grubs))))

 (describe "Uncomplete grub" 
           (it "should uncomplete a grub in the state when an uncomplete event comes"
               (let [test-grub {:_id 234243 :grub "testgrub" :completed true}
                     expected-grub (assoc test-grub :completed false)
                     complete-event (-> (select-keys [:_id])
                                      (assoc :event :uncomplete))]
                 (reset! state/grubs [test-grub])
                 (state/handle-event complete-event)
                 (should-contain expected-grub @state/grubs))))

 (describe "Delete grub" 
           (it "should delete a grub from the state when a delete event comes"
               (let [test-grub {:_id 234243 :grub "testgrub" :completed true}
                     delete-event {:_id (:_id test-grub) :event :delete}]
                 (reset! state/grubs [test-grub])
                 (state/handle-event delete-event)
                 (should= [] @state/grubs)))))
