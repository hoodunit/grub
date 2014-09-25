(ns grub.test.unit.state
  (:require [grub.state :as state]
            [clojure.test :refer :all]
            [hasch.core :as hasch]
            [clojure.core.async :as a :refer [<!! >!! chan go]]))

(deftest single-diff
  ;; Returns empty ACK diff
  (let [in (chan 1)
        >client (chan 1)
        state {:grubs {"1" {:text "2 apples" :completed false}}
               :recipes {}}
        hash (hasch/uuid state)
        states* [{:hash hash :state state}]
        states (atom states*)
        diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
        diff-msg {:type :diff
                  :diff diff
                  :hash hash}
        server-agent (state/make-server-agent in >client states)]
    (>!! in diff-msg)
    (let [diff-response (<!! >client)]
      (is (= @states
             [{:hash #uuid "0cb7ae13-2523-52fa-aa79-4a6f2489cafd"
               :state {:grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}}
              {:hash #uuid "166d7e23-5a7b-5101-8364-0d2c06b8d554"
               :state {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}}}]))
      (is (= diff-response
             {:type :diff
              :diff {:grubs {:deleted #{}, :updated nil}
                     :recipes {:deleted #{}, :updated nil}}
              :hash #uuid "166d7e23-5a7b-5101-8364-0d2c06b8d554"})))))
