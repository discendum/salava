(ns salava.extra.spaces.routes
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.swagger.upload :as upload]
            [schema.core :as s]
            [salava.core.helper :refer [dump]]
            [salava.core.layout :as layout]
            [salava.extra.spaces.space :as space]
            [salava.extra.spaces.db :as db]
            [salava.extra.spaces.util :as util]
            [salava.core.access :as access]
            [salava.extra.spaces.schemas :as schemas] ;cljc
            [clojure.string :refer [split]]))

(defn route-def [ctx]
  (routes
   (context "/admin" []
            (layout/main ctx "/spaces")
            (layout/main ctx "/spaces/creator"))

   (context "/obpv1/spaces" []
            :tags ["spaces"]

            (GET "/" []
                  :auth-rules access/admin
                  :summary "Get all spaces"
                  :current-user current-user
                  (ok (db/all-spaces ctx)))

            (GET "/:id" []
                  :auth-rules access/admin
                  :summary "Get space"
                  :path-params [id :- s/Int]
                  :current-user current-user
                  (ok (space/get-space ctx id)))

            (POST "/add_admin/:id" []
                  :return {:status (s/enum  "success" "error") (s/optional-key :message) s/Str}
                  :auth-rules access/admin
                  :summary "Add admin to space"
                  :path-params [id :- s/Int]
                  :body-params [admins :- [s/Int]]
                  :current-user current-user
                  (ok (db/add-space-admins ctx id admins)))

            (POST "/create" []
                  :return {:status (s/enum  "success" "error") (s/optional-key :message) s/Str}
                  :body [space schemas/create-space]
                  :auth-rules access/admin
                  :summary "Create new space"
                  :current-user current-user
                  (ok (space/create! ctx space)))

            (POST "/downgrade/:id/:admin-id" []
                :return {:status (s/enum "success" "error")}
                :path-params [id :- s/Int
                              admin-id :- s/Int]
                :summary "downgrade admin to space member"
                :auth-rules access/admin
                :current-user current-user
                (ok (space/downgrade! ctx id admin-id)))

            (POST "/edit/:id" []
                  :return {:status (s/enum  "success" "error") (s/optional-key :message) s/Str}
                  :auth-rules access/admin
                  :path-params [id :- s/Str]
                  :body [space schemas/edit-space]
                  :summary "Edit space"
                  :current-user current-user
                  (ok (space/edit! ctx id space (:id current-user))))

            (POST "/update_status/:id" []
                  :return {:status (s/enum "success" "error")}
                  :auth-rules access/admin
                  :path-params [id :- s/Str]
                  :body-params [status :- (s/enum "active" "deleted" "suspended")]
                  :summary "Update space status"
                  :current-user current-user
                  (ok (space/update-status! ctx id status (:id current-user))))

            #_(POST "/suspend/:id" []
                    :return {:success s/Bool}
                    :summary "Suspend space"
                    :path-params [id :- s/Str]
                    :current-user current-user
                    (ok (space/suspend! ctx id (:id current-user))))

            (POST "/upload_image/:kind" []
                  :return {:status (s/enum "success" "error") :url s/Str (s/optional-key :message) (s/maybe s/Str)}
                  :multipart-params [file :- upload/TempFileUpload]
                  :path-params [kind :- (s/enum "logo" "banner")]
                  :middleware [upload/wrap-multipart-params]
                  :summary "Upload badge image (PNG)"
                  :auth-rules access/authenticated
                  :current-user current-user
                  (ok (util/upload-image ctx current-user file kind)))

            (DELETE "/delete/:id" []
                    :return {:status (s/enum "success" "error")}
                    :summary "Delete space"
                    :path-params [id :- s/Str]
                    :auth-rules access/admin
                    :current-user current-user
                    (ok (space/delete! ctx id (:id current-user)))))))
