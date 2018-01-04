(ns salava.user.ui.edit
  (:require [reagent.core :refer [atom cursor]]
            [reagent.session :as session]
            [clojure.string :refer [blank?]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.helper :refer [input-valid? js-navigate-to path-for plugin-fun]]
            [salava.core.ui.layout :as layout]
            [salava.core.i18n :refer [t translate-text]]
            [salava.core.common :refer [deep-merge]]
            [salava.user.schemas :as schemas]
            [salava.core.helper :refer [dump]]
            [salava.core.countries :refer [all-countries-sorted]]
            [salava.user.ui.input :as input]))


(defn save-user-info [state]
  (let [params (:user @state)]
    (ajax/POST
      (path-for "/obpv1/user/edit")
      {:params  (-> params
                    (dissoc :private)
                    (dissoc :password?)
                    (dissoc :activated)
                    (dissoc :role))
       :handler (fn [data]
                  (if (= (:status data) "success")
                    (js-navigate-to "/user/edit")
                    (do
                      (swap! state assoc :message {:class "alert-danger" :content (:message data)})
                      ))
                  )})))



(defn user-connect-config []
  (let [connections (first (plugin-fun (session/get :plugins) "block" "userconnectionconfig"))]
    (if connections
      [connections]
      [:div ""])))

(defn content [state]
  (let [language-atom (cursor state [:user :language])
        first-name-atom (cursor state [:user :first_name])
        last-name-atom (cursor state [:user :last_name])
        country-atom (cursor state [:user :country])
        message (:message @state)

        email-notifications-atom (cursor state [:user :email_notifications])]
    [:div {:class "panel" :id "edit-user"}
     (if message
       [:div {:class (str "alert " (:class message))}
       (translate-text (:content message)) ])
     [:div {:class "panel-body"}
      [:form.form-horizontal
       [:div {:class "form-group row_reverse"}
        [:label {:for "languages"
                 :class "col-md-3"}
         (t :user/Language)]
        [:div.col-md-9
         [input/radio-button-selector (:languages @state) language-atom]]]

       [:div{:class "form-group row_reverse"}
        [:label {:for "input-first-name" :class "col-md-3"} (t :user/Firstname)]
        [:div {:class "col-md-9"}
         [input/text-field {:name "first-name" :atom first-name-atom}]]]

       [:div{:class "form-group row_reverse"}
        [:label {:for "input-last-name" :class "col-md-3"} (t :user/Lastname)]
        [:div {:class "col-md-9"}
         [input/text-field {:name "last-name" :atom last-name-atom}]]]

       [:div{:class "form-group row_reverse"}
        [:label {:for "input-country"
                 :class "col-md-3"}
         (t :user/Country)]
        [:div.col-md-9
         [input/country-selector country-atom]]]
       (if (:email-notifications @state)
         [:div.form-group
          [:label {:for   "input-email-notifications"
                   :class "col-md-3"}
           (t :user/Emailnotifications)]
          [:div.col-md-9
           [:label
            [:input {:name      "visibility"
                     :type      "checkbox"
                     :on-change #(reset! email-notifications-atom (if @email-notifications-atom false true)) ;#(toggle-visibility visibility-atom)
                     :checked   @email-notifications-atom}] (str " ") (if @email-notifications-atom  (t :user/Active) (t :user/Deactive))]
           (if @email-notifications-atom
             [:div (t :user/Emailnotificationsactivetip)]
             [:div (t :user/Emailnotificationsdeactivetip)])
           ]])
       (user-connect-config)
       [:div.row
        [:div.col-xs-12
         [:button {:class "btn btn-primary"
                   :disabled (if-not (and (input/first-name-valid? @first-name-atom)
                                          (input/last-name-valid? @last-name-atom)
                                          (input/country-valid? @country-atom)

                                          )
                               "disabled")
                   :on-click #(do
                               (.preventDefault %)
                               (save-user-info state))}
          (t :core/Save)]]]]]]))

(def initial-state
  {:message nil})

(defn init-data [state]
  (ajax/GET
    (path-for "/obpv1/user/edit" true)
    {:handler (fn [data]
                (reset! state (deep-merge initial-state (update-in data [:user] dissoc :id))))}))

(defn handler [site-navi]
  (let [state (atom {:user {:role ""
                            :first_name ""
                            :private false
                            :language "en"
                            :last_name ""
                            :country "EN"
                            :email_notification nil}
                     :message nil
                     :languages ["en"]
                     :email-notifications false})]
    (init-data state)
    (fn []
      (layout/default site-navi (content state)))))
