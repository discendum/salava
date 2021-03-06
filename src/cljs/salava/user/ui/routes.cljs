(ns salava.user.ui.routes
  (:require [salava.core.ui.layout :as layout]
            [salava.core.i18n :as i18n :refer [t]]
            [salava.core.ui.helper :refer [base-path path-for]]
            [salava.user.ui.login :as login]
            [salava.user.ui.activate :as password]
            [salava.user.ui.profile :as profile]
            [salava.user.ui.embed :as embed]
            [salava.user.ui.register :as register]
            [salava.user.ui.reset :as reset]
            [salava.user.ui.edit :as edit]
            [salava.user.ui.edit-password :as edit-password]
            [salava.user.ui.email-addresses :as email-addresses]
            [salava.user.ui.edit-profile :as edit-profile]
            [salava.user.ui.cancel :as cancel]
            [salava.user.ui.modal :as usermodal]
            [salava.user.ui.data :as data]
            [salava.user.ui.terms :as terms]
            [salava.user.ui.delete-user :as delete-user]
            [salava.user.ui.register-complete :as rc]
            [reagent.session :as session]
            [salava.user.ui.external :as ext]))

(defn placeholder [content]
  (fn [site-navi params]
    #(layout/default site-navi content)))

(defn ^:export routes [context]
  {(str (base-path context) "/user") [[["/profile/" [#"\d+" :user-id]] profile/handler]
                                      [["/profile/" [#"\d+" :user-id] "/embed"] embed/handler]
                                      ["/login" login/handler]
                                      [["/login/" :lang] login/handler]
                                      [["/activate/" :user-id "/" :timestamp "/" :code] password/handler]
                                      [["/activate/" :user-id "/" :timestamp "/" :code "/" :lang] password/handler]
                                      ["/register" register/handler]
                                      [["/register/" :lang] register/handler]
                                      ["/reset" reset/handler]
                                      [["/reset/" :lang] reset/handler]
                                      ["/edit" edit/handler]
                                      ["/edit/password" edit-password/handler]
                                      ["/edit/email-addresses" email-addresses/handler]
                                      ["/edit/profile" edit-profile/handler]
                                      ["/cancel" cancel/handler]
                                      ["/terms" terms/handler]
                                      [["/delete-user/" :lang] delete-user/handler]
                                      [["/data/" [#"\d+" :user-id]] data/handler]
                                      [["/external/data/" [#"\S+" :id]] ext/handler]
                                      ["/registration-complete" rc/handler]]})

(defn about []
  {:edit {:heading (t :user/User " / " :user/Accountsettings)
          :content [:div
                    [:p.page-tip (t :user/Aboutaccountsettings)]]}
   :password {:heading (t :user/User " / " :user/Passwordsettings)
              :content [:div
                        [:p.page-tip (t :user/Aboutpasswordsettings)]]}
   :email {:heading (t :user/User " / " :user/Emailaddresses)
           :content [:div
                     [:p.page-tip (t :user/Aboutemailaddresses)]
                     [:p (t :user/Aboutemailaddresses2)]
                     [:p (t :user/Emailaddresstip)]]}

   :cancel {:heading (t :user/User " / " :user/Cancelaccount)
            :content [:div
                      [:p.page-tip  (t :user/Aboutremoveaccount)]
                      [:p (t :user/Removeaccountinstruction2)]]}
   :data {:heading (t :user/User " / " :user/Mydata)
          :content [:div
                    [:p.page-tip (t :user/Aboutmydata)]]}})


(defn ^:export navi [context]
  {(str (base-path context) "/user/edit/profile")                          {:breadcrumb (t :user/User " / " :user/Editprofile)}
   (str (base-path context) "/user/edit")                                  {:weight 41 :title (t :user/Accountsettings) :site-navi true :breadcrumb (t :user/User " / " :user/Accountsettings) :about (:edit (about))}
   (str (base-path context) "/user/edit/password")                         {:weight 42 :title (t :user/Passwordsettings) :site-navi true :breadcrumb (t :user/User " / " :user/Passwordsettings) :about (:password (about))}
   (str (base-path context) "/user/edit/email-addresses")                  {:weight 43 :title (t :user/Emailaddresses) :site-navi true :breadcrumb (t :user/User " / " :user/Emailaddresses) :about (:email (about))}
   (str (base-path context) "/user/cancel")                                {:weight 49 :title (t :user/Cancelaccount) :site-navi true :breadcrumb (t :user/User " / " :user/Cancelaccount) :about (:cancel (about))}
   (str (base-path context) "/user/data/" (get-in context [:user :id]))     {:weight 50 :title (t :user/Mydata) :site-navi true :breadcrumb (t :user/User " / " :user/Mydata) :about (:data (about))}})


(defn ^:export quicklinks []
  [{:title [:p (t :social/Iwanttomanagemyaccount)]
    :url (str (path-for "/user/edit"))
    :weight 8}])
