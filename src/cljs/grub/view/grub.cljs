(ns grub.view.grub
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.dom :as dom])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn add-event [grub]
  {:event :add-grub 
   :id (str "grub-" (.now js/Date))
   :grub grub
   :completed false})

(defn edit-event [id grub]
  {:event :update-grub
   :id id
   :grub grub})

(defn complete-event [{:keys [id completed]}]
  {:event :update-grub
   :id id
   :completed (not completed)})

(defn grub-view [{:keys [id grub completed] :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:edit-state :waiting
         :>local-events publisher
         :<local-events (a/pub publisher identity)
         :grub grub}))

    om/IRenderState
    (render-state [_ {:keys [edit-state >local-events] :as state}]
      (html
       [:li.list-group-item.grub-item 
        {:class [(when completed "completed")
                 (when (= edit-state :pressed) "grub-active")
                 (when (= edit-state :editing) "edit")]
         :on-mouse-down #(put! >local-events :mouse-down) 
         :on-mouse-up #(put! >local-events :mouse-up) 
         :on-mouse-leave #(put! >local-events :mouse-leave)
         :on-click #(when (#{:waiting :pressed} edit-state)
                      (put! (om/get-shared owner :grub-update) (complete-event @props)))} 
        [:span.grub-static
         (if completed
           [:span.glyphicon.glyphicon-check]
           [:span.glyphicon.glyphicon-unchecked])
         [:span.grub-text grub]]
        [:input.grub-input 
         {:type "text" 
          :value (:grub state)
          :on-change #(om/set-state! owner :grub (.. % -target -value))
          :on-key-up #(when (dom/enter-pressed? %) (put! >local-events :enter))}]]))

    om/IWillMount
    (will-mount [_] 
      (let [<local-events (om/get-state owner :<local-events)
            <events (om/get-shared owner :<events)
            subscriber (chan)]
        (go-loop []
                 (om/set-state! owner :edit-state :waiting)
                 (let [subscriber (chan)]
                   (a/sub <local-events :mouse-down subscriber)
                   (<! subscriber)
                   (a/unsub <local-events :mouse-down subscriber)
                   (a/close! subscriber))
                 (om/set-state! owner :edit-state :pressed)
                 (a/sub <local-events :mouse-leave subscriber)
                 (a/sub <local-events :mouse-up subscriber)
                 (let [timeout (a/timeout 500)
                       [event c] (a/alts! [timeout subscriber])]
                   (a/unsub <local-events :mouse-leave subscriber)
                   (a/unsub <local-events :mouse-up subscriber)
                   (if (= c timeout)
                     (do (om/set-state! owner :edit-state :editing)
                         (a/sub <events :body-mousedown subscriber)
                         (a/sub <local-events :enter subscriber)
                         (loop []
                           (let [event (<! subscriber)]
                             (when (and (= (:type event) :body-mousedown)
                                        (dom/click-on-self? (:event event) (om/get-node owner)))
                               (recur))))
                         (a/unsub <events :body-mousedown subscriber)
                         (a/unsub <local-events :enter subscriber)
                         (put! (om/get-shared owner :grub-update)
                               (edit-event id (om/get-state owner :grub))))
                     (om/set-state! owner :edit-state :waiting)))
                 (recur))))))

(defn get-grub-ingredient [grub]
  (when-not (nil? (:grub grub))
    (let [text (clojure.string/lower-case (:grub grub))
          match (re-find #"[a-z]{3}.*$" text)]
      match)))

(defn sort-grubs [grubs]
  (sort-by (juxt :completed get-grub-ingredient :grub) (vals grubs)))

(defn add-grub [add new-grub owner]
  (when (not (empty? new-grub))
    (om/set-state! owner :new-grub "")
    (put! add (add-event new-grub))))

(defn update-new-grub-value [event owner]
  (om/set-state! owner :new-grub (.. event -target -value)))

(defn grubs-view [props owner]
  (reify
    om/IInitState
    (init-state [_]
      {:new-grub ""})
    om/IRenderState
    (render-state [this {:keys [new-grub] :as state}]
      (let [add (om/get-shared owner :grub-add)]
        (html 
         [:div 
          [:h3 "Grub List"]
          [:div.input-group.add-grub-input-form
           [:span.input-group-btn
            [:input.form-control#add-grub-input 
             {:type "text" 
              :placeholder "2 grubs"
              :value new-grub
              :on-key-up #(when (dom/enter-pressed? %)
                            (add-grub add new-grub owner))
              :on-change #(update-new-grub-value % owner)}]]
           [:button.btn.btn-primary 
            {:id "add-grub-btn" 
             :type "button"
             :on-click #(add-grub add new-grub owner)}
            "Add"]]
          [:ul#grub-list.list-group
           (for [grub (sort-grubs props)]
             (om/build grub-view grub {:key :id}))]
          [:button.btn.pull-right 
           {:id "clear-all-btn" 
            :class (when (empty? props) "hidden")
            :type "button"
            :on-click #(put! (om/get-shared owner :grub-clear-all)
                             {:event :clear-all-grubs})}
           "Clear all"]])))))
