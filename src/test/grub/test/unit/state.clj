(ns grub.test.unit.state
  (:require [grub.state :as s]
            [midje.sweet :refer :all]
            [hasch.core :as hasch]))

(fact "Sets correct initial state"
  (let [grubs [{:id "1" :text "2 bananas" :completed false}
               {:id "2" :text "3 onions" :completed false}]
        recipes []
        expected-state {:grubs {"1" {:id "1" :text "2 bananas" :completed false}
                                "2" {:id "2" :text "3 onions" :completed false}}
                        :recipes {}}
        expected-hash (hasch/uuid expected-state)]
    (s/initial-state grubs recipes) => [{:state expected-state :hash expected-hash}]))

(fact "Get current state returns last state"
  (let [states [{:hash "asdf" :state {:a :b}}
                {:hash "fdsa" :state {:c :d}}]]
    (s/get-current-state states) => {:c :d}))

(fact "Get history state returns state with given hash"
  (let [states [{:hash "hash1" :state {:a :b}}
                {:hash "hash2" :state {:c :d}}
                {:hash "hash3" :state {:e :f}}]]
    (s/get-history-state states "hash1") => {:a :b}
    (s/get-history-state states "hash2") => {:c :d}
    (s/get-history-state states "hash3") => {:e :f}))

(fact "Add history state appends state to the end"
  (let [states [{:hash "hash1" :state {:a :b}}
                {:hash "hash2" :state {:c :d}}]]
    (:state (last (s/add-history-state states {:e :f}))) => {:e :f}))

(fact "Add history state appends state to the end and drops first state if full"
  (let [states (into [] (for [i (range 20)] {:hash (str "hash" i) :state {:i i}}))
        new-states (s/add-history-state states {:i 21})]
    (count new-states) => 20
    (:state (last new-states)) => {:i 21}
    (:state (first new-states)) => {:i 1}))

(fact "Add history state does not add consecutive duplicate states"
  (let [hash (hasch/uuid {:c :d})
        states [{:hash "hash1" :state {:a :b}}
                {:hash hash :state {:c :d}}]]
    (s/add-history-state states {:c :d}) => states))
