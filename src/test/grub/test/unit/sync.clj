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

(deftest apply-diff-no-changes
  ;; Apply changes and return ACK for in sync client/server
  (let [state {:grubs {"1" {:text "2 apples" :completed false}}
               :recipes {}}
        hash (hasch/uuid state)
        states [{:hash hash :state state}]
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        shadow-hash hash
        {:keys [new-states new-shadow full-sync?]} (s/apply-diff states diff shadow-hash)]
    (do 
      (is (= {:grubs {"1" {:text "2 apples" :completed true}}
              :recipes {}}
             (:state (last new-states))))
      (is (= {:grubs {"1" {:text "2 apples" :completed true}}
              :recipes {}}
             new-shadow))
      (is (not full-sync?)))))

(deftest apply-diff-server-state-changed
  ;; Send differences back if server state changed
  (let [state {:grubs {"1" {:text "3 apples" :completed false}} :recipes {}}
        prev {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        states [{:hash (hasch/uuid state) :state state}
                {:hash (hasch/uuid prev) :state prev}]
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        shadow-hash (hasch/uuid prev)
        {:keys [new-states new-shadow full-sync?]} (s/apply-diff states diff shadow-hash)]
    (do 
      (is (= {:grubs {"1" {:text "3 apples" :completed true}}
              :recipes {}}
             (:state (last new-states))))
      (is (= {:grubs {"1" {:text "2 apples" :completed true}}
              :recipes {}}
             new-shadow))
      (is (not full-sync?)))))

(deftest apply-diff-client-out-of-sync
  ;; Shadow hash not in history means client has fallen too far
  ;; out of sync. Send a full sync
  (let [state {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        prev {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        states [{:hash (hasch/uuid state) :state state}
                {:hash (hasch/uuid prev) :state prev}]
        shadow-hash (hasch/uuid {:grubs {} :recipes {}})
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        {:keys [new-states new-shadow full-sync?]} (s/apply-diff states diff shadow-hash)]
    (do 
      (is (= state (:state (last new-states))))
      (is (= state new-shadow))
      (is full-sync?))))
