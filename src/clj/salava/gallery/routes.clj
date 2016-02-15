(ns salava.gallery.routes
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [salava.core.layout :as layout]
            [salava.gallery.db :as g]
            [salava.core.access :as access]
            salava.core.restructure))

(defn route-def [ctx]
  (routes
    (context "/gallery" []
             (layout/main ctx "/")
             (layout/main ctx "/badges")
             (layout/main ctx "/badges/:user-id")
             (layout/main ctx "/badgeview/:badge-id")
             (layout/main ctx "/pages")
             (layout/main ctx "/pages/:user-id")
             (layout/main ctx "/profiles")
             (layout/main ctx "/getbadge"))

    (context "/obpv1/gallery" []
             :tags ["gallery"]
             (POST "/badges" []
                   ;:return [}
                   :body-params [country :- (s/maybe s/Str)
                                 badge :- (s/maybe s/Str)
                                 issuer :- (s/maybe s/Str)
                                 recipient :- (s/maybe s/Str)]
                   :summary "Get public badges"
                   :auth-rules access/authenticated
                   :current-user current-user
                   (let [countries (g/badge-countries ctx (:id current-user))
                         current-country (if (empty? country)
                                           (:user-country countries)
                                           country)]
                     (ok (into {:badges (g/public-badges ctx current-country badge issuer recipient)} countries))))
             (POST "/badges/:userid" []
                   ;:return []
                   :path-params [userid :- s/Int]
                   :summary "Get user's public badges."
                   :auth-rules access/authenticated
                   (ok (hash-map :badges (g/public-badges-by-user ctx userid))))

             (GET "/public_badge_content/:badge-content-id" []
                  :return {:badge        {:name           s/Str
                                          :image_file     (s/maybe s/Str)
                                          :description    (s/maybe s/Str)
                                          :average_rating (s/maybe s/Num)
                                          :rating_count   (s/maybe s/Int)
                                          :recipient      (s/maybe s/Int)
                                          :issuer_content_name (s/maybe s/Str)
                                          :issuer_content_url  (s/maybe s/Str)
                                          :issuer_contact (s/maybe s/Str)
                                          :html_content   (s/maybe s/Str)
                                          :criteria_url   (s/maybe s/Str)}
                           :public_users (s/maybe [{:id s/Int
                                                    :first_name s/Str
                                                    :last_name s/Str}])
                           :private_user_count (s/maybe s/Int)}
                  :path-params [badge-content-id :- s/Str]
                  :summary "Get public badge data"
                  :auth-rules access/authenticated
                  :current-user current-user
                  (ok (g/public-badge-content ctx badge-content-id (:id current-user))))

             (POST "/pages" []
                   :body-params [country :- (s/maybe s/Str)
                                 owner :- (s/maybe s/Str)]
                   :summary "Get public pages"
                   :auth-rules access/authenticated
                   :current-user current-user
                   (let [countries (g/page-countries ctx (:id current-user))
                         current-country (if (empty? country)
                                           (:user-country countries)
                                           country)]
                     (ok (into {:pages (g/public-pages ctx current-country owner)} countries))))

             (POST "/pages/:userid" []
                   :path-params [userid :- s/Int]
                   :summary "Get user's public pages."
                   :auth-rules access/authenticated
                   (ok (hash-map :pages (g/public-pages-by-user ctx userid)))))))
