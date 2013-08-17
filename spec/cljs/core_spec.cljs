(ns grub.core-spec 
  (:require-macros [specljs.core :refer [describe it should=]])
  (:require [specljs.core]
            [grub.core]))

(describe "A ClojureScript test"
  (it "fails. Fix it!"
    (should= 0 1)))

