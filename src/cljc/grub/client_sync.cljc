(ns grub.client-sync
  (:require [grub.diff :as diff]
            [grub.state :as state]
    #?(:cljs [cljs.core.async :as a :refer [<! >! chan]]
       :clj [clojure.core.async :as a :refer [<! >! chan go]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(def DEBUG true)

(def full-sync-request {:type :full-sync-request})

(defn diff-msg [shadow state]
  (let [diff (diff/diff-states shadow state)]
    {:type :diff
     :diff diff
     :tag (:tag state)
     :shadow-tag (:tag shadow)}))

(defn update-states [states diff]
  (let [state (state/get-latest states)
        new-state (diff/patch-state state diff)]
    (state/add states new-state)))

(defn sync-client! [to-server new-ui-states diffs full-syncs ui-state]
  (let [ui-state-buffer (chan (a/sliding-buffer 1))]
    (a/pipe new-ui-states ui-state-buffer)
    (reset! ui-state state/empty-state)
    (go (loop [states (state/new-states @ui-state)
               shadow (state/get-latest states)
               awaiting-ack? false]
          (let [channels (if awaiting-ack? [diffs full-syncs] [diffs full-syncs ui-state-buffer])]
            (let [[val ch] (a/alts! channels)]
              (when DEBUG (println val))
              (condp = ch
                ui-state-buffer (let [new-state val
                                      new-states (state/add states new-state)
                                      latest-state (state/get-latest new-states)]
                                  (>! to-server (diff-msg shadow latest-state))
                                  (recur new-states shadow true))
                full-syncs (let [full-state (:full-state val)
                                 new-states (state/new-states full-state)
                                 latest-state (state/get-latest new-states)]
                             (reset! ui-state full-state)
                             (recur new-states latest-state false))
                diffs (let [{:keys [diff shadow-tag tag]} val
                            history-shadow (state/get-tagged states shadow-tag)]
                        (if history-shadow
                          (let [new-states (update-states states diff)
                                new-shadow (assoc (diff/patch-state shadow diff) :tag tag)]
                            (recur new-states new-shadow false))
                          (do (>! to-server full-sync-request)
                              (recur states shadow true))))
                (println "An error occurred, received value on unknown channel"))))))
    (a/put! to-server full-sync-request)))
