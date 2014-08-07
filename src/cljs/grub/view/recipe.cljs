(ns grub.view.recipe
  (:require [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [cljs-uuid.core :as uuid]
            [grub.view.dom :as dom]
            [grub.view.grub :as grub-view])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn add-event [name grubs]
  {:event :add-recipe 
   :id (str "recipe-" (uuid/make-random))
   :name name
   :grubs grubs})

(defn update-event [id name grubs]
  {:event :update-recipe 
   :id id
   :name name
   :grubs grubs})

(defn parse-grubs-from-str [grubs-str]
  (->> grubs-str
       (clojure.string/split-lines)
       (map grub-view/new-grub)
       (into [])))

(defn add-grubs [add-grubs-ch grubs-str]
  (let [grubs (parse-grubs-from-str grubs-str)
        event (grub-view/add-list-event grubs)]
    (put! add-grubs-ch event)))

(def transitions
  {:waiting {:click :editing}
   :editing {:body-mousedown :waiting
             :save :waiting}})

(defn transition-state [owner event]
  (let [current (om/get-state owner :edit-state)
        next (or (get-in transitions [current event]) current)]
    (condp = [current next]
      [:editing :waiting] (let [update-ch (om/get-shared owner :recipe-update)
                                id (:id @(om/get-props owner))
                                name (om/get-state owner :name)
                                grubs (om/get-state owner :grubs)
                                event (update-event id name grubs)]
                            (put! update-ch event))
      nil)
    (om/set-state! owner :edit-state next)))

(defn num-newlines [str]
  (count (re-seq #"\n" str)))

(defn view [{:keys [id] :as props} owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:edit-state :waiting
         :name (:name props)
         :grubs (:grubs props)
         :unmounted false}))

    om/IWillReceiveProps
    (will-receive-props [this next-props]
      (om/set-state! owner :name (:name next-props))
      (om/set-state! owner :grubs (:grubs next-props)))

    om/IRenderState
    (render-state [this {:keys [edit-state name grubs]}]
      (let [update (om/get-shared owner :recipe-update)
            add-grubs-ch (om/get-shared owner :recipe-add-grubs)]
        (html
         [:div.panel.panel-default.recipe-panel
          {:on-click 
           #(when (not (or (dom/click-on-elem? % (om/get-node owner :add-grubs-btn))
                           (dom/click-on-elem? % (om/get-node owner :save-btn))))
              (transition-state owner :click))}
          [:div.panel-heading.recipe-header
           [:input.form-control.recipe-header-input 
            {:type "text" 
             :readOnly (if (= edit-state :editing) "" "readonly")
             :value name
             :on-change #(om/set-state! owner :name (dom/event-val %))}]
           [:button.btn.btn-primary.btn-sm.recipe-add-grubs-btn 
            {:type "button"
             :ref :add-grubs-btn
             :on-click #(add-grubs add-grubs-ch grubs)}
            "Add Grubs"]]
          [:div.panel-body.recipe-grubs
           {:class (when (= edit-state :waiting) "hidden")}
           [:textarea.form-control.recipe-grubs-input
            {:id "recipe-grubs"
             :ref :textarea
             :rows (inc (num-newlines grubs))
             :value grubs
             :on-change #(om/set-state! owner :grubs (dom/event-val %))}]
           [:button.btn.btn-primary.pull-right.recipe-done-btn
            {:type "button"
             :ref :save-btn
             :on-click #(transition-state owner :save)}
            "Save"]]])))
    
    om/IDidMount
    (did-mount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (chan)]
        (om/set-state! owner :subscriber subscriber)
        (a/sub <events :body-mousedown subscriber)
        (go-loop [] (let [event (<! subscriber)]
                      (when-not (or (nil? event)
                                    (om/get-state owner :unmounted))
                        (when-not (dom/click-on-self? (:event event) (om/get-node owner))
                          (transition-state owner :body-mousedown))
                        (recur))))))
    
    om/IWillUnmount
    (will-unmount [_]
      (let [<events (om/get-shared owner :<events)
            subscriber (om/get-state owner :subscriber)]
        (om/set-state! owner :unmounted true)
        (a/unsub <events :body-mousedown subscriber)
        (a/close! (om/get-state owner :subscriber))))))
