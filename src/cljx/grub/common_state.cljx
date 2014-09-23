(ns grub.common-state
  (:require [grub.diff :as diff]
            [hasch.core :as hasch]))

(def empty-state {:grubs {} :recipes {}})

(def complete-sync-request {:type :complete})
(defn complete-sync-response [state]
  {:type :complete
   :state state})

(defn diff-msg [diff hash shadow-hash]
  {:type :diff
   :diff diff
   :hash hash
   :shadow-hash shadow-hash})

(defn diff-states [shadow state]
  (let [diff (diff/diff-states shadow state)
        hash (hasch/uuid state)
        shadow-hash (hasch/uuid shadow) 
        msg (diff-msg diff hash shadow-hash)]
    msg))