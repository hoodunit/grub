(ns grub.view.grub
  (:require [grub.view.dom :as dom]
            [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [cljs-uuid.core :as uuid])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn new-grub [text]
  {:id        (keyword (str "grub-" (uuid/make-random)))
   :text      text
   :completed false})

(def transitions
  {:waiting {:mouse-down :pressed
             :touch-start :pressed}
   :pressed {:mouse-up :waiting
             :mouse-leave :waiting
             :mouse-out :waiting
             :touch-cancel :waiting
             :touch-end :waiting
             :timeout :editing
             :scroll :waiting}
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
      [:editing :waiting] (let [grub (om/get-props owner)]
                            (om/transact! grub nil #(assoc % :text (om/get-state owner :grub-text)) :local))
      nil)
    (when-not (= current next) (om/set-state! owner :edit-state next))))

(defn view [{:keys [id text completed] :as grub} owner {:keys [remove-ch]}]
  (reify
    om/IInitState
    (init-state [_]
      {:edit-state :waiting
       :grub-text text
       :unmounted false})

    om/IWillReceiveProps
    (will-receive-props [this {:keys [text]}]
      (when-not (= (om/get-state owner :grub-text) text)
        (om/set-state! owner :grub-text text)))

    om/IRenderState
    (render-state [_ {:keys [edit-state] :as state}]
      (html
       [:li.list-group-item.grub-item 
        {:class [(when completed "completed")
                 (when (= edit-state :pressed) "grub-active")
                 (when (= edit-state :editing) "edit")]
         :on-click (fn [e] (when (#{:waiting :pressed} edit-state)
                             (om/transact! grub nil #(assoc % :completed (not completed)) :local)
                             (.blur (om/get-node owner "grub-input"))))
         :on-mouse-down #(transition-state owner :mouse-down) 
         :on-mouse-up #(transition-state owner :mouse-up) 
         :on-mouse-leave #(transition-state owner :mouse-leave)
         :on-touch-start #(transition-state owner :touch-start)
         :on-touch-cancel #(transition-state owner :touch-cancel)
         :on-touch-end #(transition-state owner :touch-end)} 
        [:input.grub-input 
         {:type "text" 
          :readOnly (if (= edit-state :editing) "" "readonly")
          :ref "grub-input"
          :value (:grub-text state)
          :on-change #(om/set-state! owner :grub-text (.. % -target -value))
          :on-key-up #(when (dom/enter-pressed? %) (transition-state owner :enter))}]
         (when (= edit-state :editing) 
           [:span.glyphicon.glyphicon-remove.pull-right
            {:on-click #(put! remove-ch id)}])]))

    om/IDidMount
    (did-mount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (chan)]
        (om/set-state! owner :subscriber subscriber)
        (a/sub <events :body-mousedown subscriber)
        (a/sub <events :body-scroll subscriber)
        (go-loop [] (let [event (<! subscriber)]
                      (when-not (or (nil? event)
                                    (om/get-state owner :unmounted))
                        (when (and (= (:type event) :body-mousedown)
                                   (not (dom/click-on-self? (:event event) 
                                                            (om/get-node owner))))
                          (transition-state owner :body-mousedown))
                        (when (= (:type event) :body-scroll)
                          (transition-state owner :scroll))
                        (recur))))))
    om/IWillUnmount
    (will-unmount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (om/get-state owner :subscriber)]
        (om/set-state! owner :unmounted true)
        (a/unsub <events :body-mousedown subscriber)
        (a/unsub <events :body-scroll subscriber)
        (a/close! (om/get-state owner :subscriber))))))
