(ns grub.test.unit.sync
  (:require [grub.sync :as s]
            [clojure.test :refer :all]
            [hasch.core :as hasch]))

(deftest initial-state
  (let [grubs [{:id "1" :text "2 bananas" :completed false}
               {:id "2" :text "3 onions" :completed false}]
        recipes []
        expected-state {:grubs {"1" {:id "1" :text "2 bananas" :completed false}
                                "2" {:id "2" :text "3 onions" :completed false}}
                        :recipes {}}
        expected-hash (hasch/uuid expected-state)]
    (is (= [{:state expected-state :hash expected-hash}] (s/initial-state grubs recipes)))))

(deftest get-current-state-returns-last-state
  (let [states [{:hash "asdf" :state {:a :b}}
                {:hash "fdsa" :state {:c :d}}]]
    (is (= {:c :d} (s/get-current-state states)))))

(deftest get-history-state-returns-state-with-hash
  (let [states [{:hash "hash1" :state {:a :b}}
                {:hash "hash2" :state {:c :d}}
                {:hash "hash3" :state {:e :f}}]]
    (is (= {:a :b} (s/get-history-state states "hash1")))
    (is (= {:c :d} (s/get-history-state states "hash2")))
    (is (= {:e :f} (s/get-history-state states "hash3")))))
