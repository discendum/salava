(ns salava.badge.main
  (:require [clojure.pprint :refer [pprint]]
            [yesql.core :refer [defqueries]]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :refer [rename-keys]]
            [clojure.string :refer [blank? split upper-case lower-case capitalize includes? join starts-with?]]
            [slingshot.slingshot :refer :all]
            [clojure.data.json :as json]
            [salava.core.time :refer [unix-time date-from-unix-time]]
            [salava.core.i18n :refer [t]]
            [salava.core.helper :refer [dump private?]]
            [salava.badge.db :as db]
            [salava.social.db :as so]
            [clojure.tools.logging :as log]
            [salava.core.util :as u]
            [salava.core.http :as http]
            [salava.badge.assertion :refer [fetch-json-data]]
            [salava.core.i18n :refer [t]]
            [pantomime.mime :refer [mime-type-of]]
            [clj-time.core :refer [today]]))

(defqueries "sql/badge/main.sql")
(defqueries "sql/badge/endorsement.sql")
(defqueries "sql/badge/evidence.sql")

(defn badge-url [ctx badge-id]
  (str (u/get-site-url ctx) (u/get-base-path ctx) "/badge/info/" badge-id))

#_(defn assoc-badge-tags [badge tags]
    (assoc badge :tags (map :tag (filter #(= (:user_badge_id %) (:id badge))
                                         tags))))
(defn assoc-badge-tags [badge tags]
  (-> badge
      (assoc :tags (some->> tags (r/filter #(= (:user_badge_id %) (:id badge))) (r/map :tag) (r/foldcat)))))

(defn map-badges-tags [badges tags]
  (->> badges (r/map #(assoc-badge-tags % tags)) (r/foldcat)))

(defn badge-owner? [ctx badge-id user-id]
  (let [owner (select-badge-owner {:id badge-id} (into {:result-set-fn first :row-fn :user_id} (u/get-db ctx)))]
    (= owner user-id)))

(defn badge-exists? [ctx id]
  (= id (check-badge-exists {:id id} (into {:result-set-fn first :row-fn :id} (u/get-db ctx)))))

(defn check-issuer-verified!
  "Fetch issuer data and check if issuer is verified by OBF"
  [ctx badge-id issuer-json-url mtime issuer-verified-initial]
  (try
    (if (and issuer-json-url mtime (< mtime (- (unix-time) (* 60 60 24 2))))
      (let [issuer-url-base (split issuer-json-url #"&event=")
            issuer-json (if (second issuer-url-base) (fetch-json-data (first issuer-url-base)))
            issuer-verified (if issuer-json (:verified issuer-json) issuer-verified-initial)]
        (if (and issuer-json badge-id)
          (update-badge-set-verified! {:issuer_verified issuer-verified :id badge-id} (u/get-db ctx)))
        issuer-verified)
      issuer-verified-initial)
    (catch Exception _
      issuer-verified-initial)))

(defn badge-publish-update! [ctx user-badge-id visibility]
  (let [badge-id (select-badge-id-by-user-badge-id {:user_badge_id user-badge-id} (into {:result-set-fn first :row-fn :badge_id} (u/get-db ctx)))]
    (if (not= visibility "private")
      (update-badge-published! {:badge_id badge-id :value 1} (u/get-db ctx))
      (let [visibility-count (select-badge-visibility-recipients-count {:badge_id badge-id} (into {:result-set-fn first :row-fn :visibility_count} (u/get-db ctx)))]
        (if (not (pos? visibility-count))
          (update-badge-published! {:badge_id badge-id :value 0} (u/get-db ctx)))))))

;FIXME
(defn badge-issued-and-verified-by-obf
  "Check if badge is issued by Open Badge Factory and if the issuer is verified"
  [ctx badge]
  (try
    (let [obf-url (get-in ctx [:config :factory :url])
          {:keys [id remote_url issuer_url mtime issuer_verified]} badge
          issued-by-obf   (and (not (nil? obf-url)) (not (nil? remote_url)) (= remote_url obf-url))
          verified-by-obf (and issued-by-obf (or (= issuer_verified true) (= issuer_verified 1)))]
      (assoc badge :issued_by_obf (boolean issued-by-obf)
             :verified_by_obf (boolean verified-by-obf)
             :obf_url obf-url))
    (catch Exception _
      badge)))

(defn png-convert-url [ctx image]
  (if (re-find #"\w+\.svg$" image)
    (str (u/get-full-path ctx) "/obpv1/file/as-png?image=" image)
    (str (u/get-site-url ctx) "/" image)))

(defn- map-badges-notifications [ctx user-id badges]
  (let [user-badge-ids (map :id badges)
        badge-ids (distinct (map :gallery_id badges))
        pending_endorsements_counts (pending-user-badge-endorsement-count-multi {:user_badge_ids user-badge-ids} (u/get-db ctx))
        pending_ext_endorsements_count (as-> (first (u/plugin-fun (u/get-plugins ctx) "ext-endorsement" "pending-user-badge-ext-endorsement-count-multi")) $
                                             (if (ifn? $) ($ {:user_badge_ids user-badge-ids} (u/get-db ctx)) []))
        new-message-counts (so/get-badge-message-count-multi ctx badge-ids user-id)]
    (->> badges
         (r/map #(assoc % :pending_endorsements_count (some (fn [b] (when (= (:id %) (:user_badge_id b)) (:count b))) pending_endorsements_counts)))
         (r/map #(assoc % :pending_ext_endorsements_count (some (fn [b] (when (= (:id %) (:user_badge_id b))  (:count b))) pending_ext_endorsements_count)))
         (r/map #(assoc % :new_message_count (some (fn [m] (when (= (:gallery_id %) (:gallery_id m)) (:count m))) new-message-counts)))
         (r/foldcat))))

(defn user-badges-all
  "Returns all the badges of a given user"
  [ctx user-id]
  (let [badges (map (fn [b]
                      (-> b
                          (assoc :revoked (= 1 (b :revoked)))
                          (assoc :png_image_file (png-convert-url ctx (:image_file b)))))
                    (select-user-badges-all {:user_id user-id} (u/get-db ctx)))
        tags (if-not (empty? badges) (select-taglist {:user_badge_ids (map :id badges)} (u/get-db ctx)))
        badges-with-tags (map-badges-tags badges tags)
        badges-with-notifications (when (seq badges) (map-badges-notifications ctx user-id badges-with-tags))]
    (hash-map :badges badges-with-notifications)))

(defn user-badges-all-p
  "Returns all the badges of a given user."
  [ctx user-id]
  (let [badges (some->> (select-user-badges-all-p {:user_id user-id} (u/get-db ctx))
                        (r/map  #(-> % (assoc :revoked (pos? (:revoked %)))))
                        (r/foldcat))
        tags (when (seq badges) (select-taglist {:user_badge_ids (map :id badges)} (u/get-db ctx)))]
    (hash-map :badges (map-badges-tags badges tags))))

(defn user-badges-to-export
  "Returns valid badges of a given user"
  [ctx user-id]
  (let [badges (select-user-badges-to-export {:user_id user-id} (u/get-db ctx))
        tags (if-not (empty? badges)  (select-taglist {:user_badge_ids (map :id badges)} (u/get-db ctx)))]
    (map-badges-tags badges tags)))

(defn user-badges-pending
  "Returns pending badges of a given user"
  [ctx user-id]
  (let [badges (map (fn [b] (assoc b :png_image_file (png-convert-url ctx (:image_file b))))
                    (select-user-badges-pending {:user_id user-id} (u/get-db ctx)))
        tags (if-not (empty? badges) (select-taglist {:user_badge_ids (map :id badges)} (u/get-db ctx)))]
    (map-badges-tags badges tags)))

(defn user-owns-badge?
  "Check if user owns badge"
  [ctx assertion user-id]
  (pos? (:count (if (= (get-in assertion [:verify :type]) "hosted")
                  (select-user-owns-hosted-badge
                   {:assertion_url (get-in assertion [:verify :url])
                    :user_id user-id}
                   (into {:result-set-fn first}
                         (u/get-db ctx)))
                  (select-user-owns-signed-badge
                   {:assertion_json (get-in assertion [:assertion_json])
                    :user_id user-id}
                   (into {:result-set-fn first}
                         (u/get-db ctx)))))))

(defn user-owns-badge-id
  "Check if user owns badge and returns id"
  [ctx badge]
  (:id (select-user-owns-badge-id badge (u/get-db-1 ctx))))

(defn check-badge-revoked
  "Check if badge assertion url exists and set badge re"
  [ctx badge-id init-revoked? assertion-url last-checked]
  (if (and (not init-revoked?) (or (nil? last-checked) (< last-checked (- (unix-time) (* 2 24 60 60)))) assertion-url (not (get-in ctx [:config :core :test-mode])))
    (let [assertion (fetch-json-data assertion-url)
          revoked? (and (= 410 (:status assertion)) (= true (get-in assertion [:body :revoked])))]
      (update-revoked! {:revoked revoked? :id badge-id} (u/get-db ctx))
      (if revoked?
        (update-visibility! {:visibility "private" :id badge-id} (u/get-db ctx)))
      revoked?)
    init-revoked?))

(defn parse-assertion-json
  [assertion-json]
  (try+
   (let [assertion (json/read-str assertion-json :key-fn keyword)
         issued-on (u/str->epoch (or (:issuedOn assertion) (:issued-on assertion)))
         expires   (u/str->epoch (:expires assertion))]
     (assoc (dissoc assertion :issued_on) :issuedOn (if issued-on (date-from-unix-time (* 1000 issued-on)) "-")
            :expires (if expires (date-from-unix-time (* 1000 expires)) "-")))
   (catch Object _
     (log/error "parse-assertion-json: " _))))

(defn send-badge-info-to-obf [ctx user-badge-id user-id]
  (let [obf-url (get-in ctx [:config :core :obf :url])
        site-url (get-in ctx [:config :core :site-url])]
    (if (string? obf-url)
      (if-let [assertion-url (select-badge-assertion-url {:id user-badge-id :user_id user-id} (into {:result-set-fn first :row-fn :assertion_url} (u/get-db ctx)))]
        (try+
         (http/http-get (str obf-url "/c/badge/passport_update") {:query-params {"badge" user-badge-id "user" user-id "url" site-url}})
         (catch Object _
           (log/error "send-badge-info-to-obf: " (.getMessage _))))))))

#_(defn fetch-badge [ctx badge-id]
    (let [my-badge (select-multi-language-user-badge {:id badge-id} (u/get-db-1 ctx))
          content (map (fn [content]
                         (-> content
                             (update :criteria_content u/md->html)
                             (assoc  :alignment (select-alignment-content {:badge_content_id (:badge_content_id content)} (u/get-db ctx)))
                             (dissoc :badge_content_id)))
                       (select-multi-language-badge-content {:id (:badge_id my-badge)} (u/get-db ctx)))]
         ; evidences (map (fn [evidence] (-> evidence (update :narrative u/md->html)))(badge-evidences ctx badge-id))]
      (assoc my-badge :content content)))
      ;:evidences evidences)))

(defn fetch-badge [ctx badge-id]
  (let [my-badge (select-multi-language-user-badge {:id badge-id} (u/get-db-1 ctx))
        content  (some->> (select-multi-language-badge-content {:id (:badge_id my-badge)} (u/get-db ctx))
                          (r/map #(-> %
                                      (assoc :criteria_content (u/md->html (:criteria_content %)))
                                      (assoc :alignment (select-alignment-content {:badge_content_id (:badge_content_id %)} (u/get-db ctx)))
                                      (dissoc :badge_content_id)))
                          (r/foldcat))]
    (assoc my-badge :content content)))

(defn fetch-badge-p [ctx badge-id]
  (let [my-badge (select-multi-language-user-badge-p {:id badge-id} (u/get-db-1 ctx))
        content  (some->> (select-multi-language-badge-content {:id (:badge_id my-badge)} (u/get-db ctx))
                          (r/map #(-> %
                                      (assoc :criteria_content (u/md->html (:criteria_content %)))
                                      (assoc :alignment (select-alignment-content {:badge_content_id (:badge_content_id %)} (u/get-db ctx)))
                                      (dissoc :badge_content_id)))
                          (r/foldcat))]
    (assoc my-badge :content content)))

(defn get-badge-REMOVE
  "Get badge by id"
  [ctx badge-id user-id]
  (let [badge (fetch-badge ctx badge-id) ;(update (select-badge {:id badge-id} (into {:result-set-fn first} (u/get-db ctx))) :criteria_content u/md->html)
        owner? (= user-id (:owner badge))
        badge-message-count (if user-id (so/get-badge-message-count ctx (:badge_id badge) user-id))
        followed? (if user-id (so/is-connected? ctx user-id (:badge_id badge)))
        all-congratulations (if user-id (select-all-badge-congratulations {:user_badge_id badge-id} (u/get-db ctx)))
        user-congratulation? (and user-id
                                  (not owner?)
                                  (some #(= user-id (:id %)) all-congratulations))
        view-count (if owner? (select-badge-view-count {:user_badge_id badge-id} (into {:result-set-fn first :row-fn :count} (u/get-db ctx))))
        badge (badge-issued-and-verified-by-obf ctx badge)
        recipient-count (select-badge-recipient-count {:badge_id (:badge_id badge) :visibility (if user-id "internal" "public")}
                                                      (into {:result-set-fn first :row-fn :recipient_count} (u/get-db ctx)))]
        ;evidences (badge-evidences ctx badge-id (:user_id badge) true)]
    (assoc badge :congratulated? user-congratulation?
           :congratulations all-congratulations
           :view_count view-count
           :recipient_count recipient-count
           :message_count badge-message-count
      ;:followed? followed?
           :receive-notifications followed?
           :revoked (check-badge-revoked ctx badge-id (:revoked badge) (:assertion_url badge) (:last_checked badge))
           :assertion (parse-assertion-json (:assertion_json badge))
           :qr_code (u/str->qr-base64 (badge-url ctx badge-id)))))
      ;:evidences evidences
      ;:user_endorsement_count (->> (select-accepted-badge-endorsements {:id badge-id}  (u/get-db ctx)) count))))

(defn get-badge
  "Get badge by id"
  [ctx badge-id user-id]
  (let [badge (fetch-badge ctx badge-id) ;(update (select-badge {:id badge-id} (into {:result-set-fn first} (u/get-db ctx))) :criteria_content u/md->html)
        owner? (= user-id (:owner badge))
        all-congratulations (if user-id (select-all-badge-congratulations {:user_badge_id badge-id} (u/get-db ctx)))
        user-congratulation? (and user-id
                                  (not owner?)
                                  (some #(= user-id (:id %)) all-congratulations))
        view-count (if owner? (select-badge-view-count {:user_badge_id badge-id} (into {:result-set-fn first :row-fn :count} (u/get-db ctx))))
        badge (badge-issued-and-verified-by-obf ctx badge)
        recipient-count (select-badge-recipient-count {:badge_id (:badge_id badge) :visibility (if user-id "internal" "public")}
                                                      (into {:result-set-fn first :row-fn :recipient_count} (u/get-db ctx)))]
    (some-> badge (assoc :congratulated? user-congratulation?
                         :congratulations all-congratulations
                         :view_count view-count
                         :recipient_count recipient-count
                         :revoked (check-badge-revoked ctx badge-id (:revoked badge) (:assertion_url badge) (:last_checked badge))
                         :qr_code (u/str->qr-base64 (badge-url ctx badge-id))))))
                         ;:user_endorsement_count (->> (select-accepted-badge-endorsements {:id badge-id}  (u/get-db ctx)) count)))))

(defn get-badge-p
  "Get badge by id, public route"
  [ctx badge-id user-id]
  (let [badge (some->> (fetch-badge-p ctx badge-id) #_(badge-issued-and-verified-by-obf ctx))]
    (some-> badge
            (assoc :revoked (check-badge-revoked ctx badge-id (:revoked badge) (:assertion_url badge) (:last_checked badge)))
                  ;:qr_code (u/str->qr-base64 (badge-url ctx badge-id)))
            (dissoc :badge_id :deleted :obf_url))))

(defn get-endorsements [ctx badge-id]
  (map (fn [e]
         {:id (:id e)
          :content (u/md->html (:content e))
          :issued_on (:issued_on e)
          :issuer {:id (:issuer_id e)
                   :language_code ""
                   :name (:issuer_name e)
                   :url  (:issuer_url e)
                   :description (:issuer_description e)
                   :image_file (:issuer_image e)
                   :email (:issuer_email e)
                   :revocation_list_url nil
                   :endorsement []}})
       (select-badge-endorsements {:id badge-id} (u/get-db ctx))))

(defn get-issuer-endorsements [ctx issuer-id]
  (let [issuer (some-> (select-issuer {:id issuer-id} (u/get-db-1 ctx)) (update :description u/md->html))]
    (assoc issuer :endorsement (map (fn [e]
                                      {:id (:id e)
                                       :content (u/md->html (:content e))
                                       :issued_on (:issued_on e)
                                       :issuer {:id (:issuer_id e)
                                                :language_code ""
                                                :name (:issuer_name e)
                                                :url  (:issuer_url e)
                                                :description (:issuer_description e)
                                                :image_file (:issuer_image e)
                                                :email (:issuer_email e)
                                                :revocation_list_url nil
                                                :endorsement []}})
                                    (select-issuer-endorsements {:id issuer-id} (u/get-db ctx))))))

(defn get-creator [ctx creator-id]
  (let [creator (some-> (select-creator {:id creator-id} (u/get-db-1 ctx)) (update :description u/md->html))]
    (assoc creator :endorsement nil)))

(defn- check-email [recipient email]
  (let [{hashed :hashed identity :identity salt :salt} recipient
        [algo hash] (split (or identity "") #"\$")]
    (first (filter #(if hashed (= hash (u/hex-digest algo (str % salt))) (= identity %))
                   [email (upper-case email) (lower-case email) (capitalize email)]))))

(defn- recipient-email [user-emails recipient]
  (or (some #(check-email recipient %) user-emails)
      (throw (Exception. "badge/Userdoesnotownthisbadge"))))

(defn save-badge-tags!
  "Save tags associated to badge. Delete existing tags."
  [ctx tags badge-id]
  (let [valid-tags (filter #(not (blank? %)) (distinct tags))]
    (delete-badge-tags! {:user_badge_id badge-id} (u/get-db ctx))
    (doall (for [tag valid-tags]
             (replace-badge-tag! {:user_badge_id badge-id :tag tag} (u/get-db ctx))))))

(defn update-recipient-count-and-connect [ctx user-id user_badge_id]
  (let [badge-id (-> (select-badge-id-by-user-badge-id {:user_badge_id user_badge_id} (into {:result-set-fn first} (u/get-db ctx))) :badge_id)
        existing-ids (select-user-badge-id-with-badge-id {:user_id user-id :badge_id badge-id} (into {:row-fn :id} (u/get-db ctx)))]
    (if (some #(= :social %) (get-in ctx [:config :core :plugins]))
      (so/create-connection-badge-by-badge-id! ctx user-id user_badge_id))
    (when (empty? (rest existing-ids))
      (update-badge-recipient-count! {:badge_id badge-id} (u/get-db ctx)))))

#_(defn set-recipient-count! [ctx user-badge-id]
    (let [badge-id (select-badge-id-by-user-badge-id {:user_badge_id user-badge-id} (into {:result-set-fn first :row-fn :badge_id} (u/get-db ctx)))]
      (update-badge-recipient-count! {:badge_id badge-id} (u/get-db ctx))))

#_(defn set-status!
    "Set badge status"
    [ctx user-badge-id status user-id]
    (if (badge-owner? ctx user-badge-id user-id)
      (update-status! {:id user-badge-id :status status} (u/get-db ctx)))
    (if (= "accepted" status)
      (update-recipient-count-and-connect ctx user-id user-badge-id)
      #_(do
          (set-recipient-count! ctx user-badge-id)
          (if (some #(= :social %) (get-in ctx [:config :core :plugins]))
            (so/create-connection-badge-by-badge-id! ctx user-id user-badge-id))))
    user-badge-id)

(defn set-status!
  "Set badge status"
  [ctx user-badge-id status user-id]
  (try+
   (if-not (badge-owner? ctx user-badge-id user-id)
     (throw+ "User does not own this badge")
     (do
       (update-status! {:id user-badge-id :status status} (u/get-db ctx))
       (when (= "accepted" status) (update-recipient-count-and-connect ctx user-id user-badge-id))
       {:status "success" :id user-badge-id}))
   (catch Object _
     (log/error "Error: " _)
     {:status "error" :id user-badge-id :message _})))

(defn toggle-show-recipient-name!
  "Toggle recipient name visibility"
  [ctx badge-id show-recipient-name user-id]
  (if (badge-owner? ctx badge-id user-id)
    (update-show-recipient-name! {:id badge-id :show_recipient_name show-recipient-name} (u/get-db ctx))))

(defn toggle-email-notifications [ctx id value user-id]
 (try+
  (if-not (badge-owner? ctx id user-id)
    (throw+ "User does not own this badge")
    (update-email-notifications! {:id id :value value} (u/get-db ctx)))
  {:status "success" :value value}
  (catch Object _
   (log/error "Error: " _)
   {:status "error" :message _})))

(defn email-notifications-setting [ctx id user-id]
  (or (select-email-notifications-setting {:id id} (into {:result-set-fn first :row-fn :email_notifications} (u/get-db ctx))) 0))

(defn congratulate!
  "User congratulates badge receiver"
  [ctx badge-id user-id]
  (try+
   (let [congratulation (select-badge-congratulation {:user_badge_id badge-id :user_id user-id} (into {:result-set-fn first} (u/get-db ctx)))]
     (if congratulation
       (throw+ "User have congratulated owner already"))
     (if (badge-owner? ctx badge-id user-id)
       (throw+ "User cannot congratulate himself"))
     (insert-badge-congratulation<! {:user_badge_id badge-id :user_id user-id} (u/get-db ctx))
     (u/event ctx user-id "congratulate" badge-id "badge")
     {:status "success" :message ""})
   (catch Object _
     {:status "error" :message _})))

#_(defn badge-settings "Get badge settings" [ctx user-badge-id user-id]
    (if (badge-owner? ctx user-badge-id user-id)
      (let [badge (update (select-badge-settings {:id user-badge-id} (into {:result-set-fn first} (u/get-db ctx))) :criteria_content u/md->html)
            tags (select-taglist {:user_badge_ids [user-badge-id]} (u/get-db ctx))]
        (-> (assoc-badge-tags badge tags)
            (assoc :user_endorsement_count (->> (select-accepted-badge-endorsements {:id user-badge-id}  (u/get-db ctx)) count))))))

(defn badge-settings "Get badge settings" [ctx user-badge-id user-id]
  (when (badge-owner? ctx user-badge-id user-id)
    (let [settings (select-badge-settings {:id user-badge-id} (into {:result-set-fn first} (u/get-db ctx)))
          tags (select-taglist {:user_badge_ids [user-badge-id]} (u/get-db ctx))]
      (-> (assoc-badge-tags settings tags)))))

#_(defn save-badge-settings!
    "Update badge settings"
    [ctx user-badge-id user-id visibility rating tags]
    (try+
     (if (badge-owner? ctx user-badge-id user-id)
       (let [data {:id          user-badge-id
                   :visibility   visibility
                   :rating       rating}]
         (if (and (private? ctx) (= "public" visibility))
           (throw+ {:status "error" :user-badge-id user-badge-id :user-id user-id :message "trying save badge visibilty as public in private mode"}))
         (update-badge-settings! data (u/get-db ctx))
         (save-badge-tags! ctx tags user-badge-id)
         (send-badge-info-to-obf ctx user-badge-id user-id)
         (badge-publish-update! ctx user-badge-id visibility)
         (if (or (= "internal" visibility) (= "public" visibility))
           (u/event ctx user-id "publish" user-badge-id "badge")
           (u/event ctx user-id "unpublish" user-badge-id "badge"))
         {:status "success"})
       (throw+ {:status "error"}))
     (catch Object ex
       (log/error "trying save badge visibilty as public in private mode: " ex)
       {:status "error"})))

(defn- publish-badge-event! [ctx user-badge-id user-id visibility]
  (if (= "private" visibility)
    (u/event ctx user-id "unpublish" user-badge-id "badge")
    (u/event ctx user-id "publish" user-badge-id "badge")))

(defn save-badge-settings!
  "Update badge settings (visibility, show_recipient_name, tags, rating)"
  [ctx user-badge-id user-id data]
  (try+
   (if-not (badge-owner? ctx user-badge-id user-id)
     (throw+ {:status "error" :message "Settings cannot be updated, user does not own this badge!"})
     (let [{:keys [tags settings]} data
           {:keys [visibility rating show_recipient_name]} settings
           previous-settings (badge-settings ctx user-badge-id user-id)
           settings-data {:id user-badge-id
                          :rating (if-not (number? rating) (:rating previous-settings) rating)
                          :visibility (if (blank? visibility) (:visibility previous-settings) visibility)
                          :show_recipient_name (if-not (number? show_recipient_name) (:show_recipient_name previous-settings) show_recipient_name)}]
       (when (and (private? ctx) (= "public" visibility))
         (throw+ {:status "error" :user-badge-id user-badge-id :user-id user-id :message "trying save badge visibility as public in private mode"}))
       (when (or rating visibility show_recipient_name) (update-badge-settings! settings-data (u/get-db ctx)))
       (when tags (save-badge-tags! ctx tags user-badge-id))
       (when-not (empty? data) (send-badge-info-to-obf ctx user-badge-id user-id))
       (when visibility
         (badge-publish-update! ctx user-badge-id visibility)
         (publish-badge-event! ctx user-badge-id user-id visibility))
       {:status "success"}))
   (catch Object _
     (log/error "Badge settings update failed: " _)
     {:status "error"})))

(defn set-visibility!
  "Set badge visibility"
  [ctx user-badge-id visibility user-id]
  (try+
   (when (badge-owner? ctx user-badge-id user-id)
     (update-visibility! {:id user-badge-id :visibility visibility} (u/get-db ctx))
     (badge-publish-update! ctx user-badge-id visibility)
     (publish-badge-event! ctx user-badge-id user-id visibility))
   {:status "success"}
   (catch Object _
     {:status "error" :message _})))

(defn save-badge-rating!
  "Update badge rating"
  [ctx badge-id user-id rating]
  (if (badge-owner? ctx badge-id user-id)
    (let [data {:id          badge-id
                :rating       rating}]
      (update-badge-rating! data (u/get-db ctx))
      (send-badge-info-to-obf ctx badge-id user-id)
      {:status "success"})
    {:status "error"}))

(defn delete-badge-evidences! [db badge-id user-id]
  (if-let [evidences (select-user-badge-evidence {:user_badge_id badge-id} db)]
    (when (seq evidences)
      (delete-all-badge-evidences! {:user_badge_id badge-id} db)
      (doseq [evidence evidences
              :let [name (str "evidence_id:" (:id evidence))]]
        (delete-user-evidence-property! {:name name :user_id user-id} db)))))

(defn delete-badge-endorsements! [db id]
  (delete-user-badge-endorsements! {:id id} db))

(defn delete-badge-endorsement-requests! [db id]
  (delete-user-badge-endorsement-requests! {:id id} db))

(defn delete-badge-with-db! [db user-badge-id]
  (delete-badge-tags! {:user_badge_id user-badge-id} db)
  (delete-badge-views! {:user_badge_id user-badge-id} db)
  (delete-badge-congratulations! {:user_badge_id user-badge-id} db)
  (update-badge-set-deleted! {:id user-badge-id} db))

(defn delete-badge!
  "Set badge deleted and delete tags"
  [ctx badge-id user-id]
  (try+
   (if-not (badge-owner? ctx badge-id user-id)
     (throw+ "User does not own this badge"))
   (jdbc/with-db-transaction
    [tr-cn (u/get-datasource ctx)]
    (do
      (delete-badge-with-db! {:connection tr-cn} badge-id)
      (delete-badge-evidences! {:connection tr-cn} badge-id user-id)
      (delete-badge-endorsements! {:connection tr-cn} badge-id)
      (delete-badge-endorsement-requests! {:connection tr-cn} badge-id)))
   (if (some #(= :social %) (get-in ctx [:config :core :plugins]))
     (so/delete-connection-badge-by-badge-id! ctx user-id badge-id))
   {:status "success" :message "Badge deleted"}
   (catch Object _
     (log/error _)
     {:status "error" :message ""})))

(defn badges-images-names
  "Get badge images and names. Return a map."
  [ctx badge-ids]
  (if-not (empty? badge-ids)
    (let [badges (select-badges-images-names {:ids badge-ids} (u/get-db ctx))]
      (reduce #(assoc %1 (str (:id %2)) (dissoc %2 :id)) {} badges))))

(defn badges-by-tag-and-owner
  "Get badges by list of tag names and owner's user-id"
  [ctx tag user-id]
  (map #(update % :criteria_content u/md->html)
       (select-badges-by-tag-and-owner {:badge_tag tag :user_id user-id} (u/get-db ctx))))

(defn badge-viewed
  "Save information about viewing a badge. If user is not logged in user-id is nil."
  [ctx badge-id user-id]
  (insert-badge-viewed! {:user_badge_id badge-id :user_id user-id} (u/get-db ctx)))

(defn badges-by-issuer [badges-issuers]
  (reduce (fn [result issuer-badge]
            (let [issuer (select-keys issuer-badge [:issuer_content_id :issuer_content_name :issuer_content_url])
                  badge (select-keys issuer-badge [:id :name :image_file])
                  index (.indexOf (map :issuer_content_id result) (:issuer_content_id issuer))]
              (if (= index -1)
                (conj result (assoc issuer :badges [badge]))
                (update-in result [index :badges] conj badge)))) [] badges-issuers))

(defn badge-stats
  "Get badge statistics by user-id."
  [ctx user-id]
  (let [badge-count (select-user-badge-count {:user_id user-id} (into {:result-set-fn first :row-fn :count} (u/get-db ctx)))
        expired-badge-count (select-user-expired-badge-count {:user_id user-id} (into {:result-set-fn first :row-fn :count} (u/get-db ctx)))
        badge-views (select-badge-views-stats {:user_id user-id} (u/get-db ctx))
        badge-congratulations (select-badge-congratulations-stats {:user_id user-id} (u/get-db ctx))
        issuer-stats (badges-by-issuer (select-badge-issuer-stats {:user_id user-id} (u/get-db ctx)))]
    {:badge_count badge-count
     :expired_badge_count expired-badge-count
     :badge_views badge-views
     :badge_congratulations badge-congratulations
     :badge_issuers issuer-stats}))

(defn badge-view-stats
  "Get user badge view statistics by badge-id"
  [ctx id]
  (select-user-badge-views-stats {:id id} (into {:result-set-fn first} (u/get-db ctx))))

(defn meta-tags [ctx id]
  (let [base-url (u/get-full-path ctx)
        badge (select-badge {:id id} (into {:result-set-fn first} (u/get-db ctx)))]
    (if (= "public" (:visibility badge))
      (-> badge
          (select-keys [:name :description :image_file])
          (rename-keys {:image_file :image :name :title})
          (assoc :json-oembed
                 [:link {:rel "alternate"
                         :type "application/json+oembed"
                         :href (str base-url "/obpv1/oembed?url=" base-url "/badge/info/" id)
                         :title (:name badge)}])))))

(defn old-id->id [ctx old-id user-id]
  (if user-id
    (select-badge-id-by-old-id-user-id {:user_id user-id :old_id old-id} (into {:result-set-fn first :row-fn :id} (u/get-db ctx)))
    (select-badge-content-id-by-old-id {:old_id old-id} (into {:result-set-fn first :row-fn :badge_content_id} (u/get-db ctx)))))
