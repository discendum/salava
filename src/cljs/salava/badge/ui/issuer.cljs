(ns salava.badge.ui.issuer
  (:require
   [clojure.string :refer [blank? split includes?]]
   [reagent.core :refer [atom cursor]]
   [salava.core.i18n :refer [t]]
   [salava.core.ui.ajax-utils :as ajax]
   [salava.core.ui.helper :refer [path-for private?]]
   [salava.core.ui.modal :as mo]
   [salava.badge.ui.endorsement :as endr]
   [reagent.session :as session]
   [salava.translator.ui.helper :refer [translate]]))

(defn init-issuer-connection [issuer-id state]
  (ajax/GET
   (path-for (str "/obpv1/social/issuer_connected/" issuer-id))
   {:handler (fn [data]
               (swap! state assoc :connected data))}))

(defn init-issuer-content [state issuer-id]
  (ajax/GET
   (path-for (str "/obpv1/badge/issuer/" issuer-id))
   {:handler (fn [data]
               (reset! state data)
               (if (session/get :user) (init-issuer-connection issuer-id state)))}))

(defn init-creator-content [state creator-id]
  (ajax/GET
   (path-for (str "/obpv1/badge/creator/" creator-id))
   {:handler (fn [data]
               (reset! state data))}))

(defn add-issuer-to-favourites [issuer-id state]
  (ajax/POST
   (path-for (str "/obpv1/social/create_connection_issuer/" issuer-id))
   {:handler (fn []
               (init-issuer-connection issuer-id state))}))

(defn remove-issuer-from-favourites [issuer-id state init-fn]
  (ajax/POST
   (path-for (str "/obpv1/social/delete_connection_issuer/" issuer-id))
   {:handler (fn []
               (if init-fn
                 init-fn
                 (init-issuer-connection issuer-id state)))}))

(defn- issuer-image [path name]
  (when (not-empty path)
    [:img.profile-picture
     {:src (if (re-find #"^file/" path) (str "/" path) path)
      :style {:width "50px"}
      :alt name}]))

(defn content [param]
  (let [issuer-id (if (map? param) (:id param) param)
        lang (if (map? param) (-> (:lang param)) nil)
        state (atom {:issuer nil :endorsement []})
        connected? (cursor state [:connected])
        base-url (str (session/get :site-url) (session/get :base-path))]

    (init-issuer-content state issuer-id)
    (fn []
      (let [{:keys [name description email url image_file]} @state
            is-user? (when-not (blank? url) (includes? url (str base-url "/profile/")))
            user-id (when is-user? (->> (split url #"/profile/") last int))]
        [:div.row {:id "badge-contents"}
         [:div.col-xs-12
          [:div.row
           (when (session/get-in [:user :id])
             [:div.pull-right
              (if-not @connected?
                [:a {:href "#" :on-click #(add-issuer-to-favourites issuer-id state)} [:i {:class "fa fa-bookmark-o"}] (str " " (t :badge/Addtofavourites))]
                [:a {:href "#" :on-click #(remove-issuer-from-favourites issuer-id state nil)} [:i {:class "fa fa-bookmark"}] (str " " (t :badge/Removefromfavourites))])])]
          [:h2.uppercase-header
           (issuer-image image_file name)
           " "
           name]

          [:div.row.flip
           [:div {:class "col-md-9 col-sm-9 col-xs-12"}
            (when-not (blank? url)
              [:div {:class "row"}
               [:div.col-xs-12
                (if is-user?
                  [:div
                   [:a {:href "#"
                        :on-click #(do
                                     (.preventDefault %)
                                     (mo/open-modal [:profile :view] {:user-id user-id}))}
                    (t :user/Viewprofile)]]

                  [:a {:target "_blank" :rel "noopener noreferrer" :href url} url])]])

            (if (not-empty email)
              [:div {:class "row"}
               [:div.col-xs-12 {:style {:margin-bottom "20px"}}
                [:span [:a {:href (str "mailto:" email)} email]]]])

            (if (not-empty description)
              [:div {:class "row about"}
               [:div.col-xs-12 {:dangerouslySetInnerHTML {:__html description}}]])]]

          (when-not (empty? (:endorsement @state))
            [:div.row
             [:div.col-xs-12
              [:hr]
              [:h4 {:style {:margin-bottom "20px"}} (translate lang :badge/IssuerEndorsedBy)]
              (into [:div]
                    (for [endorsement (:endorsement @state)]
                      (endr/endorsement-row endorsement)))]])]]))))

;;TODO
(defn creator-content [creator-id]
  (let [state (atom {})
        base-url (str (session/get :site-url) (session/get :base-path))]
    (init-creator-content state creator-id)
    (fn []
      (let [{:keys [name description email url image_file]} @state
            is-user? (when-not (blank? url) (includes? url (str base-url "/profile/")))
            user-id (when is-user? 12 #_(->> (split url #"/profile/") last int))]

        [:div.row {:id "badge-contents"}
         [:div.col-xs-12
          [:h2.uppercase-header
           (issuer-image image_file name)
           " "
           name]

          [:div.row.flip
           [:div {:class "col-md-9 col-sm-9 col-xs-12"}
            (when-not (blank? url)
              [:div {:class "row"}
               [:div.col-xs-12
                (if is-user?
                  [:div
                   [:a {:href "#"
                        :on-click #(do
                                     (.preventDefault %)
                                     (mo/open-modal [:profile :view] {:user-id user-id}))}
                    (t :user/Viewprofile)]]

                  [:a {:target "_blank" :rel "noopener noreferrer" :href url} url])]])

            (if (not-empty email)
              [:div {:class "row"}
               [:div.col-xs-12 {:style {:margin-bottom "20px"}}
                [:span [:a {:href (str "mailto:" email)} email]]]])

            (if (not-empty description)
              [:div {:class "row about"}
               [:div.col-xs-12 {:dangerouslySetInnerHTML {:__html description}}]])]]

          (when-not (empty? (:endorsement @state))
            [:div.row
             [:div.col-xs-12
              [:hr]
              [:h4 {:style {:margin-bottom "20px"}} (t :badge/IssuerEndorsedBy)]
              (into [:div]
                    (for [endorsement (:endorsement @state)]
                      (endr/endorsement-row endorsement)))]])]]))))
