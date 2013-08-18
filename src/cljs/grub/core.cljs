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
  (a/copy-chan state/incoming-view-events view/outgoing-events)
  (a/copy-chan state/incoming-events ws/outgoing-events)
  (a/copy-chan ws/incoming-events state/outgoing-events))

(defn init []
  (view/init)
  (ws/connect-to-server)
  (handle-grub-events))

(init)
