(ns grub.core
  (:require [grub.async-utils :as a]
            [grub.view :as view]
            [grub.websocket :as ws]
            [grub.state :as state]
            [cljs.core.async :refer [<! >! >!! chan close! timeout]]
            [cljs.reader])
  (:require-macros [grub.macros :refer [log logs go-loop]]
                   [cljs.core.async.macros :refer [go]]))

(defn handle-grub-events []
  (a/fan-out view/outgoing-events [state/incoming-events ws/incoming-events])
  (a/copy state/incoming-events ws/outgoing-events)
  (a/copy ws/incoming-events state/outgoing-events))

(defn init []
  (ws/connect-to-server)
  (state/init)
  (view/init)
  (handle-grub-events))

(init)
