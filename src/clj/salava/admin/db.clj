(ns salava.admin.db
  (:require [yesql.core :refer [defqueries]]
            [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as jdbc]
            [salava.core.helper :refer [dump]]
            [slingshot.slingshot :refer :all]
            [salava.core.util :as util :refer [get-db get-datasource get-site-url get-base-path get-site-name plugin-fun get-plugins get-db-col]]
            [salava.core.time :refer [unix-time get-date-from-today]]
            [salava.core.countries :refer [all-countries sort-countries]]
            [salava.user.db :as u]
            [clojure.tools.logging :as log]
            [salava.badge.main :as b]
            [salava.page.main :as p]
            [salava.mail.mail :as m]
            [salava.gallery.db :as g]
            [salava.admin.helper :refer [make-csv]]
            [salava.core.i18n :refer [t]]
            [clojure.string :refer [blank?]]))

(defqueries "sql/admin/queries.sql")

(defn admin-count
  "Count users with admin role"
  [ctx]
  (:count (select-admin-count {} (util/get-db-1 ctx))))

(defn get-owners [ctx]
  (select-admin-users-id {} (get-db ctx)))

(defn get-user-admin-events [ctx user_id]
  (select-admin-events {:user_id user_id} (get-db ctx)))

(defn get-admin-events [ctx user_id]
  (let [events (get-user-admin-events ctx user_id)]
    events))

#_(defn register-users-count
      "Get count from all active and registered users"
      [ctx]
      (total-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx))))

#_(defn user-stats
    "Get user statistics"
    [ctx last-login]
    {:total (total-user-count-fix {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :activated (activated-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :not-activated (not-activated-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-last-login (count-registered-users-after-date-fix {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-last-month (count-registered-users-after-date-fix {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-3-month (count-registered-users-after-date-fix {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-6-month (count-registered-users-after-date-fix {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-1-year (count-registered-users-after-date-fix {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :login-count-since-last-login (count-logged-users-after-date {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :last-month-login-count (count-logged-users-after-date {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :3-month-login-count (count-logged-users-after-date {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :6-month-login-count (count-logged-users-after-date {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :1-year-login-count (count-logged-users-after-date {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :internal (internal-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :public (public-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))})

(defn user-stats
  "Get user statistics"
  [ctx last-login]
  {:Totalusersno (total-user-count-fix {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :activatedusers (activated-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :notactivatedusers (not-activated-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :userssincelastlogin (count-registered-users-after-date-fix {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :userssincelastmonth (count-registered-users-after-date-fix {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :userssince3month (count-registered-users-after-date-fix {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :userssince6month (count-registered-users-after-date-fix {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :userssince1year (count-registered-users-after-date-fix {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   ;:logincountsincelastlogin (count-logged-users-after-date {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   ;:1monthlogincount (count-logged-users-after-date {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   ;:3monthlogincount (count-logged-users-after-date-range {:from (get-date-from-today -3 0 0) :to (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   ;:6monthlogincount (count-logged-users-after-date-range {:from (get-date-from-today -6 0 0) :to (get-date-from-today -3 0 0) } (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   ;:1yearlogincount (count-logged-users-after-date-range {:from (get-date-from-today -12 0 0) :to (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :internalusers (internal-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
   :publicusers (public-user-count {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))})

#_(defn last-month-users-login-count
    "Get count from all last month logged in users"
    [ctx]
    (let [date (get-date-from-today -1 0 0)]
      (count-logged-users-after-date {:time date} (into {:result-set-fn first :row-fn :count} (get-db ctx)))))

#_(defn last-month-users-registered-count
    "Get count form all last month registered users"
    [ctx]
    (let [date (get-date-from-today -1 0 0)]
      (count-registered-users-after-date {:time date} (into {:result-set-fn first :row-fn :count} (get-db ctx)))))

#_(defn badges-count
    "Get count from all badges"
    [ctx]
    (count-all-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx))))

#_(defn badge-stats
    "Get badge statistics"
    [ctx last-login]
    (let [url-pattern (str (util/get-factory-url ctx) "%")]
     {:total (count-all-badges-fix {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :pending (count-pending-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :accepted (count-accepted-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :declined (count-declined-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :since-last-login (count-all-badges-after-date-fix {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :since-last-month (count-all-badges-after-date-fix {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :since-3-month (count-all-badges-after-date-fix {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :since-6-month (count-all-badges-after-date-fix {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :since-1-year (count-all-badges-after-date-fix {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :private (count-private-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :public (count-public-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :internal (count-internal-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :factory-badges (count-badges-issued-from-url {:url url-pattern} (into {:result-set-fn first :row-fn :count} (get-db ctx)))}))

(defn badge-stats
    "Get badge statistics"
    [ctx last-login]
    (let [url-pattern (str (util/get-factory-url ctx) "%")]
     {:Totalbadgesno (count-all-badges-fix {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :pendingbadgescount (count-pending-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :acceptedbadgescount (count-accepted-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :declinedbadgescount (count-declined-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :badgessincelastlogin (count-all-badges-after-date-fix {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :badgessincelastmonth (count-all-badges-after-date-fix {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :badgessince3month (count-all-badges-after-date-fix {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :badgessince6month (count-all-badges-after-date-fix {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :badgessince1year (count-all-badges-after-date-fix {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :privatebadgescount (count-private-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :publicbadgescount (count-public-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :internalbadgescount (count-internal-badges {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
       :factorybadges (count-badges-issued-from-url {:url url-pattern} (into {:result-set-fn first :row-fn :count} (get-db ctx)))}))

#_(defn last-month-added-badges-count
    "Get count from all last month added badges"
    [ctx]
    (let [date (get-date-from-today -1 0 0)]
      (count-all-badges-after-date {:time date} (into {:result-set-fn first :row-fn :count} (get-db ctx)))))

#_(defn pages-count
    "Get count from all pages have been created"
    [ctx]
    (count-all-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx))))

#_(defn pages-stats
    "get page statistics"
    [ctx last-login]
    {:total (count-all-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-last-login (count-all-pages-after-date {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-last-month (count-all-pages-after-date {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-3-month (count-all-pages-after-date {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-6-month (count-all-pages-after-date {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :since-1-year (count-all-pages-after-date {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :private (count-private-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :public (count-public-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
     :internal (count-internal-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))})

(defn pages-stats
  "get page statistics"
   [ctx last-login]
   {:Totalpagesno (count-all-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :pagessincelastlogin (count-all-pages-after-date {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :pagessincelastmonth (count-all-pages-after-date {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :pagessince3month (count-all-pages-after-date {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :pagessince6month (count-all-pages-after-date {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :pagessince1year (count-all-pages-after-date {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :privatepagescount (count-private-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :publicpagescount (count-public-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
    :internalpagescount (count-internal-pages {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))})

(defn selfie-stats [ctx last-login]
  (as-> (first (plugin-fun (get-plugins ctx) "main" "selfie_stats")) $
        (if (ifn? $) ($ ctx last-login) nil)))

(defn space-stats [ctx  last-login]
  (as-> (first (plugin-fun (get-plugins ctx) "stats" "admin_space_stats")) $
        (if (ifn? $) ($ ctx last-login) nil)))

(defn issuer-stats [ctx last-login]
 {:Totalissuersno (count-badge-issuers {} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
  :issuerssincelastlogin (count-badge-issuers-after-date {:time last-login} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
  :issuerssincelastmonth (count-badge-issuers-after-date {:time (get-date-from-today -1 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
  :issuerssince3month (count-badge-issuers-after-date {:time (get-date-from-today -3 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
  :issuerssince6month (count-badge-issuers-after-date {:time (get-date-from-today -6 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))
  :issuerssince1year (count-badge-issuers-after-date {:time (get-date-from-today -12 0 0)} (into {:result-set-fn first :row-fn :count} (get-db ctx)))})

(defn user-badge-correlation [ctx]
 (let [data (select-user-ids-and-badge-count {} (util/get-db ctx))]
  (->> data
       (group-by :badge_count)
       (reduce-kv (fn [r k v] (conj r {:badge_count k :user_count (count v)})) []))))

#_(defn get-stats [ctx last-login]
      (try+
       {:register-users (register-users-count ctx last-login)
        :last-month-active-users (last-month-users-login-count ctx)
        :last-month-registered-users (last-month-users-registered-count ctx)
        :all-badges (badges-count ctx)
        :last-month-added-badges (last-month-added-badges-count ctx)
        :pages (pages-count ctx)}
       (catch Object _
         "error")))

(defn get-stats [ctx last-login]
  (try+
   (-> {:users (user-stats ctx last-login)
        :userbadges (badge-stats ctx last-login)
        :pages (pages-stats ctx last-login)
        :issuers (issuer-stats ctx last-login)
        :spaces (space-stats ctx last-login)
        :user-badge-correlation (user-badge-correlation ctx)}
       (merge (selfie-stats ctx last-login)))
   (catch Object _
     (log/error (.getMessage _))
     "error")))


(defn export-admin-statistics [ctx user]
  (let [{:keys [id last-visited]} user
        ul (select-user-language {:id id} (into {:result-set-fn first :row-fn :language} (get-db ctx)))
        data (into {} (filter second (-> (get-stats ctx last-visited) (dissoc :user-badge-correlation))))
        data->csvformat (reduce-kv
                          (fn [r k v]
                           (do
                            (when v
                             (conj r
                              [(t (keyword (str "admin/" (name k))) ul)]))
                            (when (map? v)
                             (reduce-kv (fn [_ y z] (conj _ [(t (keyword (str "admin/" (name y))) ul) z])) r v))))
                          []
                          data)]
    (make-csv ctx data->csvformat)))

(defn private-badge! [ctx id]
  (try+
   (update-badge-visibility! {:id id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn private-badges! [ctx badge_id]
  (try+
   (update-user-badge-visibility-by-badge-id! {:badge_id badge_id} (get-db ctx))
   (update-badge-visibility-by-badge-id! {:badge_id badge_id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn private-page! [ctx id]
  (try+
   (update-page-visibility! {:id id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn private-user! [ctx id]
  (try+
   (update-user-visibility! {:id id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn ticket [ctx description report_type item_id item_url item_name item_type reporter_id item_content_id]
  (try+
   (let [ticket (insert-report-ticket<! {:description description :report_type report_type :item_id item_id :item_url item_url :item_name item_name :item_type item_type :reporter_id reporter_id :item_content_id item_content_id} (get-db ctx))]
     (util/event ctx reporter_id "ticket" (:generated_key ticket) "admin")
     "success")
   (catch Object _
     "error")))

(defn get-tickets [ctx]
  (select-tickets {} (get-db ctx)))

(defn get-closed-tickets [ctx]
  (select-closed-tickets {} (get-db ctx)))

(defn close-ticket!
  "Close or restore ticket"
  [ctx id status]
  (try+
   (update-ticket-status! {:id id :status status} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn delete-badge! [ctx id  user-id subject message]
  (try+
   (let [user-id (select-user-id-by-badge-id {:id id} (into {:result-set-fn first :row-fn :user_id} (get-db ctx)))
         user (select-user-and-email {:id user-id} (into {:result-set-fn first} (get-db ctx)))]
     (m/send-mail ctx subject message [(:email user)])
     (update-badge-deleted! {:id id} (get-db ctx)))
   "success"
   (catch Object _
     (log/error _)
     "error")))

(defn delete-badges! [ctx badge-id subject message]
  (try+
   (let [user-ids (select-users-id-by-badge-id {:badge_id badge-id} (into {:row-fn :user_id} (get-db ctx)))
         users-email (select-users-email {:user_id user-ids} (into {:result-set-fn vec :row-fn :email} (get-db ctx)))]
     (if (and (< 1 (count subject)) (< 1 (count message)))
       (m/send-mail ctx subject message users-email))
     (update-badge-deleted-by-badge-id! {:badge_id badge-id} (get-db ctx))
     (update-badge-visibility-by-badge-id! {:badge_id badge-id} (get-db ctx)))
   "success"
   (catch Object _
     "error")))

(defn delete-page! [ctx id user-id subject message]
  (try+
   (let [user (select-user-and-email {:id user-id} (into {:result-set-fn first} (get-db ctx)))]
     (if (and (< 1 (count subject)) (< 1 (count message)))
       (m/send-mail ctx subject message [(:email user)]))
     (update-page-deleted! {:id id} (get-db ctx)))
   "success"
   (catch Object _
     "error")))

(defn delete-user! [ctx user-id subject message email]
  (try+
   (if (and (< 1 (count subject)) (< 1 (count message)))
     (m/send-mail ctx subject message [email]))
   (update-user-pages-set-private! {:user_id user-id} (get-db ctx))
   (update-user-badges-set-private! {:user_id user-id} (get-db ctx))
   (delete-user-badge-congratulations! {:user_id user-id} (get-db ctx))
   (delete-user-badge-views! {:user_id user-id} (get-db ctx))
   (update-user-deleted! {:id user-id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn delete-user-full! [ctx user-id subject message email]
  (try+
   (if (and (< 1 (count subject)) (< 1 (count message)))
     (m/send-mail ctx subject message [email]))
   (u/delete-user ctx user-id nil true)
   "success"
   (catch Object _
     "error")))

(defn undelete-user! [ctx user-id]
  (try+
   (update-user-undeleted! {:id user-id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn send-message [ctx user_id subject message email]
  (try+
   (if (and (< 1 (count subject)) (< 1 (count message)))
     (m/send-mail ctx subject message [email]))
   "success"
   (catch Object _
     "error")))

(defn get-user-name-and-primary-email [ctx user_id]
  (let [user (select-user-and-email {:id user_id} (into {:result-set-fn first} (get-db ctx)))]
    (hash-map :name (str (:first_name user) " " (:last_name user))
              :email (:email user))))

(defn get-oauth-user-services [ctx user_id]
  (if-let [fun (first (plugin-fun (get-plugins ctx) "block" "user-information"))]
    (fun ctx user_id)
    []))

(defn get-user [ctx user_id]
  (let [user (u/user-information-with-registered-and-last-login ctx user_id)
        emails (vec (u/email-addresses ctx user_id))
        user-services (vec (get-oauth-user-services ctx user_id))]
    (hash-map :name (str (:first_name user) " " (:last_name user))
              :image_file (:profile_picture user)
              :item_owner_id (:id user)
              :item_owner (str (:first_name user) " " (:last_name user))
              :info {:emails emails
                     :role (:role user)
                     :last_login (:last_login user)
                     :ctime (:ctime user)
                     :deleted (:deleted user)
                     :activated (:activated user)
                     :has_password? (:has_password user)
                     :service user-services})))

(defn get-badge-modal [ctx badgeid]
  (let [badge  (b/get-badge ctx badgeid nil)
        emails (vec (u/email-addresses ctx (:owner badge)))
        default-content (->> badge :content (filter #(= (:language_code %) (:default_language_code %))) first)]
    (hash-map :name (:name default-content)
              :image_file (:image_file default-content)
              :item_owner_id (:owner badge)
              :item_owner (str (:first_name badge) " " (:last_name badge))
              :info {:issuer_content_name (:issuer_content_name default-content)
                     :issuer_content_url (:issuer_content_url default-content)
                     :issuer_contact (:issuer_contact default-content)
                     :issuer_image (:issuer_image default-content)
                     :creator_name (:creator_name default-content)
                     :creator_url (:creator_url default-content)
                     :creator_email (:creator_email default-content)
                     :creator_image (:creator_image default-content)
                     :emails emails})))

(defn get-public-badge-content-modal [ctx badge-id user-id]
  (let [badge (g/public-multilanguage-badge-content ctx badge-id user-id)
                                        ;badge (g/select-common-badge-content {:id badge-id} (into {:result-set-fn first} (get-db ctx)))
        badge-content (first (filter #(= (:language_code %) (:default_language_code %)) (get-in badge [:badge :content])))] ;(g/select-badge-criteria-issuer-by-date {:badge_id badge-id} (into {:result-set-fn first} (get-db ctx)))
        ;recipients (g/select-badge-recipients {:badge_id badge-id} (get-db ctx))

    (hash-map :name (:name badge-content)
              :image_file (:image_file badge-content)
              :item_owner_id  (vec (map :id (:public_users badge)))
              :item_owner (vec (map (fn [x] (str (:first_name x) " " (:last_name x))) (:public_users badge)))
              :info {:issuer_content_name (:issuer_content_name badge-content)
                     :issuer_content_url  (:issuer_content_url badge-content)
                     :issuer_contact      (:issuer_contact badge-content)
                     :issuer_image        (:issuer_image badge-content)
                     :creator_name        (:creator_name badge-content)
                     :creator_url         (:creator_url badge-content)
                     :creator_email       (:creator_email badge-content)
                     :creator_image       (:creator_image badge-content)})))

(defn get-page-modal [ctx pageid]
  (let [page  (p/page-with-blocks ctx pageid)
        user (u/user-information ctx (:user_id page))
        emails (vec (u/email-addresses ctx (:user_id page)))]
    (hash-map :name (:name page)
              :image_file (:profile_picture user)
              :item_owner_id (:user_id page)
              :item_owner (str (:first_name page) " " (:last_name page))
              :info {:emails emails})))

(defn profile-countries [ctx user-id]
  (let [current-country (g/user-country ctx user-id)
        countries (g/select-profile-countries {} (into {:row-fn :country} (get-db ctx)))]
    (-> all-countries
        (select-keys (conj countries current-country))
        (sort-countries)
        (seq))))

#_(defn all-profiles
    "Search all user profiles by user's name, email and country"
    [ctx search-params user-id]
    (let [{:keys [name country order_by email filter]} search-params
          where ""
          order (case order_by
                  "ctime" " ORDER BY ctime DESC"
                  "name" " ORDER BY last_name, first_name"
                  "")
          params []
          [where params] (if-not (or (empty? country) (= country "all"))
                           [(str where " AND country = ?") (conj params country)]
                           [where params])
          [where params] (if-not (empty? name)
                           [(str where " AND CONCAT(first_name,' ',last_name) LIKE ?") (conj params (str "%" name "%"))]
                           [where params])
          [where params] (if-not (empty? email)
                           [(str where " AND u.id in (SELECT user_id from user_email WHERE email LIKE ?)") (conj params (str "%" email "%"))]
                           [where params])
          [where params] (if (pos? filter)
                           [(str where " AND u.deleted = ?") (conj params filter)]
                           [where params])
          [where params] (if (pos? filter)
                           [(str where " AND u.deleted = ?") (conj params filter)]
                           [where params])
          query (str "SELECT u.id, u.first_name, u.last_name, u.country, u.ctime, u.deleted, GROUP_CONCAT(ue.email,' ', ue.primary_address) AS email FROM user AS u
JOIN user_email AS ue ON ue.user_id = u.id
WHERE (u.profile_visibility = 'public' OR u.profile_visibility = 'internal') AND u.role <> 'deleted' "
                     where
                     " GROUP BY u.id, u.first_name, u.last_name, u.country, u.ctime, u.deleted "
                     order
                     " LIMIT 50")
          profiles (jdbc/with-db-connection
                    [conn (:connection (get-db ctx))]
                    (jdbc/query conn (into [query] params)))]

      (->> profiles
           (take 50))))

(defn select-profiles [ctx country ids order]
  (let [limit 50]
    (if (nil? ids)
       (select-user-profiles-all {:country country :order order :limit limit} (get-db ctx))
       (if (empty? ids)
         []
         (select-user-profiles-filtered {:country country :order order :ids ids :limit limit} (get-db ctx))))))


(defn profile-ids-all [ctx search-params]
 (let [{:keys [name country order_by email filter custom-field-filters]} search-params
       organization (get custom-field-filters :organization "")
       gender (get custom-field-filters :gender "")

       filters
       (cond-> []
         (not (blank? name))
         (conj (set (select-all-profile-ids-name {:name (str "%" name "%")} (get-db-col ctx :id))))

         (not (blank? email))
         (conj (set (select-all-profile-ids-email {:email (str "%" email "%")} (get-db-col ctx :id))))

         (and (not (blank? organization)) (not= "notset" organization))
         (conj (set (select-all-profile-ids-organization {:org (str "%" organization "%")}  (get-db-col ctx :id))))

         (and (not (blank? organization)) (= "notset" organization))
         (conj (set (select-all-profile-ids-organization-not-set {}  (get-db-col ctx :id))))

         (and (not (blank? gender)) (not= "notset" gender))
         (conj (set (select-all-profile-ids-gender {:gender (str "%" gender "%")}  (get-db-col ctx :id))))

         (and (not (blank? gender)) (= "notset" gender))
         (conj (set (select-all-profile-ids-gender-not-set {}  (get-db-col ctx :id))))

         #_(pos? filter)
         #_(conj (set (select-all-profile-ids-deleted {} (get-db-col ctx :id)))))]

   (when (seq filters)
     (into [] (reduce clojure.set/intersection (first filters) (rest filters))))))

(defn all-profiles
  "Search all user profiles by user's name, email and country"
  [ctx search-params user-id]
  (let [{:keys [name country order_by email filter custom-field-filters]} search-params
        profile-ids (profile-ids-all ctx search-params)
        profiles (select-profiles ctx country profile-ids order_by)]
    profiles))



(defn delete-no-verified-adress [ctx user-id email]
  (try+
   (delete-email-no-verified-address! {:user_id user-id :email email} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn delete-no-activated-user [ctx user-id]
  (try+
   (delete-email-addresses! {:user_id user-id} (get-db ctx))
   (delete-no-activated-user! {:id user-id} (get-db ctx))
   "success"
   (catch Object _
     "error")))

(defn send-user-activation-message
  "Send activation message to user"
  [ctx user-id]
  (let [user (select-user-and-email {:id user-id} (into {:result-set-fn first} (get-db ctx)))
        language (:language user)
        email (:email user)
        first-name (:first_name user)
        last-name (:last_name user)
        site-url (get-site-url ctx)
        base-path (get-base-path ctx)
        activation_code (:verification_key user)]
    (try+
     (if (not (:activated user))
       (m/send-activation-message ctx site-url (u/activation-link site-url base-path user-id activation_code language) (u/login-link site-url base-path) (str first-name " " last-name) email language)
       (throw+ "error"))
     "success"
     (catch Object _
       "error"))))

(defn user-information
  "Get user data by user-id"
  [ctx user-id]
  (let [select-user (select-user {:id user-id} (into {:result-set-fn first} (get-db ctx)))
        private (get-in ctx [:config :core :private] false)
        user (assoc select-user :private private)]
    user))

(defn set-fake-session [ctx ok-status user-id real-user-id]
  (let [{:keys [role id private activated]} (user-information ctx user-id)
        expires (+ (long (/ (System/currentTimeMillis) 1000)) (get-in ctx [:config :core :session :max-age]))]
    (log/info "admin-id: " real-user-id " is login as user-id: " user-id)
    (assoc-in ok-status [:session :identity] {:id id :role role :private private :activated activated :real-id real-user-id :expires expires})))

(defn set-session [ctx ok-status user-id]
  (let [{:keys [role id private activated]} (user-information ctx user-id)
        expires (+ (long (/ (System/currentTimeMillis) 1000)) (get-in ctx [:config :core :session :max-age]))]
    (log/info "admin-id: " user-id " logged out from user")
    (assoc-in ok-status [:session :identity] {:id id :role role :private private :activated activated :expires expires})))

(defn update-user-to-admin [ctx user-id]
  (log/info "admin set user-id: " user-id "to admin.")
  (update-user-to-admin! {:id user-id} (get-db ctx))
  "success")

(defn update-admin-to-user [ctx user-id]
  (log/info "admin set user-id:" user-id "to admin.")
  (update-admin-to-user! {:id user-id} (get-db ctx)))

(defn apply-custom-filters [ctx filters users]
 (as-> (first (plugin-fun (get-plugins ctx) "db" "apply-custom-filters-users")) $
       (when $ ($ ctx filters users))))
