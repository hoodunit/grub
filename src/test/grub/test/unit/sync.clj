(ns grub.test.unit.sync
  (:require [grub.state :as state]
            [grub.sync :as sync]
            [midje.sweet :refer :all]))

(facts "Server diff"
  (fact "Server applies diff, returns empty diff with client tag, new server tag when no server changes"
    (let [{:keys [new-states out-event new-shadow]} 
          (sync/handle-event 
           {:type :diff
            :diff {:tag 4 :shadow-tag 0 :grubs {:+ {"1" {:completed true}} :- #{}}}
            :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
            :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
            :client? false})]
      new-states => (just (just {:tag 0
                                 :grubs {"1" {:completed false, :text "2 apples"}}
                                 :recipes {}})
                          (just {:tag #(not (nil? %))
                                 :grubs {"1" {:completed true, :text "2 apples"}}
                                 :recipes {}}))
      out-event => (just {:type :diff
                          :diff (just {:shadow-tag 4
                                       :tag #(not (nil? %))
                                       :grubs {:- #{} :+ nil}
                                       :recipes {:- #{}, :+ nil}})})
      new-shadow => {:tag 4
                     :grubs {"1" {:completed true, :text "2 apples"}}
                     :recipes {}}))

  (fact "Server applies diff and returns changes when server has changed"
    (let [{:keys [new-states new-shadow out-event]} 
          (sync/handle-event 
           {:type :diff
            :diff {:shadow-tag 0 :tag 4 
                   :grubs {:+ {"1" {:completed true}} :- #{}}}
            :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                           {:tag 1 :grubs {"1" {:text "2 apples" :completed false}
                                           "2" {:text "3 onions" :completed false}}
                            :recipes {}}])
            :shadow state/empty-state
            :client? false})]
      new-states => 
      (just {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
            {:tag 1 :grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
             :recipes {}}
            (just {:tag #(not (nil? %)) :grubs {"1" {:text "2 apples" :completed true}
                                                "2" {:text "3 onions" :completed false}}
                   :recipes {}}))
      out-event =>
      (just {:type :diff
             :diff (just {:shadow-tag 4
                          :tag #(not (nil? %))
                          :grubs {:- #{} :+ {"2" {:completed false, :text "3 onions"}}}
                          :recipes {:- #{}, :+ nil}})})
      new-shadow => {:tag 4
                     :grubs {"1" {:text "2 apples" :completed true}}
                     :recipes {}}))
  
  (fact "Server forces full sync if client is out of sync"
    (let [states (atom [{:tag 14 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                        {:tag 15 :grubs {"1" {:text "2 apples" :completed false}
                                         "2" {:text "3 onions" :completed false}}
                         :recipes {}}])
          event {:type :diff
                 :diff {:shadow-tag 3 :tag 12 
                        :grubs {:updated {"0" {:completed true}} :deleted #{}}}
                 :states states
                 :shadow state/empty-state
                 :client? false}
          {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
      new-states => nil
      out-event => {:type :full-sync
                    :full-state {:tag 15
                                 :grubs {"1" {:text "2 apples" :completed false}
                                         "2" {:text "3 onions" :completed false}}
                                 :recipes {}}}))
  
  (fact "Server state is unchanged on receiving empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :diff {:tag 4 :shadow-tag 0 :grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? false})
        :new-states)
    => [{:tag 0
         :grubs {"1" {:completed false, :text "2 apples"}}
         :recipes {}}])
  
  (fact "Server returns no response on empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :diff {:tag 4 :shadow-tag 0 :grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? false})
        :out-event)
    => nil)

  (fact "Server updates client shadow on empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :diff {:tag 4 :shadow-tag 0 :grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? false})
        :new-shadow)
    => {:tag 4 :grubs {"1" {:completed false, :text "2 apples"}} :recipes {}}))

(facts "Client diffs"
  (fact "Client applies diff, returns empty diff with server tag, new client tag when no client changes"
    (let [event {:type :diff
                 :diff {:tag 4 :shadow-tag 0 :grubs {:+ {"1" {:completed true}} :- #{}}}
                 :states (atom 
                          [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}}
                            :recipes {}}])
                 :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}}
                          :recipes {}}
                 :client? true}
          {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
      new-states => 
      (just {:tag 0 :grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
            (just {:tag #(not (nil? %))
                   :grubs {"1" {:completed true, :text "2 apples"}}
                   :recipes {}}))
      new-shadow {:tag 4 :grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}}
      out-event => (just {:type :diff
                          :diff (just {:shadow-tag 4
                                       :tag #(not (nil? %))
                                       :grubs {:- #{} :+ nil}
                                       :recipes {:- #{}, :+ nil}})})))

  (fact "Client state is unchanged on receiving empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :diff {:tag 4 :shadow-tag 0 :grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? true})
        :new-states)
    => [{:tag 0
         :grubs {"1" {:completed false, :text "2 apples"}}
         :recipes {}}])
  
  (fact "Client returns no response on empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :diff {:tag 4 :shadow-tag 0 :grubs {:+ nil :- #{}}}
          :states (atom [{:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}])
          :shadow {:tag 0 :grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
          :client? true})
        :out-event)
    => nil)

  (fact "Client updates server shadow on empty diff"
    (-> (sync/handle-event 
         {:type :diff
          :diff {:tag 4 :shadow-tag 0 :grubs {:+ nil :- #{}}}
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
      (:new-states result) => nil
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
