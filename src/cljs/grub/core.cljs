(ns grub.core
  (:require [grub.state :as state]
            [grub.client-sync :as sync]
            [grub.websocket :as websocket]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]] ))

(defn start-app []
  (let [ui-state (atom state/empty-state)
        from-server (chan)
        to-server (chan)
        new-ui-states (chan)
        diffs (chan)
        full-syncs (chan)]
    (sync/sync-client! to-server new-ui-states diffs full-syncs ui-state)
    (websocket/connect to-server from-server)
    (view/render-app ui-state new-ui-states)
    (go-loop [] (let [event (<! from-server)]
                  (cond
                    (nil? event) nil                    ;; drop out of loop
                    (= (:type event) :diff) (do (>! diffs event) (recur))
                    (= (:type event) :full-sync) (do (>! full-syncs event) (recur))
                    :else (do (println "Unknown event:" event) (recur)))))))

(enable-console-print!)
(start-app)
