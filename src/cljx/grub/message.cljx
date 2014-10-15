(ns grub.message)

(def full-sync-request {:type :full-sync-request})

(defn full-sync [state]
  {:type :full-sync
   :full-state state})

(defn diff-msg [diff hash]
  {:type :diff
   :diff diff
   :hash hash})
