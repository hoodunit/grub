(ns grub.macros)

(defmacro log [& args]
  `(.log js/console ~@args))

(defmacro logs [& args]
  (let [strings (map (fn [a] `(pr-str ~a)) args)]
    `(.log js/console ~@strings)))
