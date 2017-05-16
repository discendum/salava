(ns salava.user.ui.register
  (:require [reagent.core :refer [atom cursor]]
            [reagent.session :as session]
            [clojure.string :as string]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.layout :as layout]
            [salava.core.helper :refer [dump]]
            [salava.core.ui.helper :refer [path-for current-path base-path js-navigate-to path-for private? plugin-fun]]
            [salava.core.countries :refer [all-countries-sorted]]
            [salava.oauth.ui.helper :refer [facebook-link linkedin-link ]]
            [salava.core.i18n :refer [t translate-text]]
            [salava.core.ui.error :as err]
            [salava.user.ui.input :as input]))

(defn follow-up-url []
  (let [referrer js/document.referrer
        site-url (str (session/get :site-url) (base-path))
        path (if (and referrer site-url) (string/replace referrer site-url ""))]
    #_(if (or (= "/user/login" path) (empty? path) (= referrer path) (= path (path-for "/user/login")))
      "/social/stream"
      path)
    "/social/stream"
    ))

(defn send-registration [state]
  (let [{:keys [email first-name last-name country language password password-verify]} @state
        token (last (re-find #"/user/register/token/([\w-]+)"  (str (current-path))))]
    (ajax/POST
     (path-for "/obpv1/user/register/")
     {:params  {:email email
                :first_name first-name
                :last_name last-name
                :country country
                :token token
                :language language
                :password password
                :password_verify password-verify}
      :handler (fn [data]
                 (if (= (:status data) "error")
                   (swap! state assoc :error-message (:message data))
                   (js-navigate-to (follow-up-url))))})))

(defn registration-form [state]
  (let [email-atom (cursor state [:email])
        first-name-atom (cursor state [:first-name])
        language-atom (cursor state [:language])
        last-name-atom (cursor state [:last-name])
        country-atom (cursor state [:country])
        languages (:languages @state)
        email-whitelist (:email-whitelist @state)
        password-atom (cursor state [:password])
        password-verify-atom (cursor state [:password-verify])]
    [:form {:class "form-horizontal"}
     (if (:error-message @state)
       [:div {:class "alert alert-danger" :role "alert"}
        (translate-text (:error-message @state))])

     [:div.form-group
      [:label {:class "col-sm-4"
               :for "input-email"}
       (t :user/Email)
       [:span.form-required " *"]]
      [:div.col-sm-8
       [:div {:class (str "form-bar " (if (input/email-valid? @email-atom) "form-bar-success" "form-bar-error"))}
        (if email-whitelist
          [input/email-whitelist email-whitelist email-atom]
          
          [input/text-field {:name "email" :atom email-atom}])]]
      #_[:div.col-xs-12
       (t :user/Emailinfotext)]]

      [:div.form-group
      [:label {:class "col-xs-4"
               :for "input-password"}
       (t :user/Password)
       [:span.form-required " *"]]
      [:div.col-xs-8
       [:input {:class "form-control"
                :id "input-password"
                :type "password"
                :name "password"
                :on-change #(reset! password-atom (.-target.value %))
                :value @password-atom}]]]
     [:div.form-group
      [:label {:class "col-xs-4"
               :for "input-password-verify"}
       (t :user/Verifypassword)
       [:span.form-required " *"]]
      [:div.col-xs-8
       [:input {:class "form-control"
                :id "input-password-verify"
                :type "password"
                :name "password-verify"
                :on-change #(reset! password-verify-atom (.-target.value %))
                :value @password-verify-atom}]]
      [:div " Tähä jotain välii"]]

     [:div.form-group
      [:label {:class "col-sm-4"
               :for "input-first-name"}
       (t :user/Firstname)
       [:span.form-required " *"]]
      [:div.col-sm-8
       [:div {:class (str "form-bar " (if (input/first-name-valid? @first-name-atom) "form-bar-success" "form-bar-error"))}
        [input/text-field {:name "first-name" :atom first-name-atom}]]]]

     [:div.form-group
      [:label {:class "col-sm-4"
               :for "input-last-name"}
       (t :user/Lastname)
       [:span.form-required " *"]]
      [:div.col-sm-8
       [:div {:class (str "form-bar " (if (input/last-name-valid? @last-name-atom) "form-bar-success" "form-bar-error"))}
        [input/text-field {:name "last-name" :atom last-name-atom}]]]]

     

     [:fieldset.form-group
      [:legend {:class "col-sm-4"
               :for "input-language"}
       (t :user/Language)
       [:span.form-required " *"]]
      [:div.col-sm-8
       [:div {:class (str "form-bar " (if (input/language-valid? @language-atom) "form-bar-success" "form-bar-error"))}
        [input/radio-button-selector languages language-atom]]]]

     [:div.form-group
      [:label {:class "col-sm-4"
               :for "input-country"}
       (t :user/Country)
       [:span.form-required " *"]]
      [:div.col-sm-8
       [:div {:class (str "form-bar " (if (input/country-valid? @country-atom) "form-bar-success" "form-bar-error"))}
        [input/country-selector country-atom]]]]

     [:button {:class    "btn btn-primary col-sm-4 col-sm-offset-4 col-xs-8 col-xs-offset-2"
               :disabled (if-not (and (input/email-valid? @email-atom)
                                      (input/first-name-valid? @first-name-atom)
                                      (input/last-name-valid? @last-name-atom)
                                      (input/country-valid? @country-atom)
                                      (input/language-valid? @language-atom))
                           "disabled")
               :on-click #(do
                           (.preventDefault %)
                           (send-registration state))}
      (t :user/Createnewaccount)]]))

(defn oauth-registration-form []
  [:div {:class "row"}
   [:div {:class "col-xs-6"} (facebook-link false true)]
   [:div.col-sm-6 (linkedin-link nil "register")]])

(defn registeration-content [state]
  [:div
   (oauth-registration-form)
   (if (some #(= % "oauth") (session/get-in [:plugins :all]))
       [:div {:class "or"} (t :user/or)])
   (registration-form state)])

(defn content [state]
  [:div {:id "registration-page"}
   [:div {:id "narrow-panel"
          :class "panel"}
    [:div.panel-body
     (if (:registration-sent @state)
       [:div {:class "alert alert-success"
              :role "alert"}
        (t :user/Welcomemessagesent) "."]
       (registeration-content state))]]])


(defn init-data [state]
  (ajax/GET
    (path-for "/obpv1/user/register" true)
    {:handler (fn [data]
                (let [{:keys [languages]} data]
                  (swap! state assoc :languages languages :permission "success")))}
    (fn [] (swap! state assoc :permission "error"))))

(defn handler [site-navi params]
  (let [state (atom {:permission "initial"
                     :email ""
                     :first-name ""
                     :last-name ""
                     :language ""
                     :country ""
                     :languages []
                     :error-message nil
                     :registration-sent nil
                     :password ""
                     :password-verify ""})
        lang (:lang params)]
    (when (and lang (some #(= lang %) (session/get :languages)))
      (session/assoc-in! [:user :language] lang)
      (swap! state assoc :language lang))
    (init-data state)
    
    (fn []
      (cond
        (= "initial" (:permission @state)) (layout/landing-page site-navi [:div])
        (= "success" (:permission @state))  (layout/landing-page site-navi (content state))
        :else  (layout/landing-page site-navi  (err/error-content)))
      
      )))
