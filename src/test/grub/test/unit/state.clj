(ns grub.test.unit.state
  (:require [grub.state :as s]
            [grub.common-state :as cs]
            [clojure.test :refer :all]
            [hasch.core :as hasch]))

(deftest apply-diff-normally
  ;; Apply changes and return ACK for in sync client/server
  (let [state {:grubs {"1" {:text "2 apples" :completed false}}
               :recipes {}}
        hash (hasch/uuid state)
        states [{:hash hash :state state}]
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        shadow-hash hash
        {:keys [new-state new-shadow full-sync?]} (s/receive-diff states diff shadow-hash)]
    (do 
      (is (= {:grubs {"1" {:text "2 apples" :completed true}}
              :recipes {}}
             new-state))
      (is (= {:grubs {"1" {:text "2 apples" :completed true}}
              :recipes {}}
             new-shadow))
      (is (not full-sync?)))))

(deftest server-state-changed
  ;; Send differences back if server state changed
  (let [state {:grubs {"1" {:text "3 apples" :completed false}} :recipes {}}
        prev {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        states [{:hash (hasch/uuid state) :state state}
                {:hash (hasch/uuid prev) :state prev}]
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        shadow-hash (hasch/uuid prev)
        {:keys [new-state new-shadow full-sync?]} (s/receive-diff states diff shadow-hash)]
    (do 
      (is (= {:grubs {"1" {:text "3 apples" :completed true}}
              :recipes {}}
             new-state))
      (is (= {:grubs {"1" {:text "2 apples" :completed true}}
              :recipes {}}
             new-shadow))
      (is (not full-sync?)))))

(deftest full-sync-if-client-too-far-out-of-sync
  ;; Shadow hash not in history means client has fallen too far
  ;; out of sync. Send a full sync
  (let [state {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        prev {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        states [{:hash (hasch/uuid state) :state state}
                {:hash (hasch/uuid prev) :state prev}]
        shadow-hash (hasch/uuid {:grubs {} :recipes {}})
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        {:keys [new-state new-shadow full-sync?]} (s/receive-diff states diff shadow-hash)]
    (do 
      (is (= state new-state))
      (is (= state new-shadow))
      (is full-sync?))))
