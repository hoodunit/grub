(ns grub.test.integration.synchronization
  (:require [grub.client-sync :as client-sync]
            [grub.server-sync :as server-sync]
            [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [clojure.core.async :as a :refer [<!! >!! chan go]]))

;(defn client-server [client-states server-states]
;  (let [server-shadow (last @server-states)
;        client-shadow (last @client-states)
;        new-client-states (chan)
;        >client (chan)
;        new-server-states (chan)
;        >server (chan)]
;    (client-sync/make-client-agent >server >client new-client-states client-states server-shadow)
;    (server-sync/sync-server! >client >server new-server-states server-states client-shadow)
;    {:new-client-states new-client-states
;     :new-server-states new-server-states}))
;
;(defn states-in-sync? [a b]
;  (let [last-a (dissoc (last a) :tag)
;        last-b (dissoc (last b) :tag)]
;    last-a => last-b))
;
;(defn last-state [states]
;  (-> states
;      (last)
;      (dissoc :tag)))
;
;(defn short-delay []
;  (<!! (a/timeout 300)))
;
;(fact "Client-only changes sync with server"
;  (let [client (atom [{:tag 1
;                       :grubs {"1" {:text "2 apples" :completed false}}
;                       :recipes {}}])
;        server (atom [{:tag 44 :grubs {"1" {:text "2 apples" :completed false}}
;                       :recipes {}}])
;        {:keys [new-client-states]} (client-server client server)
;        client-change {:tag 2
;                       :grubs {"1" {:text "2 apples" :completed true}}
;                       :recipes {}}]
;    (swap! client conj client-change)
;    (>!! new-client-states client-change)
;    (short-delay)
;    (states-in-sync? @client @server)
;    (last-state @client) => {:grubs {"1" {:text "2 apples" :completed true}}
;                             :recipes {}}))
;
;(fact "Other client changes synced with client"
;  (let [client (atom [{:tag 1
;                       :grubs {"1" {:text "2 apples" :completed false}}
;                       :recipes {}}])
;        server (atom [{:tag 44 :grubs {"1" {:text "2 apples" :completed false}}
;                       :recipes {}}])
;        {:keys [new-server-states]} (client-server client server)
;        server-change {:tag 2
;                       :grubs {"1" {:text "2 apples" :completed true}}
;                       :recipes {}}]
;    (swap! server conj server-change)
;    (>!! new-server-states server-change)
;    (short-delay)
;    (states-in-sync? @client @server)
;    (last-state @client) => {:grubs {"1" {:text "2 apples" :completed true}}
;                             :recipes {}}))
;
;(fact "Client changes and simultaneous server changes synced"
;  (let [client (atom [{:tag 1
;                       :grubs {"1" {:text "2 apples" :completed false}}
;                       :recipes {}}])
;        server (atom [{:tag 44 :grubs {"1" {:text "2 apples" :completed false}}
;                       :recipes {}}])
;        {:keys [new-client-states new-server-states]} (client-server client server)
;        client-change {:tag 2
;                       :grubs {"1" {:text "2 apples" :completed true}}
;                       :recipes {}}
;        server-change {:tag 45
;                       :grubs {"1" {:text "2 apples" :completed false}
;                               "2" {:text "milk" :completed false}}
;                       :recipes {}}]
;    (swap! client conj client-change)
;    (swap! server conj server-change)
;    (>!! new-client-states client-change)
;    (short-delay)
;    (>!! new-server-states (last @server))
;    (short-delay)
;    (states-in-sync? @client @server)
;    (last-state @client) => {:grubs {"1" {:text "2 apples" :completed true}
;                                     "2" {:text "milk" :completed false}}
;                             :recipes {}}))
