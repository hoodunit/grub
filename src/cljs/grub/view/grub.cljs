(ns grub.view.grub
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn enter-pressed? [event]
  (let [enter-keycode 13]
    (= (.-which event) enter-keycode)))

(defn complete-event [{:keys [id completed]}]
  {:event :update-grub
   :id id
   :completed (not completed)})

(defn click-on-self? [{:keys [event]} node]
  (or (= (.-target event) node) (.contains node (.-target event))))

(defn edit-event [id grub]
  {:event :update-grub
   :id id
   :grub grub})

(defn grub-view [{:keys [id grub completed] :as grub-state} owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:edit-state :waiting
         :events-in publisher
         :events (a/pub publisher identity)
         :grub grub}))

    om/IRenderState
    (render-state [_ {:keys [edit-state events-in events pressed] :as state}]
      (logs id edit-state)
      (html
       [:li.list-group-item.grub-item 
        {:id id
         :class [(when completed "completed")
                 (when (= edit-state :pressed) "grub-active")
                 (when (= edit-state :editing) "edit")]
         :on-mouse-down #(put! events-in :mouse-down) 
         :on-mouse-up #(put! events-in :mouse-up) 
         :on-mouse-leave #(put! events-in :mouse-leave)
         :on-click #(when (#{:waiting :pressed} edit-state)
                      (put! (om/get-shared owner :update) (complete-event @grub-state)))} 
        [:span.grub-static
         (if completed
           [:span.glyphicon.glyphicon-check]
           [:span.glyphicon.glyphicon-unchecked])
         [:span.grub-text grub]]
        [:input.grub-input 
         {:type "text" 
          :value (:grub state)
          :on-change #(om/set-state! owner :grub (.. % -target -value))
          :on-key-up #(when (enter-pressed? %) (put! events-in :enter))}]]))

    om/IWillMount
    (will-mount [_] 
      (let [local-events (om/get-state owner :events)
            events (om/get-shared owner :events)
            subscriber (chan)]
        (go-loop []
                 (om/set-state! owner :edit-state :waiting)
                 (a/sub local-events :mouse-down subscriber)
                 (<! subscriber)
                 (a/unsub local-events :mouse-down subscriber)
                 (om/set-state! owner :edit-state :pressed)
                 (a/sub local-events :mouse-leave subscriber)
                 (a/sub local-events :mouse-up subscriber)
                 (let [timeout (a/timeout 500)
                       [event c] (a/alts! [timeout subscriber])]
                   (a/unsub local-events :mouse-leave subscriber)
                   (a/unsub local-events :mouse-up subscriber)
                   (if (= c timeout)
                     (do (om/set-state! owner :edit-state :editing)
                         (a/sub events :body-mousedown subscriber)
                         (a/sub local-events :enter subscriber)
                         (loop []
                           (let [event (<! subscriber)]
                             (when (and (= (:type event) :body-mousedown)
                                        (click-on-self? event (om/get-node owner)))
                               (recur))))
                         (a/unsub events :body-mousedown subscriber)
                         (a/unsub local-events :enter subscriber)
                         (put! (om/get-shared owner :update) (edit-event id (om/get-state owner :grub))))
                     (om/set-state! owner :edit-state :waiting)))
                 (recur))))))

(defn get-grub-ingredient [grub]
  (when-not (nil? (:grub grub))
    (let [text (clojure.string/lower-case (:grub grub))
          match (re-find #"[a-z]{3}.*$" text)]
      match)))

(defn sort-grubs [grubs]
  (sort-by (juxt :completed get-grub-ingredient :grub) (vals grubs)))

(defn add-grub-event [grub]
  {:event :add-grub 
   :id (str "grub-" (.now js/Date))
   :grub grub
   :completed false})

(defn add-grub [add {:keys [new-grub]} owner]
  (when (not (empty? new-grub))
    (let [new-grub-event (add-grub-event new-grub)]
      (om/set-state! owner :new-grub "")
      (put! add (add-grub-event new-grub)))))

(defn add-grub-on-enter [event add state owner]
  (when (enter-pressed? event)
    (add-grub add state owner)))

(defn handle-new-grub-change [event owner]
  (om/set-state! owner :new-grub (.. event -target -value)))

(defn grubs-view [grubs owner]
  (reify
    om/IInitState
    (init-state [_]
      {:new-grub ""})
    om/IRenderState
    (render-state [this {:keys [new-grub] :as state}]
      (let [add (om/get-shared owner :add)]
        (html 
         [:div 
          [:h3 "Grub List"]
          [:div.input-group.add-grub-input-form
           [:span.input-group-btn
            [:input.form-control#add-grub-input 
             {:type "text" 
              :placeholder "2 grubs"
              :value new-grub
              :on-key-up #(add-grub-on-enter % add state owner)
              :on-change #(handle-new-grub-change % owner)}]]
           [:button.btn.btn-primary 
            {:id "add-grub-btn" 
             :type "button"
             :on-click #(add-grub (om/get-shared owner :add) new-grub owner)}
            "Add"]]
          [:ul#grub-list.list-group
           (for [grub (sort-grubs grubs)]
             (om/build grub-view grub))]
          [:button.btn.pull-right 
           {:id "clear-all-btn" 
            :class (when (empty? grubs) "hidden")
            :type "button"
            :on-click #(put! (om/get-shared owner :clear-all) {:event :clear-all-grubs})}
           "Clear all"]])))))
