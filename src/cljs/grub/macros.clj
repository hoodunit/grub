(ns grub.macros)

(defmacro log [& args]
  `(.log js/console ~@args))

(defmacro logs [& args]
  (let [strings (map (fn [a] `(pr-str ~a)) args)]
    `(.log js/console ~@strings)))


;; Maybe monad
(defmacro and-let* [bindings & body]
  (when (not= (count bindings) 2)
    (throw (IllegalArgumentException.
            "and-let* requires an even number of forms in binding vector")))
  (let [form (bindings 0)
        tst (bindings 1)]
    `(let [temp# ~tst]
        (when temp#
          (let [~form temp#]
            ~@body)))))

(defmacro and-let [bindings & body]
  (when (not (even? (count bindings)))
    (throw (IllegalArgumentException.
            "and-let requires an even number of forms in binding vector")))
  (let [whenlets (reduce (fn [sexpr bind]
                           (let [form (first bind)
                                 tst (second bind)]
                             (conj sexpr `(and-let* [~form ~tst]))))
                         ()
                         (partition 2 bindings))
        body (cons 'do body)]
    `(->> ~body ~@whenlets)))
