(ns grub.view.recipe
  (:require [grub.view.dom :as dom]
            [grub.view.grub :as grub-view]
            [grub.util :as util]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as a :refer [<! put! chan]]
            [cljs-uuid.core :as uuid])
  (:require-macros [grub.macros :refer [log logs]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn new-recipe [name grubs]
  {:id (str "recipe-" (uuid/make-random))
   :name name
   :grubs grubs})

(defn parse-grubs-from-str [grubs-str]
  (->> grubs-str
       (clojure.string/split-lines)
       (map grub-view/new-grub)
       (into [])))

(defn add-grubs [add-grubs-ch grubs-str]
  (let [grubs (parse-grubs-from-str grubs-str)
        grubs-map (util/map-by-key :id grubs)]
    (put! add-grubs-ch grubs-map)))

(def transitions
  {:waiting {:click :editing}
   :editing {:body-mousedown :waiting
             :save :waiting}})

(defn transition-state [owner event]
  (let [current (om/get-state owner :edit-state)
        next (or (get-in transitions [current event]) current)]
    (condp = [current next]
      [:editing :waiting] (let [recipe (om/get-props owner)
                                name (om/get-state owner :name)

                                grubs (om/get-state owner :grubs)]
                            (om/transact! recipe nil #(assoc % :name name :grubs grubs) :local))
      nil)
    (when-not (= current next) (om/set-state! owner :edit-state next))))

(defn num-newlines [str]
  (count (re-seq #"\n" str)))

(defn view [{:keys [id] :as recipe} owner {:keys [remove-recipe-ch]}]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:edit-state :waiting
         :name (:name recipe)
         :grubs (:grubs recipe)
         :unmounted false}))

    om/IWillReceiveProps
    (will-receive-props [this next-recipe]
      (om/set-state! owner :name (:name next-recipe))
      (om/set-state! owner :grubs (:grubs next-recipe)))

    om/IRenderState
    (render-state [this {:keys [edit-state name grubs]}]
      (let [update (om/get-shared owner :recipe-update)]
        (html
         [:div.panel.panel-default.recipe-panel
          {:on-click 
           #(when (not (or (dom/click-on-elem? % (om/get-node owner :add-grubs-btn))
                           (dom/click-on-elem? % (om/get-node owner :save-btn))))
              (transition-state owner :click))}
          [:div.panel-heading.recipe-header
           {:class (when (= edit-state :editing) "edit")}
           [:input.form-control.recipe-header-input 
            {:type "text" 
             :readOnly (if (= edit-state :editing) "" "readonly")
             :value name
             :on-change #(om/set-state! owner :name (dom/event-val %))}]
           [:button.btn.btn-primary.btn-sm.recipe-add-grubs-btn
            {:type "button"
             :class (when (= edit-state :editing) "hidden")
             :ref :add-grubs-btn
             :on-click #(add-grubs (om/get-shared owner :add-grubs-ch) grubs)}
            [:span.glyphicon.glyphicon-plus]
            " Grubs"]]
          [:div.panel-body.recipe-grubs
           {:class (when (= edit-state :waiting) "hidden")}
           [:textarea.form-control.recipe-grubs-input
            {:id "recipe-grubs"
             :ref :textarea
             :rows (inc (num-newlines grubs))
             :value grubs
             :on-change #(om/set-state! owner :grubs (dom/event-val %))}]
           [:button.btn.btn-danger.pull-left.recipe-remove-btn
            {:type "button"
             :on-click #(put! remove-recipe-ch id)}
            [:span.glyphicon.glyphicon-trash]]
           [:button.btn.btn-primary.pull-right.recipe-done-btn
            {:type "button"
             :ref :save-btn
             :on-click #(transition-state owner :save)}
            [:span.glyphicon.glyphicon-ok]]]])))
    
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
