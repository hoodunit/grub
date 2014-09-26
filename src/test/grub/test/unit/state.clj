(ns grub.test.unit.state
  (:require [grub.shared-state :as state]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [hasch.core :as hasch]
            [clojure.core.async :as a :refer [<!! >!! chan go]]))

(defn hashed-states [& states]
  (->> states
       (map (fn [s] {:hash (hasch/uuid s)
                     :state s}))
       (into [])))

(fact "Applies diff and returns empty diff when no server changes"
  (let [states (hashed-states
                {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}})
        states* (atom states)
        msg {:type :diff
             :diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
             :hash (:hash (first states))}
        in (chan 1)
        out (chan 1)]
    (state/make-server-agent in out states*)
    (>!! in msg)
    (let [response (<!! out)]
      @states* => (hashed-states
                   {:grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
                   {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}})
      response => {:type :diff
                   :diff {:grubs {:deleted #{}, :updated nil}
                          :recipes {:deleted #{}, :updated nil}}
                   :hash (:hash (last @states*))})))

(fact "Applies diff and returns changes when server has changed"
  (let [states (hashed-states
                {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                {:grubs {"1" {:text "2 apples" :completed false}
                         "2" {:text "3 onions" :completed false}}
                 :recipes {}})
        states* (atom states)
        msg {:type :diff
             :diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
             :hash (:hash (first states))}
        in (chan 1)
        out (chan 1)]
    (state/make-server-agent in out states*)
    (>!! in msg)
    (let [response (<!! out)]
      @states* => (hashed-states
                   {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed true}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}})
      response => {:type :diff
                   :diff {:grubs {:deleted #{}
                                  :updated {"2" {:completed false, :text "3 onions"}}}
                          :recipes {:deleted #{}, :updated nil}}
                   :hash (hasch/uuid {:grubs {"1" {:text "2 apples" :completed true}}
                                      :recipes {}})})))

(fact "Force full sync if client is out of sync"
  (let [states (hashed-states
                {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                {:grubs {"1" {:text "2 apples" :completed false}
                         "2" {:text "3 onions" :completed false}}
                 :recipes {}})
        states* (atom states)
        msg {:type :diff
             :diff {:grubs {:updated {"0" {:completed true}} :deleted #{}}}
             :hash (:hash {:grubs {"0" {:text "milk" :completed false}} 
                           :recipes {}})}
        in (chan 1)
        out (chan 1)]
    (state/make-server-agent in out states*)
    (>!! in msg)
    (let [response (<!! out)]
      @states* => (hashed-states
                   {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}})
      response => {:type :full-sync
                   :state {:grubs {"1" {:text "2 apples" :completed false}
                                   "2" {:text "3 onions" :completed false}}
                           :recipes {}}})))

(fact "Full sync if client requests it"
  (let [states (hashed-states
                {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                {:grubs {"1" {:text "2 apples" :completed false}
                         "2" {:text "3 onions" :completed false}}
                 :recipes {}})
        states* (atom states)
        msg {:type :full-sync}
        in (chan 1)
        out (chan 1)]
    (state/make-server-agent in out states*)
    (>!! in msg)
    (let [response (<!! out)]
      @states* => (hashed-states
                   {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}})
      response => {:type :full-sync
                   :state {:grubs {"1" {:text "2 apples" :completed false}
                                   "2" {:text "3 onions" :completed false}}
                           :recipes {}}})))

(fact "Passes diffs of new states to client"
  (let [states (hashed-states
                {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                {:grubs {"1" {:text "2 apples" :completed false}
                         "2" {:text "3 onions" :completed false}}
                 :recipes {}}
                {:grubs {"1" {:text "2 apples" :completed false}
                         "2" {:text "3 onions" :completed false}
                         "3" {:text "milk" :completed false}}
                 :recipes {}})
        states* (atom states)
        client-state {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        msg {:type :new-state
             :new-states states}
        in (chan 1)
        out (chan 1)]
    (state/make-server-agent in out states* client-state)
    (>!! in msg)
    (let [response (<!! out)]
      @states* => (hashed-states
                   {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}}
                    :recipes {}}
                   {:grubs {"1" {:text "2 apples" :completed false}
                            "2" {:text "3 onions" :completed false}
                            "3" {:text "milk" :completed false}}
                    :recipes {}})
      response => {:type :diff
                   :diff {:grubs {:deleted #{}
                                  :updated {"2" {:text "3 onions" :completed false}
                                            "3" {:text "milk" :completed false}}}
                          :recipes {:deleted #{}, :updated nil}}
                   :hash (hasch/uuid client-state)})))
