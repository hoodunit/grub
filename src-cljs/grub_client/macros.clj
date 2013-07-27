(ns grub-client.macros)

(defmacro log [& args]
  `(.log js/console ~@args))
