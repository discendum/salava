(ns salava.badgeIssuer.db
 (:require
  [clojure.data.json :as json]
  [clojure.string :refer [blank?]]
  [clojure.tools.logging :as log]
  [salava.core.util :refer [get-db file-from-url-fix get-full-path md->html]]
  [slingshot.slingshot :refer :all]
  [yesql.core :refer [defqueries]]))

(defqueries "sql/badgeIssuer/main.sql")

(defn user-selfie-badges [ctx user-id]
  (let [selfies (get-user-selfie-badges {:creator_id user-id} (get-db ctx))]
   (->> selfies (reduce (fn [r s]
                         (conj r (-> s (assoc :tags (if (blank? (:tags s)) nil (json/read-str (:tags s)))
                                              :criteria_html (md->html (:criteria s))))))
                  []))))

(defn user-selfie-badge [ctx user-id id]
  (get-selfie-badge {:id id} (get-db ctx)))

(defn selfie-badge [ctx id]
 (get-selfie-badge {:id id} (get-db ctx)))

(defn delete-selfie-badge [ctx user-id id]
  (try+
    (hard-delete-selfie-badge! {:id id :creator_id user-id} (get-db ctx))
    {:status "success"}
    (catch Object _
      {:status "error"})))

#_(defn update-criteria-url! [ctx user-badge-id]
    (let [badge-id (select-badge-id-by-user-badge-id {:user_badge_id user-badge-id} (into {:result-set-fn first :row-fn :badge_id} (get-db ctx)))
          criteria_content_id (select-criteria-content-id-by-badge-id {:badge_id badge-id} (into {:result-set-fn first :row-fn :criteria_content_id} (get-db ctx)))
          url (str (get-full-path ctx) "/selfie/criteria/" criteria_content_id"?bid="badge-id)]
     (update-badge-criteria-url! {:id criteria_content_id :url url} (get-db ctx))))

(defn finalise-user-badge! [ctx data]
  (finalise-issued-user-badge! data (get-db ctx)))

(defn delete-user-selfie-badges! [ctx user-id]
  (delete-selfie-badges-all! {:user-id user-id} (get-db ctx)))

(defn map-badges-issuable [ctx gallery_ids badges]
  (let [_ (select-issuable-gallery-badges {:gallery_ids gallery_ids} (get-db ctx))]
    (->> badges
         (map #(assoc % :selfie_id (some (fn [b] (when (= (:gallery_id %) (:gallery_id b))
                                                   (:selfie_id b))) _))))))
