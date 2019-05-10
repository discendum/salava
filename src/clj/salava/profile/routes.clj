(ns salava.profile.routes
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [salava.core.layout :as layout]
            [salava.core.util :refer [get-base-path]]
            [schema.core :as s]
            [salava.profile.db :as p]
            [salava.core.access :as access]
            [salava.profile.schemas :as schemas]
            salava.core.restructure))

(defn route-def [ctx]
  (routes
    (context "/profile" []
             (layout/main-meta ctx "/:id" :user)
             (layout/main-meta ctx "/:id/embed" :user))
    (context "/obpv1/profile" []
             :tags ["profile"]
             (GET "/:userid" []
                  :summary "Get user information and profile fields"
                  :path-params [userid :- s/Int]
                  :current-user current-user
                  (let [profile (p/user-information-and-profile ctx userid (:id current-user))
                        visibility (get-in profile [:user :profile_visibility])]
                    (if (or (= visibility "public")
                            (and (= visibility "internal") current-user))
                      (ok profile)
                      (unauthorized))))
             (GET "/user/edit" []
                  ;:return
                  :summary "Get user information and profile fields for editing"
                  :auth-rules access/signed
                  :current-user current-user
                  (ok (p/user-profile-for-edit ctx (:id current-user))))

             (POST "/user/edit" []
                   :return {:status (s/enum "success" "error") :message s/Str}
                   :body-params [profile_visibility :- (:profile_visibility schemas/User)
                                 profile_picture :- (:profile_picture schemas/User)
                                 about :- (:about schemas/User)
                                 fields :- [{:field (apply s/enum (map :type schemas/additional-fields)) :value (s/maybe s/Str)}]
                                 blocks :- [(s/maybe (:block schemas/BlockForEdit))]
                                 theme :- s/Int
                                 tabs :- [(s/maybe {:id s/Int :name s/Str :visibility s/Str})]] ;;refactor to schemas page...

                   :summary "Save user profile"
                   :auth-rules access/authenticated
                   :current-user current-user
                   (ok (p/save-user-profile ctx profile_visibility profile_picture about fields blocks theme tabs (:id current-user)))))))
