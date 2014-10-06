(ns grub.message)

(def full-sync-request {:type :full-sync})

(defn full-sync [state]
  {:type :full-sync
   :state state})

(defn diff-msg [diff hash]
  {:type :diff
   :diff diff
   :hash hash})
