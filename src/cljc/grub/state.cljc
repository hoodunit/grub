(ns grub.state)

(def empty-state {:tag 0 :grubs {} :recipes {}})

(defn state= [a b]
  (= (dissoc a :tag) (dissoc b :tag)))
