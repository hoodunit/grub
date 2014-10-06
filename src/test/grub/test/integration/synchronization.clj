(ns grub.test.integration.synchronization
  (:require [grub.state :as state]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [hasch.core :as hasch]
            [clojure.core.async :as a :refer [<!! >!! chan go]]))

(defn hashed-states [& states]
  (->> states
       (map (fn [s] {:hash (hasch/uuid s)
                     :state s}))
       (into [])))

(defn states-atom [& states]
  (atom (apply hashed-states states)))

(defn <!!? [c]
  (let [[v p] (a/alts!! [c (a/timeout 100)])]
    v))

(fact "Client-only changes synced with server"
  (let [client-shadow {:grubs {"1" {:text "2 apples" :completed true}} :recipes {}}
        client-states (states-atom
                       {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                       {:grubs {"1" {:text "2 apples" :completed true}} :recipes {}})
        server-shadow {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        server-states (states-atom server-shadow)
        client-in (chan)
        client-out (chan)
        server-in (chan)
        server-out (chan)
        client-state-changes (chan 1)
        msg {:type :new-state
             :state {:grubs {"1" {:text "2 apples" :completed true}} :recipes {}}}]
    (a/pipe client-out server-in)
    (a/pipe server-out client-in)
    (state/make-client-agent client-in client-out client-states server-shadow)
    (state/make-server-agent server-in server-out server-states client-shadow)
    (add-watch client-states :test (fn [_ _ _ new-states] (a/put! client-state-changes new-states)))
    (>!! client-in msg)
    (<!!? client-state-changes)
    (:state (last @client-states)) => {:grubs {"1" {:completed true, :text "2 apples"}}
                                       :recipes {}}
    (:state (last @server-states)) => {:grubs {"1" {:completed true, :text "2 apples"}}
                                       :recipes {}}))

(fact "Client and server changes synced"
  (let [client-shadow {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        client-states (states-atom
                       {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
                       {:grubs {"1" {:text "2 apples" :completed true}} :recipes {}})
        server-shadow {:grubs {"1" {:text "2 apples" :completed false}} :recipes {}}
        server-states (states-atom 
                       server-shadow
                       {:grubs {"1" {:text "4 apples" :completed false}} :recipes {}})
        client-in (chan)
        client-out (chan)
        server-in (chan)
        server-out (chan)
        msg {:type :new-state
             :state {:grubs {"1" {:text "2 apples" :completed true}} :recipes {}}}
        client-state-changes (chan 1)]
    (a/pipe client-out server-in)
    (a/pipe server-out client-in)
    (state/make-client-agent client-in client-out client-states server-shadow)
    (state/make-server-agent server-in server-out server-states client-shadow)
    (add-watch client-states :test (fn [_ _ _ new-states] (a/put! client-state-changes new-states)))
    (>!! client-in msg)
    (<!!? client-state-changes)
    @client-states => (hashed-states
                       {:grubs {"1" {:completed true, :text "4 apples"}}, :recipes {}})
    @server-states => (hashed-states
                       {:grubs {"1" {:completed false, :text "2 apples"}}, :recipes {}}
                       {:grubs {"1" {:completed false, :text "4 apples"}}, :recipes {}}
                       {:grubs {"1" {:completed true, :text "4 apples"}}, :recipes {}})))
