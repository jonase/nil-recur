(ns examples.table-component
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(enable-console-print!)

(def projects
  [{:name "Reagent"
    :author "holmsand"
    :url "https://github.com/reagent-project/reagent"}
   {:name "Quiescent"
    :author "levand"
    :url "https://github.com/levand/quiescent"}
   {:name "Reacl"
    :author "active-group"
    :url "https://github.com/active-group/reacl"}
   {:name "Om"
    :author "swannodette"
    :url "https://github.com/swannodette/om"}])


(defn table-header [columns owner]
  (om/component
   (dom/thead nil
              (apply dom/tr nil
                     (map #(dom/th nil (:title %))
                          columns)))))

(defn table-body [{:keys [data columns]} owner]
  (om/component
   (apply dom/tbody nil
          (for [record data]
            (apply dom/tr nil
                   (for [{:keys [cell-fn cell cell-data-fn]} columns]
                     (dom/td nil
                             (if cell-fn
                               (cell-fn record)
                               (om/build cell ((or cell-data-fn identity) record))))))))))

(defn table [table-spec owner]
  (om/component
   (dom/table nil
              (om/build table-header (:columns table-spec))
              (om/build table-body table-spec))))


;;; Application

(defn index-by [f coll]
  (reduce (fn [result item]
            (assoc result (f item) item))
          {}
          coll))

(def app-state (atom (index-by :name projects)))

(defn voter [{:keys [votes on-vote]} owner]
  (om/component
   (dom/span nil
             (dom/button #js {:onClick #(on-vote (inc votes))} "+")
             votes
             (dom/button #js {:onClick #(on-vote (dec votes))} "-"))))

(defn root-component [data]
  (om/component
   (let [data (om/value data)]
     (om/build table
               {:data (->> data
                           om/value
                           vals
                           (sort-by #(or (:votes %) 0))
                           reverse)
                :columns [{:title "Votes"
                           :cell voter
                           :cell-data-fn (fn [record]
                                           {:votes (or (:votes record) 0)
                                            :on-vote (fn [n]
                                                       (swap! app-state assoc-in [(:name record) :votes] n))})}
                          {:title "Author"
                           :cell-fn :author}
                          {:title "Project"
                           ;; Should not create anonymous components.
                           ;; :cell #(om/component (dom/a #js {:href (:url %)} (:name %)))
                           :cell-fn (fn [{:keys [name url]}]
                                      (dom/a #js {:href url} name))}]}))))

(defn init []
  (om/root root-component
           app-state
           {:target (.getElementById js/document "app")}))
