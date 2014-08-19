(ns grub.state
  (:require [grub.sync :as sync]
            [grub.util :as util]
            [grub.common-state :as cs]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hasch.core :as hasch]))

(def state (atom cs/empty-state))
(def to-db (atom nil))
(def to-all (chan))
(def from-all (a/mult to-all))

(defn initial-state [grubs recipes]
  {:grubs (util/map-by-key :id grubs)
   :recipes (util/map-by-key :id recipes)})

(defn sync-remote-changes [to-client state* server-shadow]
  (let [server-shadow* @server-shadow]
    (when (not= state* server-shadow*)
      (let [diff (sync/diff-states server-shadow* state*)
            msg {:type :diff
                 :diff diff
                 :hash (hasch/uuid state*)
                 :shadow-hash (hasch/uuid server-shadow*)}]
        (println "Sync because:")
        (println "Server = " state*)
        (println "Client = " server-shadow*)
        (println "Diff:" diff)
        (println "Send" (hasch/uuid server-shadow*) "->" (hasch/uuid state*))
        (a/put! to-client msg)
        ;; TODO: only reset server shadow if send succeeds
        (reset! server-shadow state*)))))

(defn sync-new-client! [to from]
  (let [client-id (java.util.UUID/randomUUID)
        server-shadow (atom cs/empty-state)]
    (add-watch state client-id (fn [k ref old new] 
                                 (sync-remote-changes to new server-shadow)))
    (a/go-loop []
               (let [{:keys [type diff hash shadow-hash] :as msg} (<! from)]
                 (if (not (nil? msg))
                   (do
                     (condp = type
                       :diff (do
                               (println "Received client diff:" shadow-hash "->" hash)
                               (println "Before shadow:" (hasch/uuid @server-shadow) @server-shadow)
                               (if (= (hasch/uuid @server-shadow) shadow-hash)
                                 (println "Before hash check: good")
                                 (println "Before hash check: FAIL"))
                               (let [new-shadow (swap! server-shadow #(sync/patch-state % diff))
                                     new-state (swap! state #(sync/patch-state % diff))]
                                 ;; TODO: check if hashes match
                                 (println "After shadow:" (hasch/uuid new-shadow) new-shadow)
                                 (if (= (hasch/uuid new-shadow) hash)
                                   (println "After hash check: good")
                                   (println "After hash check: FAIL"))
                                 (>! @to-db diff)))
                       :complete (let [new-state (reset! server-shadow @state)]
                                   (a/put! to (cs/complete-sync-response new-state)))
                       (println "Invalid msg:" msg))
                     (recur))
                   (remove-watch state client-id))))))

(defn init [_to-db grubs recipes]
  (reset! state (initial-state grubs recipes))
  (reset! to-db _to-db))
