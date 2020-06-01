(ns salava.extra.spaces.db
  (:require [yesql.core :refer [defqueries]]
            [salava.core.util :as u]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [salava.extra.spaces.util :refer [save-image!]]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer :all]))

(defqueries "sql/extra/spaces/main.sql")
(defrecord Space_member [id user_id space_id role default_space])
(defrecord Pending_admin [id space_id email])

(defn space-property [ctx id property]
  (when-let [value (select-space-property {:id id :name property} (into {:result-set-fn first :row-fn :value} (u/get-db ctx)))]
    (json/read-str value :key-fn keyword)))

(defn save-space-property [ctx id property value]
  (prn id)
  (insert-space-property! {:space_id id :name property :value (json/write-str value)} (u/get-db ctx)))

(defn space-admins [ctx id]
  (select-space-admins {:space_id id} (u/get-db ctx)))

(defn create-space-admin!
  "Adds existing user as a space member and sets role to admin
   Otherwise, adds email as pending admin"
  [ctx space-id email]
  (log/info "Creating space admin " email)
  (if-let [user-id (select-email-address {:email email} (into {:result-set-fn first :row-fn :user_id} (u/get-db ctx)))]
   (create-space-member! (->Space_member nil user-id space-id "admin" 0) (u/get-db ctx))
   (create-pending-space-admin! (->Pending_admin nil space-id email) (u/get-db ctx)))
  (log/info "Space admin " email " created!"))

(defn space-exists?
  "check if space name already exists"
  [ctx space]
  (if (empty? (select-space-by-name {:name (:name space)} (u/get-db ctx))) false true))

(defn get-space-information [ctx id]
  (assoc (select-space-by-id {:id id} (into {:result-set-fn first} (u/get-db ctx)))
    :css (space-property ctx id "css")
    :admins (space-admins ctx id)))

(defn create-new-space!
 "Initializes space and creates admins"
 [ctx space]
 ;(try+
 (log/info "Creating space" (:name space))
 (log/info "Space exists? " (space-exists? ctx space))
 (when (empty? (:admins space)) (throw+ "Error, no space admin defined"))
 (when (space-exists? ctx space) (throw+ (str "Space with name " (:name space) " already exists")))
 (jdbc/with-db-transaction [tx (:connection (u/get-db ctx))]
  (let [space_id (-> space
                   (dissoc :id :admin)
                   (assoc :logo (save-image! ctx (:logo space)) :banner (save-image! ctx (:banner space)))
                   (create-space<! {:connection tx})
                   :generated_key)]
   (doseq [$ (:admins space)
            :let [email (if (number? $) (select-primary-address {:id $} (into {:result-set-fn first :row-fn :email} (u/get-db ctx))))]]
          (create-space-admin! ctx space_id email))
   (when (:css space) (save-space-property ctx space_id "css" (:css space)))

   (log/info "Finished creating space!")))
  ;{:status "success"}
 #_(catch Object _
    (log/error "Error, No space admin defined")
    {:status "error"}))

(defn update-space-info [ctx id space user-id]
  (let [data (assoc space :id id :user_id user-id :last_modified_by user-id :logo (if (re-find #"^data:image" (:logo space)) (save-image! ctx (:logo space)) (:logo space)))]
    (prn space)
    (update-space-information! data (u/get-db ctx))
    (when (:css space) (save-space-property ctx id "css" (:css data)))))

(defn space-id [ctx id]
 (if (uuid? (java.util.UUID/fromString id)) (some-> (select-space-by-uuid {:uuid id} (u/get-db ctx)) :id) id))


(defn clear-space-data!
 "Clear out space information"
 [ctx id]
 (let [id (space-id ctx id)]
   (jdbc/with-db-transaction [tx (:connection (u/get-db ctx))]
          (delete-space! {:id id} {:connection tx})
          (delete-space-members! {:space_id id} {:connection tx})
          (delete-space-properties! {:space_id id} {:connection tx}))))

(defn all-spaces [ctx]
 (select-all-spaces {} (u/get-db ctx)))

(defn active-spaces [ctx]
  (select-all-active-spaces {} (u/get-db ctx)))

(defn suspended-spaces [ctx])
