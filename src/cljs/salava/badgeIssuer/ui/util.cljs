(ns salava.badgeIssuer.ui.util
  (:require
    [clojure.string :refer [blank?]]
    [reagent.core :refer [atom cursor create-class]]
    [reagent-modals.modals :as m]
    [reagent.session :as session]
    [salava.badgeIssuer.schemas :as schemas]
    [salava.core.i18n :refer [t]]
    [salava.core.time :refer [iso8601-to-unix-time]]
    [salava.core.ui.ajax-utils :as ajax]
    [salava.core.ui.helper :refer [path-for input-valid? navigate-to]]
    [salava.core.ui.modal :as mo]))

(defn validate-inputs [s b]
  (doall
    [(input-valid? (:name s) (:name b))
     (input-valid? (:description s) (:description b))
     (input-valid? (:criteria s) (:criteria b))
     (input-valid? (:image s) (:image b))
     (input-valid? (:issuable_from_gallery s) (:issuable_from_gallery b))]))

(defn error-msg [state]
  [:div
   [:div.modal-header
    [:button {:type "button"
              :class "close"
              :data-dismiss "modal"
              :aria-label "OK"}
     [:span {:aria-hidden "true"
             :dangerouslySetInnerHTML {:__html "&times;"}}]]]
    ;[:h4.modal-title (translate-text message)]]
   [:div.modal-body
    [:div.alert.alert-warning
     @(cursor state [:error-message])]]
   [:div.modal-footer
    [:button {:type "button"
              :class "btn btn-primary"
              :data-dismiss "modal"}
     "OK"]]])

(defn save-selfie-badge [state reload-fn]
  (reset! (cursor state [:error-message]) nil)
  (let [its (if @(cursor state [:badge :issue_to_self]) @(cursor state [:badge :issue_to_self]) 0)
        badge-info (-> @(cursor state [:badge])
                       (assoc :issue_to_self its)
                       (select-keys [:id :name :criteria :description :image :tags :issuable_from_gallery :issue_to_self]))
        validate-info (validate-inputs schemas/save-selfie-badge badge-info)]
    (if (some false? validate-info)
      (do
        (reset! (cursor state [:error-message])
          (case (.indexOf validate-info false)
            0 (t :badgeIssuer/Namefieldempty)
            1 (t :badgeIssuer/Descriptionfieldempty)
            2 (t :badgeIssuer/Criteriafieldempty)
            (t :badgeIssuer/Errormessage)))
        (when-not @(cursor state [:in-modal])
          (reset! (cursor state [:step]) 1)
          (m/modal! (error-msg state) {})))

      (ajax/POST
        (path-for "/obpv1/selfie/create")
        {:params badge-info
         :handler (fn [data]
                    (when (= "error" (:status data))
                      (reset! (cursor state [:error-message]) (t :badgeIssuer/Errormessage))
                      (m/modal! (error-msg state) {}))
                    (when (= "success" (:status data))
                     (reset! (cursor state [:badge]) (:badge data))
                     (when reload-fn (reload-fn))))}))))

(defn issue-selfie-badge [state reload-fn]
  (reset! (cursor state [:success-alert]) false)
  (let [id @(cursor state [:badge :id])
        recipients (mapv :id @(cursor state [:selected-users]))
        expires_on (if (nil? @(cursor state [:badge :expires_on])) nil (iso8601-to-unix-time @(cursor state [:badge :expires_on])))
        request (if (and (seq  @(cursor state [:send_request_to])) (not (blank? @(cursor state [:request-comment]))))
                    {:comment @(cursor state [:request-comment])
                     :selected_users (mapv :id @(cursor state [:send_request_to]))}
                  nil)
        evidence @(cursor state [:all_evidence])]

      (ajax/POST
        (path-for (str "/obpv1/selfie/issue"))
        {:params {:selfie_id id
                  :recipients recipients
                  :expires_on expires_on
                  :issued_from_gallery (or @(cursor state [:issued_from_gallery]) false)
                  :issue_to_self @(cursor state [:issue_to_self])
                  :request_endorsement request
                  :evidence evidence
                  :visibility @(cursor state [:visibility])}
         :handler (fn [data]
                    (if (= "success" (:status data))
                      (do
                        (reset! (cursor state [:success-alert]) true)
                        (when reload-fn (reload-fn)))
                      (reset! (cursor state [:error-msg]) (t :core/Errorpage))))
         :finally (fn []
                    (js/setTimeout (fn [] (reset! (cursor state [:success-alert]) false)) 20000))})))


(defn delete-selfie-badge [state]
  (let [id @(cursor state [:badge :id])]
    (ajax/DELETE
      (path-for (str "/obpv1/selfie/" id))
      {:handler (fn [])})))

(defn generate-image [state]
  (reset! (cursor state [:generating-image]) true)
  (ajax/POST
    (path-for "/obpv1/selfie/generate_image")
    {:handler (fn [{:keys [status url message]}]
                (when (= "success" status)
                  (reset! (cursor state [:badge :image]) url)
                  (reset! (cursor state [:generating-image]) false)))}))

(defn toggle-setting [setting]
  (if (pos? @setting)
    (reset! setting 0)
    (reset! setting 1)))

(defn issuing-history [state]
 (reset! (cursor state [:history :Initializing]) true)
 (ajax/GET
   (path-for (str "/obpv1/selfie/history/" (get-in @state [:badge :id])))
   {:handler (fn [data]
               (reset! (cursor state [:history :data]) data)
               (reset! (cursor state [:history :Initializing]) false))}))

(defn revoke-selfie-badge [user-badge-id state]
  (ajax/POST
    (path-for (str "/obpv1/selfie/revoke/" user-badge-id))
    {:handler (fn [data]
                (when (= "success" (:status data))
                  (reset! (cursor state [:revocation-request]) false)
                  (issuing-history state)))}))

(defn revoke-badge-content [user-badge-id state]
  (when (and @(cursor state [:revocation-request]) (= user-badge-id @(cursor state [:revoke-id])))
     [:div.col-md-12
      [:div.panel.thumbnail
        [:div.alert.alert-warning
         [:p (t :badgeIssuer/Confirmbadgerevocation)]]
        [:div
         [:button {:type "button"
                   :class "btn btn-danger btn-bulky"
                   :on-click #(do (.preventDefault %)
                                  (revoke-selfie-badge user-badge-id state))}
          (t :badgeIssuer/Revoke)]
         [:button.btn.btn-primary.btn-bulky {:type "button"
                                             :on-click #(reset! (cursor state [:revocation-request]) false)}
          (t :core/Cancel)]]]]))
