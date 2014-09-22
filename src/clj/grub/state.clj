(ns grub.state
  (:require [grub.diff :as diff]
            [grub.util :as util]
            [grub.common-state :as cs]
            [clojure.core.async :as a :refer [<! >! chan go]]
            [hasch.core :as hasch]))

(defn initial-state [grubs recipes]
  [{:grubs (util/map-by-key :id grubs)
    :recipes (util/map-by-key :id recipes)}])

(def states (ref []))

(defn get-history-state [states hash]
  (:state (first (filter #(= (:hash %) hash) states))))

(defn add-history-state [states new-state]
  (let [{:keys [state hash]} (first states)
        new-hash (hasch/uuid new-state)]
    (if (= hash new-hash)
      states
      (conj states {:hash hash :state state}))))

(defn receive-diff [states diff shadow-hash]
  (let [state (:state (first states))
        shadow (get-history-state states shadow-hash)]
    (if shadow
      {:new-state (diff/patch-state state diff)
       :new-shadow (diff/patch-state shadow diff)
       :full-sync? false}
      {:new-state state
       :new-shadow state
       :full-sync? true})))

(defn apply-diff? [states diff shadow-hash]
  (get-history-state states shadow-hash))

(def to-db (atom nil))

(defn make-client [in client-state states]
  (let [out (chan)
        full-sync! (fn [] (let [new-client (dosync (ref-set client-state @states))]
                            (a/put! out (cs/complete-sync-response new-client))))]
    (a/go-loop 
       []
       (when-let [{:keys [type diff shadow-hash state]} (<! in)]
         (condp = type 
           :new-state (a/put! out (diff/diff-states @client-state (first @states)))
           :diff (dosync
                  (let [state (:state (first @states))
                        shadow (get-history-state states shadow-hash)]
                    (if shadow
                      (do (alter states add-history-state (diff/patch-state state diff))
                          (alter client-state diff/patch-state shadow diff))
                      (full-sync!))))
           :full-sync (full-sync!)
           nil)
         (recur)))
    out))

(defn sync-new-client! [>client <client]
  (let [client-id (java.util.UUID/randomUUID)
        state-changes (chan)
        events (chan)
        client-state (ref cs/empty-state)]
    (add-watch states client-id (fn [_ _ _ [state _]] (a/put! state-changes state)))
    (a/pipe (a/merge <client 
                     (a/map< (fn [s] {:type :new-state :state s}) state-changes)) 
            events)
    (let [out (make-client events client-id client-state states)]
      (a/go-loop [] (if-let [v (<! out)]
                      (do (>! >client v)
                          (recur))
                      (remove-watch states client-id))))

    ;; (let [full-sync! (fn [] (let [new-client (dosync (ref-set client-state @states))]
    ;;                           (a/put! >client (cs/complete-sync-response new-client))))]
    ;;   (a/go-loop 
    ;;    []
    ;;    (if-let [{:keys [type diff shadow-hash state]} (<! events)]
    ;;      (condp = type 
    ;;        :new-state (a/put! >client (diff/diff-states @client-state (first @states)))
    ;;        :diff (dosync
    ;;               (let [state (:state (first @states))
    ;;                     shadow (get-history-state states shadow-hash)]
    ;;                 (if shadow
    ;;                   (do (alter states add-history-state (diff/patch-state state diff))
    ;;                       (alter client-state diff/patch-state shadow diff))
    ;;                   (full-sync!))))
    ;;        :full-sync (full-sync!))
    ;;      (remove-watch states client-id))))
    ))

;; (defn sync-new-client! [to from]
;;   (let [client-id (java.util.UUID/randomUUID)
;;         state-changes (chan)]
;;     (add-watch states client-id (fn [_ _ _ [current-state _]] 
;;                                   (put! state-changes current-state)))
;;     (a/go-loop [client-state cs/empty-state]
;;                (if-let [[{:keys [type] :as msg} c] (<! from)]
;;                  (do (condp = type
;;                        :diff 
;;                        (if (= (hasch/uuid @client-state) shadow-hash)
;;                          ;; We have what they thought we had
;;                          ;; Apply changes normally
;;                          (let [new-shadow (swap! client-state diff/patch-state diff)]
;;                            (log "Hash matched state, apply changes")
;;                            (if (= (hasch/uuid new-shadow) hash)
;;                              (let [new-state (swap! state diff/patch-state diff)]
;;                                (>! @to-db diff))
;;                              (do (log "Applying diff failed --> full sync")
;;                                  (let [sync-state @state]
;;                                    (reset! client-state sync-state)
;;                                    (a/put! to (cs/complete-sync-response sync-state))))))
;;                          ;; We have something different than they thought
;;                          ;; Check history
;;                          (do
;;                              (log "Hash check failed --> Reset from history")
;;                              (if-let [history-state (get-history-state shadow-hash)]
;;                                ;; Found what they thought we had in history, 
;;                                ;; reset client state to this and continue as normal
;;                                (do 
;;                                  (reset! client-state history-state)
;;                                  (let [new-shadow (swap! client-state diff/patch-state diff)]
;;                                    (if (= (hasch/uuid new-shadow) hash)
;;                                      (let [new-state (swap! state diff/patch-state diff)]
;;                                        (>! @to-db diff))
;;                                      (do (log "Applying diff failed --> full sync")
;;                                          (let [sync-state @state]
;;                                            (reset! client-state sync-state)
;;                                            (a/put! to (cs/complete-sync-response sync-state)))))))
;;                                ;; Not found in history, do complete sync
;;                                (do (log "Hash check failed, not in history --> full sync")
;;                                    (let [sync-state @state]
;;                                      (reset! client-state sync-state)
;;                                      (a/put! to (cs/complete-sync-response sync-state)))))))
;;                        :complete (let [new-state (reset! client-state @state)]
;;                                    (log "full sync")
;;                                    (a/put! to (cs/complete-sync-response new-state)))
;;                        (log "Invalid msg:" msg))
;;                      (recur))
;;                  (remove-watch state client-id)))))

(defn init [_to-db grubs recipes]
  (reset! states (initial-state grubs recipes))
  (reset! to-db _to-db))
