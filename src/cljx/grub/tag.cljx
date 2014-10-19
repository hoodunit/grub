(ns grub.tag)

(defn new-tag []
  #+clj (java.util.Date.)
  #+cljs (js/Date.))

(defn oldest-tag []
  #+clj (java.util.Date. 0)
  #+cljs (js/Date. 0))
