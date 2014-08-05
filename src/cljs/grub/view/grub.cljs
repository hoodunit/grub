(ns grub.view.grub
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [grub.view.dom :as dom]
            [cljs-uuid.core :as uuid])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn new-grub [grub]
  {:id (str "grub-" (uuid/make-random))
   :grub grub
   :completed false})

(defn add-event [grub]
  (assoc (new-grub grub) :event :add-grub))

(defn add-list-event [grubs]
  {:event :add-grub-list
   :grubs grubs})

(defn edit-event [id grub]
  {:event :update-grub
   :id id
   :grub grub})

(defn complete-event [{:keys [id completed]}]
  {:event :update-grub
   :id id
   :completed (not completed)})

(def transitions
  {:waiting {:mouse-down :pressed
             :touch-start :pressed}
   :pressed {:mouse-up :waiting
             :mouse-leave :waiting
             :mouse-out :waiting
             :touch-cancel :waiting
             :touch-end :waiting
             :timeout :editing}
   :editing {:enter :waiting
             :body-mousedown :waiting}})

(defn transition-state [owner event]
  (let [current (om/get-state owner :edit-state)
        next (or (get-in transitions [current event]) current)]
    (condp = [current next]
      [:waiting :pressed] (let [timeout-fn #(transition-state owner :timeout)
                                timeout-id (js/setTimeout timeout-fn 500)]
                            (om/set-state! owner :timeout-id timeout-id))
      [:pressed :waiting] (js/clearTimeout (om/get-state owner :timeout-id)) 
      [:editing :waiting] (let [update-ch (om/get-shared owner :grub-update)
                                id (:id @(om/get-props owner))
                                edit-event (edit-event id (om/get-state owner :grub))]
                            (put! update-ch edit-event))
      nil)
    (om/set-state! owner :edit-state next)))

(defn view [{:keys [id grub completed] :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:edit-state :waiting
         :grub grub}))

    om/IRenderState
    (render-state [_ {:keys [edit-state] :as state}]
      (html
       [:li.list-group-item.grub-item 
        {:class [(when completed "completed")
                 (when (= edit-state :pressed) "grub-active")
                 (when (= edit-state :editing) "edit")]
         :on-click #(when (#{:waiting :pressed} edit-state)
                      (put! (om/get-shared owner :grub-update) (complete-event @props)))
         :on-mouse-down #(transition-state owner :mouse-down) 
         :on-mouse-up #(transition-state owner :mouse-up) 
         :on-mouse-leave #(transition-state owner :mouse-leave)
         :on-touch-start #(transition-state owner :touch-start)
         :on-touch-cancel #(transition-state owner :touch-cancel)
         :on-touch-end #(transition-state owner :touch-end)} 
        [:span.grub-static
         (if completed
           [:span.glyphicon.glyphicon-check]
           [:span.glyphicon.glyphicon-unchecked])
         [:span.grub-text grub]]
        [:input.grub-input 
         {:type "text" 
          :value (:grub state)
          :on-change #(om/set-state! owner :grub (.. % -target -value))
          :on-key-up #(when (dom/enter-pressed? %) (transition-state owner :enter))}]]))

    om/IWillMount
    (will-mount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (chan)]
        (a/sub <events :body-mousedown subscriber)
        (go-loop [] (let [event (<! subscriber)]
                      (when-not (dom/click-on-self? (:event event) (om/get-node owner))
                        (transition-state owner :body-mousedown))
                      (recur)))))))
