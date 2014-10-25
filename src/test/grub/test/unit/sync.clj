(ns grub.test.unit.sync
  (:require [grub.state :as state]
            [grub.sync :as sync]
            [midje.sweet :refer :all]))

(facts "Server"
  (fact "Diff, no server changes - Apply diff, return empty diff"
    (let [states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          {:keys [out-event new-shadow]} 
          (sync/handle-event 
           {:type :diff
            :tag 4
            :shadow-tag 0
            :diff {:grubs {:+ {"1" {:completed true}} :- #{}}}
            :states states
            :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
            :client? false})]
      @states => (just (just {:tag 0
                              :grubs {"1" {:completed false, :text "2 apples"}}
                              :recipes {}})
                       (just {:tag 1
                              :grubs {"1" {:completed true, :text "2 apples"}}
                              :recipes {}}))
      out-event => (just {:type :diff
                          :diff {:grubs {:- #{} :+ nil} :recipes {:- #{}, :+ nil}}
                          :shadow-tag 4
                          :tag 1})
      new-shadow => {:tag 4
                     :grubs {"1" {:completed true, :text "2 apples"}}
                     :recipes {}}))

  (fact "Diff, server changes - Apply diff, return changes"
    (let [states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                        {:tag 1 :grubs {"1" {:text "2 apples" :completed false}
                                        "2" {:text "3 onions" :completed false}}
                         :recipes {}}])
          {:keys [new-shadow out-event]} 
          (sync/handle-event 
           {:type :diff
            :shadow-tag 0
            :tag 4
            :diff {:grubs {:+ {"1" {:completed true}} :- #{}}}
            :states states
            :shadow state/empty-state
            :client? false})]
      @states => 
      (just {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
            {:tag 1 :grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}} :recipes {}}
            {:tag 2 :grubs {"1" {:text "2 apples" :completed true} 
                            "2" {:text "3 onions" :completed false}} :recipes {}})
      out-event =>
      (just {:type :diff
             :shadow-tag 4
             :tag 2
             :diff {:grubs {:- #{} :+ {"2" {:completed false, :text "3 onions"}}}
                    :recipes {:- #{}, :+ nil}}})
      new-shadow => {:tag 4
                     :grubs {"1" {:text "2 apples" :completed true}}
                     :recipes {}}))
  
  (fact "Diff, client out of sync - Force full sync"
    (let [states (atom [{:tag 14 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                        {:tag 15 :grubs {"1" {:text "2 apples" :completed false}
                                         "2" {:text "3 onions" :completed false}}
                         :recipes {}}])
          event {:type :diff
                 :shadow-tag 3
                 :tag 12
                 :diff {:grubs {:updated {"0" {:completed true}} :deleted #{}}}
                 :states states
                 :shadow state/empty-state
                 :client? false}
          {:keys [new-shadow out-event]} (sync/handle-event event)]
      out-event => {:type :full-sync
                    :full-state {:tag 15
                                 :grubs {"1" {:text "2 apples" :completed false}
                                         "2" {:text "3 onions" :completed false}}
                                 :recipes {}}}))
  
  (fact "New state - Update state, send diff, update shadow assuming diff received"
    (let [states (atom [{:tag 14 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          event {:type :new-state
                 :states states
                 :shadow {:tag 3 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                 :client? false
                 :new-state {:grubs {"1" {:text "2 apples" :completed true}} :recipes {}}}
          {:keys [new-shadow out-event]} (sync/handle-event event)]
      @states => [{:tag 14 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                  {:tag 15 :grubs {"1" {:text "2 apples" :completed true}} :recipes {}}]
      new-shadow => {:tag 4 :grubs {"1" {:text "2 apples" :completed true}} :recipes {}}
      out-event => {:type :diff
                    :shadow-tag 3
                    :tag 15
                    :diff {:grubs {:+ {"1" {:completed true}} :- #{}}
                           :recipes {:+ nil :- #{}}}}))
  
  (fact "Server sends full sync if client requests it"
    (let [result (sync/handle-event 
                  {:type :full-sync-request
                   :states (atom [{:tag 14 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                                  {:tag 15 :grubs {"1" {:text "2 apples" :completed false}
                                                   "2" {:text "3 onions" :completed false}}
                                   :recipes {}}])})]
      (:new-shadow result) => 
      (just {:tag #(not (nil? %))
             :grubs {"1" {:text "2 apples" :completed false}
                     "2" {:text "3 onions" :completed false}}
             :recipes {}})
      (:out-event result) => 
      {:type :full-sync
       :full-state {:tag 15
                    :grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}}}))
  )


(facts "Client diffs"
  (fact "Client applies diff, does not return diff when no client changes"
    (let [states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          event {:type :diff
                 :shadow-tag 0
                 :tag 4
                 :diff {:grubs {:+ {"1" {:completed true}} :- #{}}}
                 :states states
                 :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}}
                          :recipes {}}
                 :client? true}
          {:keys [new-shadow out-event]} (sync/handle-event event)]
      @states => 
      (just {:tag 0 :grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
            {:tag 1
             :grubs {"1" {:completed true, :text "2 apples"}}
             :recipes {}})
      new-shadow {:tag 4 :grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}}
      out-event => nil))

  (fact "Client state is unchanged on receiving empty diff"
    (let [states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])]
      (sync/handle-event 
       {:type :diff
        :shadow-tag 0
        :tag 4
        :diff {:grubs {:+ nil :- #{}}}
        :states states
        :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        :client? true})
      @states => [{:tag 0
                   :grubs {"1" {:completed false, :text "2 apples"}}
                   :recipes {}}]))
  
  (fact "Client returns no response on empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :shadow-tag 0
          :tag 4
          :diff {:grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? true})
        :out-event)
    => nil)

  (fact "Client updates server shadow on empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :shadow-tag 0
          :tag 4
          :diff {:grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? true})
        :new-shadow)
    => {:tag 4 :grubs {"1" {:completed false, :text "2 apples"}} :recipes {}}))

(facts "Full sync"
  (fact "Server sends full sync if client requests it"
    (let [result (sync/handle-event 
                  {:type :full-sync-request
                   :states (atom [{:tag 14 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                                  {:tag 15 :grubs {"1" {:text "2 apples" :completed false}
                                                   "2" {:text "3 onions" :completed false}}
                                   :recipes {}}])})]
      (:new-shadow result) => 
      (just {:tag #(not (nil? %))
             :grubs {"1" {:text "2 apples" :completed false}
                     "2" {:text "3 onions" :completed false}}
             :recipes {}})
      (:out-event result) => 
      {:type :full-sync
       :full-state {:tag 15
                    :grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}}})))
