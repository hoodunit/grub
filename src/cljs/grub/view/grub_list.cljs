(ns grub.view.grub-list
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! chan]]
            [grub.view.dom :as dom]
            [grub.view.grub :as grub-view])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn get-grub-ingredient [grub]
  (when-not (nil? (:grub grub))
    (let [text (clojure.string/lower-case (:grub grub))
          match (re-find #"[a-z]{3}.*$" text)]
      match)))

(defn sort-grubs [grubs]
  (sort-by (juxt :completed get-grub-ingredient :grub) (vals grubs)))

(defn add-grub [owner grubs new-grub-text]
  (when (not (empty? new-grub-text))
    (let [new-grub (grub-view/new-grub new-grub-text)]
      (om/set-state! owner :new-grub-text "")
      (om/transact! grubs #(assoc % (:id new-grub) new-grub)))))

(defn view [grubs owner]
  (reify
    om/IInitState
    (init-state [_]
      {:new-grub-text ""
       :remove-grub-ch (chan)})
    om/IRenderState
    (render-state [this {:keys [new-grub-text remove-grub-ch] :as state}]
      (let [add (om/get-shared owner :grub-add)]
        (html 
         [:div 
          [:h3 "Grub List"]
          [:div.input-group.add-grub-input-form
           [:span.input-group-btn
            [:input.form-control#add-grub-input 
             {:type "text" 
              :placeholder "What do you need?"
              :value new-grub-text
              :on-key-up #(when (dom/enter-pressed? %)
                            (add-grub owner grubs new-grub-text))
              :on-change #(om/set-state! owner :new-grub-text (dom/event-val %))}]]
           [:button.btn.btn-primary 
            {:id "add-grub-btn" 
             :type "button"
             :on-click #(add-grub owner grubs new-grub-text)}
            [:span.glyphicon.glyphicon-plus]]]
          [:ul#grub-list.list-group
           (for [grub (sort-grubs grubs)]
             (om/build grub-view/view grub {:key :id :opts {:remove-ch remove-grub-ch}}))]
          [:button.btn.pull-right 
           {:id "clear-all-btn" 
            :class (when (empty? grubs) "hidden")
            :type "button"
            :on-click #(om/update! grubs {})}
           "Clear all"]])))
    om/IWillMount
    (will-mount [_]
      (let [add-grubs-ch (om/get-shared owner :add-grubs-ch)
            remove-grub-ch (om/get-state owner :remove-grub-ch)]
        (go-loop []
                 (let [grubs-map (<! add-grubs-ch)]
                   (when-not (nil? grubs-map)
                     (om/transact! grubs #(merge % grubs-map))
                     (recur))))
        (go-loop []
                 (let [id (<! remove-grub-ch)]
                   (when-not (nil? id)
                     (om/transact! grubs #(dissoc % id))
                     (recur))))))))
