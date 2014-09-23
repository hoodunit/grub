(ns grub.message)

(def full-sync-request {:type :complete})

(defn full-sync [state]
  {:type :complete
   :state state})

(defn diff-msg [diff shadow-hash]
  {:type :diff
   :diff diff
   :shadow-hash shadow-hash})
