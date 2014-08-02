(ns grub.view.dom)

(defn enter-pressed? [event]
  (let [enter-keycode 13]
    (= (.-which event) enter-keycode)))

(defn click-on-self? [event node]
  (.contains node (.-target event)))

(defn on-document-mousedown [f]
  (.addEventListener js/document "mousedown" f))

(defn event-val [event]
  (.. event -target -value))
