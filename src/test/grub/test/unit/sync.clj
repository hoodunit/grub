(ns grub.test.unit.sync
  (:require [grub.state :as state]
            [grub.sync :as sync]
            [midje.sweet :refer :all]
            [hasch.core :as hasch]))

(defn hashed-states [& states]
  (->> states
       (map (fn [s] {:hash (hasch/uuid s)
                     :state s}))
       (into [])))

(fact "Server applies diff and returns empty diff when no server changes"
  (let [states (atom (hashed-states
                      {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}))
        event {:type :diff
               :diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
               :hash (:hash (first @states))
               :states states
               :shadow (:state (last @states))
               :client? false}
        {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
    new-states => (hashed-states
                   {:grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
                   {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}})
    new-shadow {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}}
    out-event => {:type :diff
                  :diff {:grubs {:deleted #{}, :updated nil}
                         :recipes {:deleted #{}, :updated nil}}
                  :hash (:hash (last new-states))}))

(fact "Client applies diff and returns empty diff when no client changes"
  (let [states (atom (hashed-states
                      {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}))
        event {:type :diff
               :diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
               :hash (:hash (first @states))
               :states states
               :shadow (:state (last @states))
               :client? true}
        {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
    new-states => (hashed-states
                   {:grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
                   {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}}) 
    new-shadow => {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}}
    out-event => {:type :diff
                  :diff {:grubs {:deleted #{}, :updated nil}
                         :recipes {:deleted #{}, :updated nil}}
                  :hash (:hash (last new-states))}))

(fact "Server applies diff and returns changes when server has changed"
  (let [states (atom (hashed-states
                      {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                      {:grubs {"1" {:text "2 apples" :completed false}
                               "2" {:text "3 onions" :completed false}}
                       :recipes {}}))
        event {:type :diff
               :diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
               :hash (:hash (first @states))
               :states states
               :shadow state/empty-state
               :client? false}
        {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
    new-states => (hashed-states
                   {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed true}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}})
    out-event => {:type :diff
                  :diff {:grubs {:deleted #{}
                                 :updated {"2" {:completed false, :text "3 onions"}}}
                         :recipes {:deleted #{}, :updated nil}}
                  :hash (hasch/uuid {:grubs {"1" {:text "2 apples" :completed true}}
                                     :recipes {}})}))

(fact "Server forces full sync if client is out of sync"
  (let [states (atom (hashed-states
                      {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                      {:grubs {"1" {:text "2 apples" :completed false}
                               "2" {:text "3 onions" :completed false}}
                       :recipes {}}))
        event {:type :diff
               :diff {:grubs {:updated {"0" {:completed true}} :deleted #{}}}
               :hash (:hash {:grubs {"0" {:text "milk" :completed false}} 
                             :recipes {}})
               :states states
               :shadow state/empty-state
               :client? false}
        {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
    new-states => nil
    out-event => {:type :full-sync
                  :full-state {:grubs {"1" {:text "2 apples" :completed false}
                                  "2" {:text "3 onions" :completed false}}
                          :recipes {}}}))

(fact "Server sends full sync if client requests it"
  (let [states (atom (hashed-states
                      {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                      {:grubs {"1" {:text "2 apples" :completed false}
                               "2" {:text "3 onions" :completed false}}
                       :recipes {}}))
        event {:type :full-sync-request
               :states states}
        {:keys [new-states new-shadow out-event]} (sync/handle-event event)]
    new-states => nil
    out-event => {:type :full-sync
                  :full-state {:grubs {"1" {:text "2 apples" :completed false}
                                  "2" {:text "3 onions" :completed false}}
                          :recipes {}}}))
