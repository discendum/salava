(ns salava.social.ui.dashboard
  (:require [salava.core.ui.layout :as layout]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.social.ui.stream :as stream]
            [reagent.core :refer [atom cursor]]
            [salava.core.ui.modal :as mo]
            [salava.core.i18n :as i18n :refer [t translate-text]]
            [salava.core.time :refer [date-from-unix-time]]
            [reagent-modals.modals :as m]
            [salava.core.ui.helper :refer [path-for plugin-fun not-activated?]]
            [salava.badge.ui.my :as my]
            [reagent.session :as session]
            [salava.core.helper :refer [dump]]
            [salava.user.ui.helper :refer [profile-picture]]
            [salava.core.ui.notactivated :refer [not-activated-banner]]
            [salava.badge.ui.pending :as pb]))



(defn init-dashboard [state]
  (stream/init-data state)
  (pb/init-data state)
  (ajax/GET
    (path-for (str "/obpv1/user/dashboard"))
    {:handler (fn [data]
                (swap! state merge data))}))

(defn follow-event-badge [event state]
  (let [{:keys [subject verb image_file message ctime event_id name object]}  event
        modal-message (str "messages")]
    [:div {:class "media"}
     [:a {:href "#"
          :on-click #(do
                       (mo/open-modal [:gallery :badges] {:badge-id object})
                       (.preventDefault %))
          :style {:text-decoration "none"}}
      [:div.media-left
       [:img {:src (str "/" image_file)} ]]
      [:div.media-body
       [:div.content-text
        [:p.content-heading
         (t :social/Youarefollowingthisbadge)]
        [:span.date (date-from-unix-time (* 1000 ctime) "days")]
        ]]]]))

(defn message-event [event state]
  (let [{:keys [subject verb image_file message ctime event_id name object]}  event
        ;reload-fn (fn [] (init-data state))
        ]
    [:div {:class "media"}
     [:a {:href "#"
          :on-click #(do
                       ;(b/open-modal object true init-data state)
                       ;(init-data state)
                       (mo/open-modal [:gallery :badges] {:badge-id object
                                                          :show-messages true
                                                          :reload-fn nil})
                       (.preventDefault %) )
          :style {:text-decoration "none"}}
      [:div.media-left
       [:img {:src (str "/" image_file)} ]]
      [:div.media-body
       [:div.content-text
        [:p.content-heading (:message message)]
        [:span.date (date-from-unix-time (* 1000 ctime) "days") ]]]]]))

(defn publish-event-badge [event state]
  (let [{:keys [subject verb image_file message ctime event_id name object first_name last_name]}  event]
    [:div {:class "media"}
     ;(hide-event event_id state)
     [:a {:href "#"
          :on-click #(do
                       (mo/open-modal [:badge :info] {:badge-id object})

                       (.preventDefault %) )}
      [:div.media-left
       [:img {:src (str "/" image_file)} ]]
      [:div.media-body
       [:div.content-text
        [:p.content-heading (str (t :social/User) " " first_name " " last_name " " (t :social/Publishedbadge) " " name)]
        [:span.date (date-from-unix-time (* 1000 ctime) "days")]
        ]]]]))

(defn publish-event-page [event state]
  (let [{:keys [subject verb profile_picture message ctime event_id name object first_name last_name]}  event]
    [:div {:class "media"}
     [:a {:href "#"
          :on-click #(do
                       (mo/open-modal [:page :view] {:page-id object})

                       (.preventDefault %) )}
      [:div.media-left
       [:img {:src (profile-picture profile_picture) } ]]
      [:div.media-body
       [:div.content-text
        [:p.content-heading (str (t :social/User) " " first_name " " last_name " " (t :social/Publishedpage) " " name)]
        [:span.date (date-from-unix-time (* 1000 ctime) "days") ]]]]]))

(defn follow-event-user [event state]
  (let [{:keys [subject verb ctime event_id object s_first_name s_last_name o_first_name o_last_name o_id s_id owner o_profile_picture s_profile_picture]}  event
        modal-message (str "messages")]
    [:div {:class "media"}
     [:a {:href "#"
          :on-click #(do
                       (mo/open-modal [:user :profile] {:user-id (if (= owner s_id)
                                                                   o_id
                                                                   s_id)})
                       (.preventDefault %) )}
      [:div.media-left
       [:img {:src (profile-picture (if (= owner s_id)
                                      o_profile_picture
                                      s_profile_picture)) } ]]
      [:div.media-body
       [:div.content-text
        [:p.content-heading (if (= owner s_id)
                              (str (t :social/Youstartedfollowing) " " o_first_name " " o_last_name)
                              (str  s_first_name " " s_last_name " " (t :social/Followsyou)  ))]
        [:span.date (date-from-unix-time (* 1000 ctime) "days")]]]]]))

(defn badge-advert-event [event state]
  (let [{:keys [subject verb image_file ctime event_id name object issuer_content_id issuer_content_name issuer_image]} event
        visibility (if issuer_image "visible" "hidden")]
    [:div {:class "media"}
     [:a {:href "#"
          :on-click #(do
                       (.preventDefault %)
                       (mo/open-modal [:application :badge] {:id subject :state state}))}
      [:div.media-left
       [:img {:src (str "/" image_file)} ]]
      [:div.media-body
       [:div.content-text
        [:p.content-heading (str issuer_content_name " " (t :social/Publishedbadge) " " name)]
        [:span.date (date-from-unix-time (* 1000 ctime) "days") ]]]]]))


(defn welcome-block [state]
  [:div#welcome-block {:class "block"}
   [:div.welcome-block.block-content.row
    [:a {:data-toggle "collapse" :href "#hidden" :role "button" :aria-expanded "false" :aria-controls "hidden"}
     [:div.content
      [:div
       [:i.fa.fa-chevron-down.icon] (str (t :core/Welcometo) " " (session/get :site-name) " " (get-in @state [:user-profile :user :first_name] "") "!")]]
     [:div.collapse.hidden-content {:id "hidden" }
      [:p "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat."]
      ]
     ]]])

(defn notifications-block [state]
  (let [events (->> (:events @state) (remove #(= (:verb %) "ticket")) (take 5))]
    [:div {:class "box col-md-4" }
     [:div#box_1
      [:div.col-md-12.block
       ;(prn @state)
       [:div.notifications-block.row_1.notifications;.block-content
        [:div.heading_1 [:i.fa.fa-rss.icon]
         [:a {:href "social/stream"} [:span.title (t :social/Stream)]] [:span.badge (count (:events @state))]]
        (reduce (fn [r event]
                  (conj r [:div.notification-div.ax_default
                           (cond
                             (and (= "badge" (:type event)) (= "follow" (:verb event))) (follow-event-badge event state)
                             (and (= "user" (:type event)) (= "follow" (:verb event))) (follow-event-user event state)
                             (and (= "badge" (:type event)) (= "publish" (:verb event))) (publish-event-badge event state)
                             (and (= "page" (:type event)) (= "publish" (:verb event))) (publish-event-page event state)
                             (= "advert" (:type event)) (badge-advert-event event state)
                             (= "message" (:verb event)) [message-event event state]
                             :else "")
                           ])
                  )[:div.content] events)
        ]]]]))

(defn latest-earnable-badges []
  (let [block (first (plugin-fun (session/get :plugins)  "application" "latestearnablebadges"))]
    (if block [block] [:div ""])))

(defn application-button []
  (let [button (first (plugin-fun (session/get :plugins) "application" "button"))]
    (if button
      [button]
      [:div ""])))

(defn badges-block [state]
  (fn []
    (let [badges (:badges @state )]
      [:div {:class "box col-md-5 "}
       [:div#box_2
        [:div.col-md-12.block
         [:div.badge-block;.block-content
          [:div.heading_1
           [:i.fa.fa-certificate.icon]
           [:a {:href (path-for "/badge")} [:span.title (t :badge/Badges)]]]
          (when-not (not-activated?)  [application-button]); [:button.btn.button "Earn badges"]
          [:div.content
           [:div.stats
            [:div.total-badges
             [:p.num (get-in @state [:stats :badge_count] 0)]
             [:p.desc (t :badge/Badges)]]
            [:div
             [:p.num (->> (get-in @state [:stats :badge_views])
                          (reduce #(+ %1 (:reg_count %2) (:anon_count %2)) 0))]
             [:p.desc (t :badge/Badgeviews)]]]

           (when (seq (:pending-badges @state)) [:div.pending
                                                 [:p.header (t :badge/Pendingbadges)]
                                                 (if (seq (:pending-badges @state))
                                                   (reduce (fn [r badge]
                                                             (conj r [:a {:href (path-for "/badge")} [:img {:src (str "/" (:image_file badge)) :alt (:name badge) :title (:name badge)}]])
                                                             ) [:div] (take 5 (:pending-badges @state)))
                                                   )])
           [:div.badges
            [:p.header (t :social/Lastestearnedbadges)]
            (reduce (fn [r badge]
                      (conj r [:a {:href "#" :on-click #(do
                                                          (.preventDefault %)
                                                          (mo/open-modal [:badge :info] {:badge-id (:id badge )} {:hidden (fn [] (init-dashboard state))}))} [:img {:src (str "/" (:image_file badge)) :alt (:name badge) :title (:name badge)}]])
                      ) [:div] badges)
            ]
           [latest-earnable-badges]]]]]])))

(defn explore-block [state]
  [:div.box.col-md-4
   [:div#box_1 {:class "row_2"}
    [:div.col-md-12.block
     [:div.row_2;.block-content
      [:div.heading_1
       [:i.fa.fa-search.icon]
       [:span.title (t :gallery/Explore)]]
      [:div.content
       [:div.info-block.badge-connection
        [:a {:href (path-for "/gallery/badges")}
         [:div.info
          [:i.fa.fa-certificate.icon]
          [:div.text
           [:p.num (get-in @state [:gallery :badges])]
           [:p.desc (t :badge/Badges)]]]]]
       [:div.info-block.page
        [:a {:href (path-for "/gallery/pages")}[:div.info
                                                [:i.fa.fa-file.icon]
                                                [:div.text
                                                 [:p.num (get-in @state [:gallery :pages])]
                                                 [:p.desc (t :page/Pages)]]]]]
       [:div.info-block
        [:a {:href (path-for "/gallery/profiles")}[:div.info
                                                   [:i.fa.fa-user.icon]
                                                   [:div.text
                                                    [:p.num (get-in @state [:gallery :profiles :all])]
                                                    [:p.desc (t :gallery/Profiles)]]]]]]]]]])

(defn user-connections-stats []
  (let [blocks (first (plugin-fun (session/get :plugins) "block" "userconnectionstats"))]
    (if blocks
      [blocks]
      [:div ""])))

(defn connections-block [state]
  [:div.box.col-md-5
   [:div#box_2
    [:div.col-md-12.block
     [:div.row_2
      [:div
       [:div.heading_1 [:i.fa.fa-group.icon]
        [:span.title (t :social/Connections)]]
       [:div.content;.connections-block;.block-content
        [user-connections-stats]
        [:div.info-block.badge-connection
         [:a {:href (path-for "/connections/badge")}
          [:div.info
           [:i.fa.fa-certificate.icon]
           [:div.text
            [:p.num (get-in @state [:connections :badges])]
            [:p.desc (t :badge/Badges)]]]]]
        [:div.info-block.endorsement
         [:a {:href (path-for "/connections/endorsement")}
          [:div.info
           [:i.fa.fa-thumbs-up.icon]
           [:div.text
            [:p.num (:endorsing @state)]
            [:p.desc "Endorsing" #_(t :badge/Endorsing)]]]]]

        [:div.info-block.endorsement
         [:a {:href (path-for "/connections/endorsement")}
          [:div.info
           (when (pos? (:pending-endorsements @state)) [:span.badge (:pending-endorsements @state)])
           [:i.fa.fa-thumbs-up.icon]

           [:div.text
            [:p.num (:endorsers @state)]
            [:p.desc "Endorsers" #_(t :badge/Endorsing)]
            ]]]]]]]]]])

(defn profile-block [state]
  (let [user (:user-profile @state)]
    [:div.box.col-md-3
     [:div {:id "box_3"}
      [:div.col-md-12.block
       [:div.profile-block.row_1;.block-content
        [:div.heading_1
         [:i.fa.fa-user.icon]
         [:span.title
          (t :user/Profile)]]
        [:div.content
         (when-not (not-activated?) [:a.btn.button {:href (path-for "/user/edit/profile")} (t :page/Edit)])
         [:div.visibility
          (case (get-in user [:user :profile_visibility])
            "public" [:div [:i.fa.fa-eye.icon] [:span.text (t :core/Public)]]
            "internal" [:div [:i.fa.fa-eye-slash.icon ] [:span.text (t :core/Internal)]]
            nil)]

         [:div
          [:img.img-rounded {:src (profile-picture (get-in user [:user :profile_picture]))}]
          [:div.stats
           [:div.text
            [:p.num (:pages_count @state)]
            [:p.desc (t :page/Pages)]]
           [:div.text
            [:p.num (:files_count @state)]
            [:p.desc (t :file/Files)]]]]]]]]]))

(defn help-block [state]
  [:div {:class "box col-md-3"}
   [:div#box_3
    [:div.col-md-12.block
     [:div.row_2.help;.getting-started.block-content
      [:div.heading_1
       [:i.fa.fa-info-circle.icon]
       [:span.title.help (t :core/help)]]
      [:div.content
       [:a {:href (str (path-for "/user/profile/") (session/get-in [:user :id]))}[:p (t :social/Iwanttoseeprofile)]]
       [:a {:href (str (path-for "/user/edit/profile"))}[:p (t :social/Iwanttoeditprofile)]]
       [:a {:href (str (path-for "/badge"))}[:p (t :social/Iwanttoseemysbadges)]]
       (when (some #(= % :extra/application) (session/get :plugins))[:p (t :social/Iwanttoearnnewbadges)])
       [:a {:href (str (path-for "/gallery/badges"))} [:p (t :social/Iwanttofindbadges)]]]]]]])

(defn content [state]
  [:div#dashboard-container
   [m/modal-window]
   (if (not-activated?)
     (not-activated-banner))
   [welcome-block state]
   [:div.row
    [notifications-block state]
    [badges-block state]
    [profile-block state]]

   [:div.row
    ;[profile-block state]
    [explore-block state]
    [connections-block state]
    [help-block]]])


(defn handler [site-navi]
  (let [state (atom {})]
    (init-dashboard state)
    (fn [] (layout/dashboard site-navi [content state]))))

