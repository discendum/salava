(ns salava.user.ui.register-complete
  (:require [reagent.session :as session]
            [reagent.core :refer [atom]]
            [salava.core.ui.layout :as layout]
            [salava.core.ui.error :as err]
            [salava.core.ui.helper :refer [js-navigate-to navigate-to path-for]]
            [salava.core.i18n :refer [t]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.helper :refer [dump]]
            [dommy.core :as dommy :refer-macros [sel sel1]]
            [clojure.string :as string]))

(defn content [state]
  [:div#registration-page
   [:div.panel {:id "narrow-panel"}
    [:div.panel-heading
     [:div.media
      [:div.media-left
       [:div.logo-image-icon-url #_{:style {:vertical-align "middle"}}]]
      [:div.media-body {:style {:vertical-align "bottom"}}
       [:h1 {:style {:font-size "16px" :font-weight "600" :margin "unset" :margin-bottom "2px"}} (str (t :core/Emailactivation2) " " (session/get :site-name) #_(t :core/Service))]]]]
    [:div {:style {:margin-top "10px"}} [:hr.border]]
    [:div.panel-body
     [:div.col-md-12
      [:div
       [:p (t :social/Notactivatedbody1)]
       [:ul
        [:li (t :social/Notactivatedbody2)]
        [:li (t :social/Notactivatedbody3)]
        [:li (t :social/Notactivatedbody4)]
        [:li (t :social/Notactivatedbody5)]
        [:li (t :social/Notactivatedbody6)]]]
      [:div.col-md-12 {:style {:text-align "center"}} [:a.btn.btn-primary {:style {:margin-top "20px" :text-align "center"}
                                                                           :href "#" :on-click #(do
                                                                                                  (.push window.dataLayer (clj->js {:event "virtualPage" :vpv "app/user/register/complete" :registration-method "form"}))
                                                                                                  (.preventDefault %)
                                                                                                  (navigate-to "/social"))}
                                                       (t :core/Continue)]]]]]])

(defn init-data [state]
  (ajax/GET
   (path-for "/obpv1/user/register/complete")
   {:handler (fn [data]
               (swap! state assoc :permission (:status data)))}
   (fn [] (swap! state assoc :permission "error"))))

(defn handler [site-navi params]
  (let [state (atom {:permission "initial"})
        lang (or (:lang params) (session/get-in [:user :language] (-> (or js/window.navigator.userLanguage js/window.navigator.language) (string/split #"-") first)))]
    (when (and lang (some #(= lang %) (session/get :languages)))
      (session/assoc-in! [:user :language] lang)
      (-> (sel1 :html) (dommy/set-attr! :lang lang)))
    (init-data state)
    (fn []
      (cond
        (= "initial" (:permission @state)) (layout/landing-page site-navi [:div])
        (= "success" (:permission @state))  (layout/landing-page site-navi (content state))
        :else  (layout/landing-page site-navi  (err/error-content))))))
