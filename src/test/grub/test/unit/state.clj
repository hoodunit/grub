(ns grub.test.unit.state
  (:require [grub.state :as s]
            [midje.sweet :refer :all]))

(fact "Get current state returns last state"
  (let [states [{:tag 1 :a :b}
                {:tag 2 :c :d}]]
    (s/get-latest states) => {:tag 2 :c :d}))

(fact "Get history state returns state with given hash"
  (let [states [{:tag 1 :a :b}
                {:tag 2 :c :d}
                {:tag 3 :e :f}]]
    (s/get-tagged states 1) => {:tag 1 :a :b}
    (s/get-tagged states 2) => {:tag 2 :c :d}
    (s/get-tagged states 3) => {:tag 3 :e :f}))

(fact "Add history state appends state to the end and increments tag"
  (let [states [{:tag 1 :a :b}
                {:tag 2 :c :d}]]
    (s/add states {:e :f}) => [{:tag 1 :a :b}
                               {:tag 2 :c :d}
                               {:tag 3 :e :f}]))

(fact "Add history state appends state to the end and drops first state if full"
  (let [states (into [] (for [i (range 20)] {:tag i :i i}))
        new-states (s/add states {:i 21})]
    (count new-states) => 20
    (dissoc (last new-states) :tag) => {:i 21}
    (first new-states) => {:tag 1 :i 1}))

(fact "Add history state does not add consecutive duplicate states"
  (let [states [{:tag 1 :a :b}
                {:tag 2 :c :d}]]
    (s/add states {:tag 2 :c :d}) => states))
