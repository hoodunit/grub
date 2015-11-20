(ns grub.server-sync
  (:require [grub.diff :as diff]
            [grub.state :as state]
            [datomic.api :as d]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [grub.db :as db]
            [clojure.pprint :refer [pprint]]))

(defn full-sync [state tag]
  {:type :full-sync
   :full-state state
   :tag tag})

(def empty-state state/empty-state)

(defn diff-msg [shadow state]
  (println "diff-msg")
  (let [diff (diff/diff-states shadow state)]
    {:type :diff
     :diff diff
     :tag (:tag state)
     :shadow-tag (:tag shadow)}))

(defn sync-server! [to-client diffs full-sync-reqs db-conn]
  (go (loop []
        (let [[event ch] (a/alts! [full-sync-reqs diffs])]
          (when-not (nil? event)
            (condp = ch
              diffs
              (let [{:keys [diff shadow-tag tag]} event
                    client-shadow-db (d/as-of (d/db db-conn) shadow-tag)
                    client-shadow-state (db/get-current-db-state client-shadow-db)
                    {:keys [db-after]} (db/patch-state! db-conn diff)
                    new-tag (d/basis-t db-after)
                    new-state (assoc (db/get-current-db-state db-after) :tag new-tag)
                    new-shadow (assoc (diff/patch-state client-shadow-state diff) :tag tag)
                    return-diff (diff-msg new-shadow new-state)]
                (println "************************* as-of:" new-tag)
                (println "client-shadow:" (pprint (dissoc client-shadow-state :recipes)))
                (println "new-state:" (pprint (dissoc new-state :recipes)))
                (println "new-shadow" (pprint (dissoc new-shadow :recipes)))
                ;(println "**************************history-state:" history-state)
                ;(println "**************************new-state:" new-state)
                ;(println "**************************new-shadow:" new-shadow)
                ;(println "return diff:" return-diff)
                (>! to-client return-diff)
                (recur))

              full-sync-reqs
              (do (>! to-client (full-sync (db/get-current-state db-conn) (d/basis-t (d/db db-conn))))
                  (recur))
              (do (println "Unhandled event:" event)
                  (recur))))))))
