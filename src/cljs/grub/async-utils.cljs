(ns grub.async-utils
  (:refer-clojure :exclude [map filter])
  (:require [cljs.core.async :as async :refer [<! >! chan put! alts! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [grub.macros :refer [do-chan]]))

(defn log [in]
  (let [out (chan)]
    (do-chan [e in]
      (.log js/console e)
      (>! out e))
    out))

(defn put-all! [cs x]
  (doseq [c cs]
    (put! c x)))

(defn fan-out [in cs-or-n]
  (let [cs (if (number? cs-or-n)
             (repeatedly cs-or-n chan)
             cs-or-n)]
    (go (loop []
          (let [x (<! in)]
            (if-not (nil? x)
              (do
                (put-all! cs x)
                (recur))
              :done))))
    cs))

(defn fan-in
  ([ins] (fan-in (chan) ins))
  ([out ins]
    (go (loop [ins (vec ins)]
          (when (> (count ins) 0)
            (let [[x in] (alts! ins)]
              (when x
                (>! out x)
                (recur ins))
              (recur (vec (disj (set ins) in))))))
        (close! out))
    out))

(defn copy
  ([c]
    (first (fan-out c 1)))
  ([out c]
    (first (fan-out c [out]))))

(defn map [f in]
  (let [out (chan)]
    (go (loop []
          (if-let [x (<! in)]
            (do (>! out (f x))
              (recur))
            (close! out))))
    out))

(defn map-filter [f in]
  (let [out (chan)]
    (go (loop []
          (if-let [x (<! in)]
            (do 
              (when-let [val (f x)]
                (>! out val))
              (recur))
            (close! out))))
    out))

(defn filter [pred in]
  (let [out (chan)]
    (go (loop []
          (if-let [x (<! in)]
            (do (when (pred x) (>! out x))
              (recur))
            (close! out))))
    out))

(defn siphon
  ([in] (siphon in []))
  ([in coll]
    (go (loop [coll coll]
          (if-let [v (<! in)]
            (recur (conj coll v))
            coll)))))
     
