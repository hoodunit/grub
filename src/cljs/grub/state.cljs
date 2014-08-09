(ns grub.state
  (:require [grub.sync :as sync]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(def app-state (atom {:grubs {}
                      :recipes {}}))

(defn update-state-on-event! [in]
  (let [out (chan)]
    (add-watch app-state :app-state
               (fn [key ref old new]
                 (when-not (= old new)
                   (let [diff (sync/diff-states old new)]
                     (a/put! out diff)))))
    (go-loop [] 
             (when-let [diff (<! in)]
               (swap! app-state #(sync/patch-state % diff))
               (recur)))
    out))
