(ns grub-client.macros)

(defmacro log [& args]
  `(.log js/console ~@args))

(defmacro go-loop [& body]
  `(cljs.core.async.macros/go
     (while true
       ~@body)))
