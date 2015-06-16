(ns grub.core
  (:require [grub.state :as state]
            [grub.sync :as sync]
            [grub.websocket :as websocket]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [grub.macros :refer [log logs]]))

(def system
  {:pending-msg (atom nil)
   :ws (atom nil)
   :channels {:local-states (chan)
              :remote-states (chan)
              :to-remote (chan)
              :from-remote (chan)}
   :states (atom nil)
   :view-state nil})

(defn start [{:keys [states pending-msg] :as system}]
  (reset! states sync/empty-state)
  (let [new-states (chan)
        render-states (chan)
        >remote (chan)
        events (chan)
        view-state (view/render-app state/empty-state render-states new-states)
        ws (websocket/connect pending-msg >remote events)]
    (sync/sync-client! >remote events new-states states)
    (add-watch states :render (fn [_ _ old new]
                                (when-not (= old new)
                                  (a/put! render-states (state/get-latest new)))))
    (assoc system
      :ws ws
      :channels {:new-states new-states
                 :>remote >remote
                 :events events}
      :states states
      :view-state view-state)))

(defn stop [{:keys [channels ws]} system]
  (doseq [c (vals channels)] (a/close! c))
  (websocket/disconnect ws))

(start system)
