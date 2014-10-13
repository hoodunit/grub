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
  (let [local-states (chan)
        remote-states (chan)
        to-remote (chan)
        from-remote (chan)
        view-state (view/render-app state/empty-state remote-states local-states)
        ws (websocket/connect (:pending-msg system) to-remote from-remote)
        agent-states (state/sync-client! from-remote to-remote local-states remote-states)]
    (assoc system
      :ws ws
      :channels {:local-states local-states
                 :remote-states remote-states
                 :to-remote to-remote
                 :from-remote from-remote}
      :view-state view-state
      :agent-states agent-states)))

(defn stop [{:keys [channels ws]} system]
  (doseq [c (vals channels)] (a/close! c))
  (websocket/disconnect ws))

(start system)
