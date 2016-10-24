(ns salava.social.ui.stream
   (:require [salava.core.ui.layout :as layout]
             [salava.core.i18n :as i18n :refer [t]]
             [salava.core.ui.ajax-utils :as ajax]
             [reagent.core :refer [atom cursor]]
             [salava.social.ui.badge-message-modal :refer [badge-message-stream-link]]
             [salava.user.ui.helper :refer [profile-picture]]
             [reagent-modals.modals :as m]
             [salava.core.helper :refer [dump]]
             [salava.core.time :refer [date-from-unix-time]]
             [salava.user.ui.helper :refer [profile-picture]]
             [salava.gallery.ui.badges :as b]
             [salava.core.ui.helper :as h :refer [unique-values navigate-to path-for]]))

(defn init-data [state]
  (ajax/GET
    (path-for "/obpv1/social/events" true)
    {:handler (fn [data]
                (swap! state assoc :events data))}))


(defn message-item [{:keys [message first_name last_name ctime id profile_picture user_id]}]
  [:div {:class "media" :key id}
   [:span {:class "pull-left"}
    [:img {:class "message-profile-img" :src (profile-picture profile_picture)}]]
   [:div {:class "media-body"}
    [:h4 {:class "media-heading"}
    [:a {:href (path-for (str "/user/profile/" user_id)) :target "_blank"}(str first_name " "last_name)]
    ;[:span (str (t :social/Commented) ":") ]
    ]
                                        ;(date-from-unix-time (* 1000 ctime) "minutes")
    (into [:div] (for [ item (clojure.string/split-lines message)]
                   [:p item]))]
   ]
  )

(defn stream-follow-item [event state]
  (let [{:keys [subject verb image_file message ctime event_id name object]}  event
        modal-message (str "messages")]
    [:div {:class "media message-item follow"}
    [:button {:type       "button"
                :class      "close"
                :aria-label "OK"
                :on-click   #(do
                               (ajax/POST
                                (path-for (str "/obpv1/social/hide_event/" event_id))
                                {:response-format :json
                                 :keywords?       true          
                                 :handler         (fn [data]
                                                    (do
                                                      (init-data state)))
                                 :error-handler   (fn [{:keys [status status-text]}]
                                                    )})
                               (.preventDefault %))
                }
       [:span {:aria-hidden "true"
               :dangerouslySetInnerHTML {:__html "&times;"}}]]
     [:div.media-left
      [:a {:href "#"
           :on-click #(do
                        (b/open-modal object false init-data state)
                        (.preventDefault %) )}
       [:img {:src (str "/" image_file)} ]]]
     [:div.media-body
      [:div.date (date-from-unix-time (* 1000 ctime) "days") ]
      [:h3 {:class "media-heading"}
       
       [:a {:href "#"
           :on-click #(do
                        (b/open-modal object false init-data state)
                        (.preventDefault %) )} (str  name)]]
       (t :social/Youstartedfollowbadge)
      ]]))

(defn stream-message-item [event state]
  (let [{:keys [subject verb image_file message ctime event_id name object]}  event
        new-messages  (get-in event [:message :new_messages])
        modal-message (if (pos? new-messages)
                              [:span (t :social/Newmessages) [:span.badge new-messages]]
                              (t :social/Readmore))
        ;(str  (t :social/Readmore) (if (pos? new-messages) (str " (" new-messages " " (if (= 1 new-messages) (t :social/Newmessage) (t :social/Newmessages)) ")")))
        ]
    [:div {:class (if (pos? new-messages) "media message-item new " "media message-item" )}
    [:button {:type       "button"
                :class      "close"
                :aria-label "OK"
                :on-click   #(do
                               (ajax/POST
                                (path-for (str "/obpv1/social/hide_event/" event_id))
                                {:response-format :json
                                 :keywords?       true          
                                 :handler         (fn [data]
                                                    (do
                                                      (init-data state)))
                                 :error-handler   (fn [{:keys [status status-text]}]
                                                    )})
                               (.preventDefault %))
                }
       [:span {:aria-hidden "true"
               :dangerouslySetInnerHTML {:__html "&times;"}}]]
     [:div.media-left
      [:a {:href "#"
           :on-click #(do
                        (b/open-modal object true init-data state)
                        (.preventDefault %) )} 
       [:img {:src (str "/" image_file)} ]]]
     [:div.media-body
      [:div.date (date-from-unix-time (* 1000 ctime) "days") ]
      [:h3 {:class "media-heading"}
      ;(if (pos? new-messages) [:span.new  (str new-messages " ") (if (= 1 new-messages) (t :social/New) (t :social/News))])
      (if (pos? new-messages) [:span.new  new-messages])
       [:a {:href "#"
           :on-click #(do
                        (b/open-modal object true init-data state)
                        (.preventDefault %) )} name]]
      (message-item message)
      [badge-message-stream-link modal-message (:object event) init-data state]
      ]]))

(defn content [state]
  (let [events (:events @state)]
    [:div {:class "my-badges pages"}
     [m/modal-window]
     (into [:div {:class "row"}]
           (for [event events]
             (cond
               (= "follow" (:verb event)) (stream-follow-item event state)
               (= "message" (:verb event)) (stream-message-item event state)
               :else "")))]))


(defn handler [site-navi]
  (let [state (atom {:events []})]
    (init-data state)
    (fn []
      (layout/default site-navi (content state)))))