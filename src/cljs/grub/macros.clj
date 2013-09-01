(ns grub.macros)

(defmacro log [& args]
  `(.log js/console ~@args))

(defmacro logs [& args]
  (let [strings (map (fn [a] `(pr-str ~a)) args)]
    `(.log js/console ~@strings)))

(defmacro go-loop [& body]
  `(cljs.core.async.macros/go
     (while true
       ~@body)))

(defmacro do-chan [[binding chan] & body]
  `(let [chan# ~chan]
     (cljs.core.async.macros/go
       (loop []
         (if-let [~binding (cljs.core.async/<! chan#)]
           (do
             ~@body
             (recur))
           :done)))))
