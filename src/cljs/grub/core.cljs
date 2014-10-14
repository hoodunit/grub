(ns grub.core
  (:require [grub.state :as state]
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
   :view-state nil})

(defn start [system]
  (let [new-states (chan)
        >remote (chan)
        events (chan)
        state (view/render-app state/empty-state new-states)
        ws (websocket/connect (:pending-msg system) >remote events)
        agent-states (state/sync-client! >remote events new-states state)]
    (assoc system
      :ws ws
      :channels {:new-states new-states
                 :>remote >remote
                 :events events}
      :state state)))

(defn stop [{:keys [channels ws]} system]
  (doseq [c (vals channels)] (a/close! c))
  (websocket/disconnect ws))

(start system)
