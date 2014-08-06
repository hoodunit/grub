(ns grub.view.dom)

(defn enter-pressed? [event]
  (let [enter-keycode 13]
    (= (.-which event) enter-keycode)))

(defn click-on-self? [event node]
  (click-on-elem? event node))

(defn click-on-elem? [event node]
  (.contains node (.-target event)))

(defn on-document-mousedown [f]
  (.addEventListener js/document "mousedown" f))

(defn on-window-scroll [f]
  (.addEventListener js/window "scroll" f))

(defn event-val [event]
  (.. event -target -value))
