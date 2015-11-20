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
    (go (loop [state (assoc @ui-state :tag 0)
               shadow state
               awaiting-ack? false]
          (let [channels (if awaiting-ack? [diffs full-syncs] [diffs full-syncs ui-state-buffer])]
            (let [[event ch] (a/alts! channels)]
              (when DEBUG (println event))
              (when-not (nil? event)
                (condp = ch
                  ui-state-buffer (let [new-state (assoc event :tag (inc (:tag state)))]
                                    (println "new-state:\n" new-state)
                                    (>! to-server (diff-msg shadow new-state))
                                    (recur new-state shadow true))
                  full-syncs (let [{:keys [full-state tag]} event
                                   new-tag (inc (:tag state))
                                   new-state (assoc full-state :tag new-tag)]
                               (reset! ui-state full-state)
                               (recur new-state (assoc full-state :tag tag) false))
                  diffs (let [{:keys [diff shadow-tag tag]} event]
                          (cond (< shadow-tag (:tag state)) (recur state shadow false)
                                (= shadow-tag (:tag state))
                                (let [new-shadow (assoc (diff/patch-state state diff) :tag tag)
                                      new-state (assoc (swap! ui-state diff/patch-state diff) :tag (inc (:tag state)))]
                                  (if (state/state= new-shadow new-state)
                                    (recur new-state new-shadow false)
                                    (do (>! to-server (diff-msg new-shadow new-state))
                                        (recur new-state new-shadow true))))
                                :else (do (>! to-server (full-sync-request (:tag shadow)))
                                          (recur state shadow true))))
                  (println "An error occurred, received value on unknown channel")))))))
    (a/put! to-server full-sync-request)))
