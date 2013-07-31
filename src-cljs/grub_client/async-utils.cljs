(ns grub-client.async-utils
  (:require [cljs.core.async :as async :refer [<! >! chan put! alts!]])
  (:require-macros [cljs.core.async.macros :as m :refer [go]]
                   [grub-client.macros :refer [go-loop]]))

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
     (go (while true
           (let [[x] (alts! ins)]
             (>! c x))))
     c))

(defn copy-chan
  ([c]
    (first (fan-out c 1)))
  ([out c]
    (first (fan-out c [out]))))

(defn event-chan
  ([type] (event-chan js/window type))
  ([el type] (event-chan (chan) el type))
  ([c el type]
    (let [writer #(put! c %)]
      (.addEventListener el type writer)
      {:chan c
       :unsubscribe #(.removeEventListener el type writer)})))

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
     
