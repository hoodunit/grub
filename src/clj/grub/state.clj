(ns grub.state
  (:require [grub.sync :as sync]
            [grub.util :as util]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hasch.core :as hasch]))

(def empty-state
  {:grubs {}
   :recipes {}})

(def state (atom empty-state))
(def to-db (atom nil))
(def to-all (chan))
(def from-all (a/mult to-all))

(defn get-initial-state [grubs recipes]
  {:grubs (util/map-by-key :id grubs)
   :recipes (util/map-by-key :id recipes)})

(defn sync-remote-changes [to-client state* server-shadow]
  (let [server-shadow* @server-shadow]
    (when (not= state* server-shadow*)
      (let [diff (sync/diff-states server-shadow* state*)
            msg {:diff diff
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

(defn add-client! [to from]
  (let [client-id (java.util.UUID/randomUUID)
        server-shadow (atom empty-state)]
    (add-watch state client-id (fn [k ref old new] 
                                 (sync-remote-changes to new server-shadow)))
    (a/go-loop []
               (if-let [{:keys [diff hash shadow-hash]} (<! from)]
                 (do
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
                     (>! @to-db diff)
                     (recur)))
                 (remove-watch state client-id)))))

(defn init [_to-db grubs recipes]
  (reset! state (get-initial-state grubs recipes))
  (reset! to-db _to-db))
