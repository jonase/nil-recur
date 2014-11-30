(ns examples.select-component
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn component-1 [{:keys [users]} owner]
  (reify
    om/IInitState
    (init-state [this]
      {:selected-user-id nil})
    om/IRenderState
    (render-state [this {:keys [selected-user-id]}]
      (apply dom/select #js {:onChange #(om/set-state! owner :selected-user-id
                                                       (-> % .-target .-value))
                             :value (or selected-user-id "__placeholder")}
             (when-not selected
               (dom/option #js {:disabled true :value "__placeholder"}
                           "Select a user"))
             (map (fn [{:keys [first-name last-name email]}]
                    (dom/option #js {:value email}
                                last-name ", " first-name))
                  users)))))

(defn select [{:keys [placeholder data selected label-fn key-fn on-select]} owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/select
             #js {:onChange (fn [evt]
                              (let [key (-> evt .-target .-value)
                                    sel (some (fn [record]
                                                (if (= key (key-fn record))
                                                  record))
                                              data)]
                                (on-select sel)))
                  :value (if selected
                           (key-fn selected)
                           "__placeholder")}
             (when (and placeholder (not selected))
               (dom/option #js {:disabled true :value "__placeholder"} placeholder))
             (map (fn [record]
                    (dom/option #js {:value (key-fn record)} (label-fn record)))
                  data)))))

(defn component-2 [{:keys [users]} owner]
  (reify
    om/IInitState
    (init-state [this]
      {:selected-user nil})
    om/IRenderState
    (render-state [this {:keys [selected-user]}]
      (om/build select
                {:placeholder "Select a user"
                 :selected selected-user
                 :data users
                 :label-fn #(str (:last-name %) ", " (:first-name %))
                 :key-fn :email
                 :on-select #(om/set-state! owner :selected-user %)}))))


(defn root-component [data owner]
  (om/component
   (let [data (om/value data)]
     (dom/div nil
            (om/build component-1 data)
            (om/build component-2 data)))))

(def users
  [{:first-name "Donald" :last-name "Knuth" :email "donald-knuth@example.com"}
   {:first-name "Leslie" :last-name "Lamport" :email "leslie-lamport@example.com"}
   {:first-name "Edsger" :last-name "Dijkstra" :email "edsger-dijskstra@example.com"}
   {:first-name "John" :last-name "McCarthy" :email "john-mccarthy@example.com"}
   {:first-name "Alan" :last-name "Kay" :email "alan-kay@example.com"}])

(defn example-1 []
  (om/root root-component
           {:users users}
           {:target (.getElementById js/document "app")}))
