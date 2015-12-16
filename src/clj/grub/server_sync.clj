(ns grub.server-sync
  (:require [grub.db :as db]
            [grub.diff :as diff]
            [grub.event :as event]
            [grub.util :as util]
            [datomic.api :as d]
            [clojure.core.async :as a :refer [<! >! chan go]]))

(def DEBUG false)

(defn make-printer []
  (let [print-chan (chan)]
    (go (loop []
          (println (<! print-chan))
          (recur)))
    print-chan))

(def debug-print
  (let [printer (make-printer)]
    (fn [msg] (when DEBUG (a/put! printer msg)))))

(defn rand-id [] (util/rand-str 10))

(defn start-sync! [list-name to-client diffs full-sync-reqs db-conn report-queue]
  (let [id (rand-id)]
    (go (loop [client-tag nil
               awaiting-state? true]
          (let [channels (if awaiting-state? [full-sync-reqs diffs] [full-sync-reqs diffs report-queue])
                [event ch] (a/alts! channels)]
            (when-not (nil? event)
              (condp = ch
                diffs
                (let [{:keys [diff shadow-tag tag]} event
                      client-shadow-db (d/as-of (d/db db-conn) shadow-tag)
                      client-shadow-state (db/get-current-db-state client-shadow-db list-name)
                      a (debug-print (str id " " "Got diff from client: " shadow-tag " -> " tag))
                      {:keys [db-after]} (db/patch-state! db-conn list-name diff)
                      new-tag (d/basis-t db-after)
                      new-state (assoc (db/get-current-db-state db-after list-name) :tag new-tag)
                      new-shadow (assoc (diff/patch-state client-shadow-state diff) :tag tag)
                      return-diff (event/diff-msg new-shadow new-state)]
                  (debug-print (str id " " "Send diff to client : " tag " -> " new-tag))
                  (>! to-client return-diff)
                  (recur new-tag false))

                full-sync-reqs
                (let [current-db (d/db db-conn)
                      current-tag (d/basis-t current-db)
                      current-state (assoc (db/get-current-db-state current-db list-name) :tag current-tag)]
                  (debug-print (str id " " "Full sync client to : " current-tag))
                  (>! to-client (event/full-sync current-state))
                  (recur current-tag false))

                report-queue
                (let [tx-report event
                      new-db-state (:db-after tx-report)
                      new-tag (d/basis-t new-db-state)]
                  (if (>= client-tag new-tag)
                    ;; Already up to date, do nothing
                    (do (debug-print (str id " " "Got report " new-tag " but client already up-to-date at " new-tag))
                        (recur client-tag false))

                    ;; Changes, send them down
                    (let [new-state (assoc (db/get-current-db-state new-db-state list-name) :tag new-tag)
                          client-db (d/as-of (d/db db-conn) client-tag)
                          client-state (assoc (db/get-current-db-state client-db list-name) :tag client-tag)]
                      (debug-print (str id " " "Got report, send diff to client: " client-tag " -> " new-tag))
                      (>! to-client (event/diff-msg client-state new-state))
                      (recur new-tag false))))

                (throw (Throwable. "Bug: Received an event on unknown channel")))))))))
