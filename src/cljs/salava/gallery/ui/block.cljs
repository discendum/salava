(ns salava.gallery.ui.block
  (:require [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.helper :refer [path-for]]
            [salava.core.ui.badge-grid :refer [badge-grid-element]]
            [salava.core.i18n :refer [t]]
            [reagent.core :refer [atom cursor]]
            [salava.core.ui.page-grid :refer [page-grid-element]]))

(defn init-grid [kind state]
  (ajax/GET
    (path-for "/obpv1/gallery/recent")
    {:params {:kind kind
              :userid (:user-id @state)}
     :handler (fn [data] (swap! state merge data))}))

(defn page-grid [pages profile_picture page-small-view]
  (into [:div {:class "row wrap-grid" :id "grid"}]
        (for [element-data (if page-small-view (sort-by :mtime > pages) (take 3 (sort-by :mtime > pages)))]
          (page-grid-element (assoc element-data :profile_picture profile_picture) {:type "profile"}))))


(defn badge-grid [badges badge-small-view]
  (into [:div {:class "row wrap-grid" :id "grid"}]
        (for [element-data (if badge-small-view (sort-by :mtime > badges) (take 4 (sort-by :mtime > badges)))]
          (badge-grid-element element-data nil "profile" nil))))

(defn ^:export recentbadges [data]
  (let [badge-small-view (cursor data [:badge-small-view])
        {:keys [edit-mode? user-id user]} data]
    (init-grid "badges" data)
    (fn []
      [:div#user-badges
       [:div.row
        [:div.col-md-12
         [:h3 {:class ""} (t :user/Recentbadges)]
         [badge-grid (:badges @data) @badge-small-view]

         (when (< 4 (count @(cursor data [:badges])))
          [:div [:a {:href "#" :on-click #(reset! badge-small-view (if @badge-small-view false true))}  (if @badge-small-view (t :admin/Showless) (t :user/Showmore))]])]]])))



(defn ^:export recentpages [data]
  (let [page-small-view (cursor data [:page-small-view])
        {:keys [edit-mode? user-id user]} data]
    (init-grid "pages" data)
    (fn []
      [:div#user-pages
       [:div.row
        [:div.col-md-12
         [:h3 {:class ""} (t :user/Recentpages)]
         [page-grid (:pages @data) (:profile_picture user) @page-small-view]

         (when (< 3 (count @(cursor data [:pages])))
          [:div [:a {:href "#" :on-click #(reset! page-small-view (if @page-small-view false true))}  (if @page-small-view (t :admin/Showless) (t :user/Showmore))]])]]])))
