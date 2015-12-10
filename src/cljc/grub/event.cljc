(ns grub.event
  (:require [grub.diff :as diff]))

(defn full-sync-request []
  {:type :full-sync-request})

(defn diff-msg [shadow state]
  (let [diff (diff/diff-states shadow state)]
    {:type :diff
     :diff diff
     :tag (:tag state)
     :shadow-tag (:tag shadow)}))

(defn connected []
  {:type :connected})

(defn full-sync [state]
  {:type :full-sync
   :full-state state})
