(ns grub.view.dom)

(defn enter-pressed? [event]
  (let [enter-keycode 13]
    (= (.-which event) enter-keycode)))

(defn click-on-self? [event node]
  (.contains node (.-target event)))

(defn on-body-mousedown [f]
  (let [body-elem (aget (.getElementsByTagName js/document "body") 0)]
    (.addEventListener body-elem "mousedown" f)))

(defn event-val [event]
  (.. event -target -value))
