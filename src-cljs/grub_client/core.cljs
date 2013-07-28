(ns grub-client.core
  (:require [dommy.core :as dommy]
            [cljs.core.async :as async :refer [<! >! chan close! timeout]])
  (:require-macros [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :as m :refer [go alt! alts!]]
                   [grub-client.macros :refer [log]]))

(deftemplate grub-template [grub]
  [:tr 
   [:td 
    [:div.checkbox [:label [:input {:type "checkbox"}] grub]]]])

(def add-grub-text 
  (node [:input.form-control {:type "text" :placeholder "2 grubs"}]))

(def add-grub-btn 
  (node [:button.btn.btn-default {:type "button"} "Add"]))

(deftemplate main-template [grubs]
  [:div.container
   [:div.row.show-grid
    [:div.col-lg-4]
    [:div.col-lg-4
     [:h3 "Grub List"]
     [:div.input-group
      add-grub-text
      [:span.input-group-btn
       add-grub-btn]]
     [:table.table.table-condensed
      [:tbody#grubList]]]
    [:div.col-lg-4]]])

(defn render-body []
  (dommy/prepend! (sel1 :body) (main-template)))

(defn push-new-grub [channel]
  (let [new-grub (dommy/value add-grub-text)]
    (dommy/set-value! add-grub-text "")
    (go (>! channel new-grub))))

(defn put-grubs-from-clicks [channel]
  (dommy/listen! add-grub-btn :click #(push-new-grub channel)))

(defn put-grubs-if-enter-pressed [channel event]
  (when (= (.-keyIdentifier event) "Enter")
                (push-new-grub channel)))

(defn put-grubs-from-enter [channel]
  (dommy/listen! add-grub-text 
                 :keyup 
                 (partial put-grubs-if-enter-pressed channel)))

(defn get-added-grubs []
  (let [out (chan)]
    (put-grubs-from-clicks out)
    (put-grubs-from-enter out)
    out))

(defn append-new-grub [grub]
  (dommy/append! (sel1 :#grubList) (grub-template grub)))

(defn append-new-grubs [chan]
  (go (while true
        (let [grub (<! chan)]
          (append-new-grub grub)))))

(defn add-grubs-to-list [in]
  (go (while true 
        (let [new-grub (<! in)]
          (append-new-grub new-grub)))))

(defn filter-empty-grubs [in]
  (let [out (chan)]
    (go (while true
          (let [grub (<! in)]
            (when-not (empty? grub) (>! out grub)))))
    out))

(def websocket* (atom nil))

(defn push-grubs-to-server [chan]
  (let [websocket (js/WebSocket. "ws://localhost:3000/ws")]
    (aset websocket "onmessage" (fn [event] 
                                  (let [grub (.-data event)]
                                    (log "Received grub:" grub)
                                    (append-new-grub grub))))
    (go (while true
          (let [grub (<! chan)]
            (.send websocket grub))))))

(defn fan-out [in num-chans]
  (let [out-channels (repeatedly num-chans chan)]
   (go (while true
             (let [x (<! in)]
               (doseq [out out-channels]
                 (>! out x)))))
   out-channels))

(defn add-new-grubs-as-they-come []
  (let [added-grubs (get-added-grubs)
        filtered-grubs (filter-empty-grubs added-grubs)
        out-channels (fan-out filtered-grubs 2)]
    (append-new-grubs (first out-channels))
    (push-grubs-to-server (second out-channels))))

(defn init []
  (render-body)
  (add-new-grubs-as-they-come))

(init)
