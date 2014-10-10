(ns grub.test.unit.sync
  (:require [grub.sync :as s]
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
