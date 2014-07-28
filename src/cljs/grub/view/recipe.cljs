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

(defn parse-grubs-from-str [grubs-str]
  (->> grubs-str
       (clojure.string/split-lines)
       (map grub-view/new-grub)
       (into [])))

(defn add-grubs [add-grubs-ch grubs-str]
  (let [grubs (parse-grubs-from-str grubs-str)
        event (grub-view/add-list-event grubs)]
    (put! add-grubs-ch event)))

(defn recipe-view [recipe owner]
  (reify
    om/IRender
    (render [this]
      (let [{:keys [id name grubs]} recipe
            add-grubs-ch (om/get-shared owner :recipe-add-grubs)]
        (html
         [:div.panel.panel-default.recipe-panel
          {:id id
           :key id}
          [:div.panel-heading.recipe-header
           [:input.form-control.recipe-header-input 
            {:id "recipe-name"
             :type "text" 
             :placeholder "Grub pie"
             :value name}]
           [:button.btn.btn-primary.btn-sm.recipe-add-grubs-btn 
            {:type "button"
             :on-click #(add-grubs add-grubs-ch grubs)}
            "Add Grubs"]]
          [:div.panel-body.recipe-grubs.hidden
           [:textarea.form-control.recipe-grubs-input
            {:id "recipe-grubs"
             :rows 3 
             :placeholder "2 grubs"
             :value grubs}]
           [:button.btn.btn-primary.hidden.pull-right.recipe-btn.recipe-done-btn
            {:type "button"} "Done"]]])))))

(defn add-recipe [ch name grubs owner]
  (when (and (not (empty? name))
             (not (empty? grubs)))
    (om/set-state! owner :new-recipe-name "")
    (om/set-state! owner :new-recipe-grubs "")
    (om/set-state! owner :editing false)
    (put! ch (add-event name grubs))))

(defn new-recipe-view [_ owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [publisher (chan)]
        {:editing false
         :>local-events publisher
         :<local-events (a/pub publisher identity)
         :new-recipe-name ""
         :new-recipe-grubs ""}))

    om/IRenderState
    (render-state [this {:keys [editing >local-events new-recipe-name new-recipe-grubs]}]
      (let [add (om/get-shared owner :recipe-add)]
        (html
         [:div.panel.panel-default.recipe-panel
          {:on-click #(put! >local-events :click)}
          [:div.panel-heading.recipe-header
           [:input.form-control.recipe-header-input 
            {:type "text" 
             :placeholder "New recipe"
             :value new-recipe-name
             :on-change #(om/set-state! owner :new-recipe-name (dom/event-val %))}]]
          [:div.panel-body.recipe-grubs
           {:class (when (not editing) "hidden")}
           [:textarea.form-control.recipe-grubs-input
            {:id "recipe-grubs"
             :rows 3 
             :placeholder "Recipe ingredients"
             :value new-recipe-grubs
             :on-change #(om/set-state! owner :new-recipe-grubs (dom/event-val %))}]
           [:button.btn.btn-primary.pull-right.recipe-btn.recipe-done-btn
            {:type "button"
             :on-click #(add-recipe add new-recipe-name new-recipe-grubs owner)}
            "Done"]]])))
    
    om/IWillMount
    (will-mount [_]
      (let [<local-events (om/get-state owner :<local-events)
            <events (om/get-shared owner :<events)]
        (go-loop []
                 (let [subscriber (chan)]
                   (a/sub <local-events :click subscriber)
                   (<! subscriber)
                   (a/unsub <local-events :click subscriber)
                   (a/close! subscriber))
                 (om/set-state! owner :editing true)
                 (let [subscriber (chan)]
                   (a/sub <events :body-mousedown subscriber)
                   (loop []
                     (let [event (<! subscriber)]
                       (when (and (= (:type event) :body-mousedown)
                                  (dom/click-on-self? (:event event) (om/get-node owner)))
                         (recur))))
                   (a/unsub <events :body-mousedown subscriber)
                   (a/close! subscriber))
                 (om/set-state! owner :editing false)
                 (recur))))))

(defn recipes-view [recipes owner]
  (reify
    om/IRender
    (render [this]
      (html
       [:div
        [:h3.recipes-title "Recipes"]
        (om/build new-recipe-view recipes)
        [:ul#recipe-list.list-group.recipe-list
         (for [recipe (vals recipes)]
           (om/build recipe-view recipe {:key :id}))]]))))
