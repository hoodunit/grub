(ns grub.async
  (:require [clojure.core.async :as async :refer [<! >! chan go put! alts!]]))

(defmacro go-loop [& body]
  `(clojure.core.async/go
     (while true
       ~@body)))

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

(defn copy-chan
  ([c]
    (first (fan-out c 1)))
  ([out c]
    (first (fan-out c [out]))))

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
  ([c ins]
     (go-loop
      (let [[x] (alts! ins)]
        (>! c x)))
     c))

(defn map-chan
  ([f source] (map-chan (chan) f source))
  ([c f source]
    (go-loop
      (>! c (f (<! source))))
    c))

(defn filter-chan
  ([f source] (filter-chan (chan) f source))
  ([c f source]
    (go-loop
      (let [v (<! source)]
        (when (f v)
          (>! c v))))
    c))

(defn do-chan! [f source]
  (go-loop
   (let [v (<! source)]
     (f v))))

(defn do-chan [f source]
  (let [out (chan)]
    (go-loop
     (let [v (<! source)]
       (>! out v)
       (f v)))
    out))
     
