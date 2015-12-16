(ns grub.core
  (:require [grub.state :as state]
            [grub.client-sync :as sync]
            [grub.websocket :as websocket]
            [grub.view.app :as view]
            [cljs.core.async :as a :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]] ))

(defn list-name-from-url []
  (last (clojure.string/split (.-location js/window) #"/")))

(defn save-list-name-from-url []
  (set! (.-cookie js/document) (str "list=" (list-name-from-url))))

(defn start-app []
  (save-list-name-from-url)
  (let [ui-state (atom state/empty-state)
        from-server (chan)
        to-server (chan)
        new-ui-states (chan)
        diffs (chan)
        full-syncs (chan)
        connected (chan)]
    (sync/start-sync! to-server new-ui-states diffs full-syncs connected ui-state)
    (websocket/connect to-server from-server)
    (view/render-app ui-state new-ui-states)
    (go-loop [](let [event (<! from-server)]
                  (cond
                    (= (:type event) :diff) (do (>! diffs event) (recur))
                    (= (:type event) :full-sync) (do (>! full-syncs event) (recur))
                    (= (:type event) :connected) (do (>! connected event) (recur))
                    (nil? event) nil ;; drop out of loop
                    :else (do (println "Unknown event:" event) (recur)))))))

(enable-console-print!)
(start-app)
