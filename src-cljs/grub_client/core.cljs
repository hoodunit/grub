(ns grub-client.core
  (:require [dommy.core :as dommy]
            [cljs.core.async :as async :refer [<! >! chan close! timeout]])
  (:require-macros [dommy.macros :refer [deftemplate sel1 node]]
                   [cljs.core.async.macros :as m :refer [go alt!]]
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
      [:tbody#grubList
       (for [grub grubs] (grub-template grub))]]]
    [:div.col-lg-4]]])

(defn render-body [grubs]
  (dommy/prepend! (sel1 :body) (main-template grubs)))

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

(defn add-grubs-to-list [in]
  (go (while true 
        (let [new-grub (<! in)]
          (log "Added grub: " new-grub)
          (append-new-grub new-grub)))))

(defn filter-empty-grubs [in]
  (let [out (chan)]
    (go (while true
          (let [grub (<! in)]
            (when-not (empty? grub) (>! out grub)))))
    out))

(defn add-new-grubs-to-list []
  (let [added-grubs (get-added-grubs)
        filtered-grubs (filter-empty-grubs added-grubs)]
    (add-grubs-to-list filtered-grubs)))

(def test-grubs
  ["8 dl water"
   "8 whole peppercorns"
   "2 bay leaves"
   "1 - 2 (150 g) onions"
   "2 dl cream"
   "1 dl dill"])

(defn init []
  (render-body test-grubs)
  (add-new-grubs-to-list))

(init)
