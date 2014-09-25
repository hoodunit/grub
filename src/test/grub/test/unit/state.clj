(ns grub.test.unit.state
  (:require [grub.state :as state]
            [clojure.test :refer :all]
            [hasch.core :as hasch]
            [clojure.core.async :as a :refer [<!! >!! chan go]]))

(defn hashed-states [& states]
  (->> states
       (map (fn [s] {:hash (hasch/uuid s)
                     :state s}))
       (into [])))

(deftest diff-no-server-changes
  ;; Returns empty ACK diff with hash of current state
  ;; when no server changes
  (let [states (hashed-states
                 {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}})
        states* (atom states)
        msg {:type :diff
                  :diff {:grubs {:updated {"1" {:completed true}} :deleted #{}}}
                  :hash (:hash (first states))}
        in (chan 1)
        out (chan 1)]
    (state/make-agent in out states*)
    (>!! in msg)
    (let [diff-response (<!! out)]
      (is (= (hashed-states
              {:grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
              {:grubs {"1" {:completed true, :text "2 apples"}}, :recipes {}})
             @states*))
      (is (= {:type :diff
              :diff {:grubs {:deleted #{}, :updated nil}
                     :recipes {:deleted #{}, :updated nil}}
              :hash (:hash (last @states*))}
             diff-response)))))

(deftest diff-server-changes
  ;; Returns diff with changes when server has changed
  ;; Client state fetched from history
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
    (state/make-agent in out states*)
    (>!! in msg)
    (let [diff-response (<!! out)]
      (is (= (hashed-states
              {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
              {:grubs {"1" {:text "2 apples" :completed false}
                       "2" {:text "3 onions" :completed false}}
               :recipes {}}
              {:grubs {"1" {:text "2 apples" :completed true}
                       "2" {:text "3 onions" :completed false}}
               :recipes {}})
             @states*))
      (is (= {:type :diff
              :diff {:grubs {:deleted #{}
                             :updated {"2" {:completed false, :text "3 onions"}}}
                     :recipes {:deleted #{}, :updated nil}}
              :hash (hasch/uuid {:grubs {"1" {:text "2 apples" :completed true}}
                                        :recipes {}})}
             diff-response)))))

(deftest diff-client-out-of-sync
  ;; Returns full sync if client state not found
  ;; in history
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
    (state/make-agent in out states*)
    (>!! in msg)
    (let [diff-response (<!! out)]
      (is (= (hashed-states
              {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
              {:grubs {"1" {:text "2 apples" :completed false}
                       "2" {:text "3 onions" :completed false}}
               :recipes {}})
             @states*))
      (is (= {:type :full-sync
              :state {:grubs {"1" {:text "2 apples" :completed false}
                       "2" {:text "3 onions" :completed false}}
               :recipes {}}}
             diff-response)))))

(deftest full-sync-request
  ;; Returns full sync if client requests it
  (let [states (hashed-states
                {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                {:grubs {"1" {:text "2 apples" :completed false}
                         "2" {:text "3 onions" :completed false}}
                 :recipes {}})
        states* (atom states)
        msg {:type :full-sync}
        in (chan 1)
        out (chan 1)]
    (state/make-agent in out states*)
    (>!! in msg)
    (let [diff-response (<!! out)]
      (is (= (hashed-states
              {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
              {:grubs {"1" {:text "2 apples" :completed false}
                       "2" {:text "3 onions" :completed false}}
               :recipes {}})
             @states*))
      (is (= {:type :full-sync
              :state {:grubs {"1" {:text "2 apples" :completed false}
                              "2" {:text "3 onions" :completed false}}
                      :recipes {}}}
             diff-response)))))

(deftest new-state
  ;; Passes diff with new state to client
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
    (state/make-agent in out states* client-state)
    (>!! in msg)
    (let [diff-response (<!! out)]
      (is (= (hashed-states
              {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
              {:grubs {"1" {:text "2 apples" :completed false}
                       "2" {:text "3 onions" :completed false}}
               :recipes {}}
              {:grubs {"1" {:text "2 apples" :completed false}
                       "2" {:text "3 onions" :completed false}
                       "3" {:text "milk" :completed false}}
               :recipes {}})
             @states*))
      (is (= {:type :diff
              :diff {:grubs {:deleted #{}
                             :updated {"2" {:text "3 onions" :completed false}
                                       "3" {:text "milk" :completed false}}}
                     :recipes {:deleted #{}, :updated nil}}
              :hash (hasch/uuid client-state)}
             diff-response)))))
