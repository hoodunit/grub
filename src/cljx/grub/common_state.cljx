(ns grub.common-state
  (:require [grub.sync :as sync]
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
  (let [diff (sync/diff-states shadow state)
        ;; what we now have
        hash (hasch/uuid state)

        ;; what we had/what you used to have
        ;; should match what they think we have
        shadow-hash (hasch/uuid shadow) 
        msg (diff-msg diff hash shadow-hash)]
    msg
                                        ;(logs "Sync because:")
                                        ;(logs "Local = " state)
                                        ;(logs "Remote = " shadow)
                                        ;(logs "Diff:" diff)
                                        ;(logs "Send" shadow-hash "->" hash)
    ))
