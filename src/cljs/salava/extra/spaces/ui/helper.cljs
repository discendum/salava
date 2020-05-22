(ns salava.extra.spaces.ui.helper
 (:require
  [reagent-modals.modals :as m]
  [salava.core.time :refer [date-from-unix-time]]))

(defn space-card [info]
 (let [{:keys [name logo valid_until visibility status ctime]} info]
   [:div {:class "col-xs-12 col-sm-6 col-md-4"}
          ;:key id}
    [:div {:class "media grid-container"}
     [:a {:href "#"} ;:on-click #(mo/open-modal [:profile :view] {:user-id id}) :style {:text-decoration "none"}}
      [:div.media-content
       [:div.media-left
        (if logo
          [:img {:src "" ;(profile-picture profile_picture)
                 :alt "" #_(str first_name " " last_name)}]
          [:i.fa.fa-building-o {:style {:font-size "22px"}}])]
       [:div.media-body
        [:div {:class "media-heading profile-heading"}
         name]
        [:div.media-profile
         [:div.join-date
          (t :gallery/Joined) ": " (date-from-unix-time (* 1000 ctime))]]]]
      #_[:div.common-badges
         (if (= id current-user)
           (t :gallery/ownprofile)
           [:span common_badge_count " " (if (= common_badge_count 1)
                                           (t :gallery/commonbadge) (t :gallery/commonbadges))])]]]]))
