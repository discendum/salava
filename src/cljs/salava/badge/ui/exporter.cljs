(ns salava.badge.ui.exporter
  (:require [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-modals.modals :as m]
            [clojure.set :refer [intersection]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.layout :as layout]
            [salava.core.helper :refer [dump]]
            [salava.core.ui.helper :refer [unique-values path-for not-activated? js-navigate-to]]
            [salava.core.ui.notactivated :refer [not-activated-banner]]
            [salava.core.ui.grid :as g]
            [salava.core.ui.error :as err]
            [salava.core.i18n :refer [t]]))

(defn email-options [state]
  (map #(hash-map :value % :title %) (map #(:email %) (:emails @state))))

(defn visibility-options []
  [{:value "all" :title (t :badge/All)}
   {:value "public"  :title (t :badge/Public)}
   {:value "internal"  :title (t :badge/Shared)}
   {:value "private" :title (t :badge/Private)}])

(defn order-radio-values []
  [{:value "mtime" :id "radio-date" :label (t :badge/bydate)}
   {:value "name" :id "radio-name" :label (t :badge/byname)}])

(defn badge-grid-form [state]
  [:div {:id "grid-filter"
         :class "form-horizontal"}
   [g/grid-select        (t :badge/Email ":") "select-email" :email-selected (email-options state) state]
   [g/grid-search-field  (t :badge/Search ":") "badgesearch" (t :badge/Searchbyname) :search state]
   [g/grid-select        (t :badge/Show ":") "select-visibility" :visibility (visibility-options) state]
   [g/grid-buttons       (t :badge/Tags ":") (unique-values :tags (:badges @state)) :tags-selected :tags-all state]
   [g/grid-radio-buttons (t :badge/Order ":") "order" (order-radio-values) :order state]])

(defn badge-visible? [element state]
  (and
    (= (:email-selected @state) (:email element))
    (or (= (:visibility @state) "all")
        (= (:visibility @state) (:visibility element)))
    (or (> (count
             (intersection
               (into #{} (:tags-selected @state))
               (into #{} (:tags element)))
             ) 0)
        (= (:tags-all @state) true))
    (or (empty? (:search @state))
        (not= (.indexOf (.toLowerCase (:name element)) (.toLowerCase (:search @state))) -1))))

(defn grid-element [element-data state]
  (let [{:keys [id image_file name description visibility assertion_url issuer_content_name issuer_content_url]} element-data
        obf_url (session/get :factory-url)]
     [:div {:class "media grid-container" :key id}
      [:div.media-content
        [:div.visibility-icon
           (case visibility
             "private" [:i {:class "fa fa-lock"}]
             "internal" [:i {:class "fa fa-group"}]
             "public" [:i {:class "fa fa-globe"}]
             nil)]
       (if image_file
         [:div.media-left
          [:a {:href (path-for (str "/badge/info/" id))}[:img {:src (str "/" image_file)
                 :alt name}]]])
       [:div.media-body
        [:div.media-heading
         [:a.badge-link {:href (path-for (str "/badge/info/" id))}
          name]]
      [:div.media-issuer
         [:a {:href issuer_content_url
              :target "_blank"
              :title issuer_content_name} issuer_content_name]]]]
      [:div {:class "media-bottom"}
       [:div.row
        [:div.col-xs-9
         (let [checked? (boolean (some #(= id %) (:badges-selected @state)))]
           [:div.checkbox
            [:label {:for (str "checkbox-" id)}
             [:input {:type "checkbox"
                      :id (str "checkbox-" id)
                      :on-change (fn []
                                   (if checked?
                                     (swap! state assoc :badges-selected (remove #(= % id) (:badges-selected @state)))
                                     (swap! state assoc :badges-selected (conj (:badges-selected @state) id))))
                      :checked checked?}]

             (t :badge/Exporttobackpack)]])]
        [:div {:class "col-xs-3 text-right"}
         [:a {:href (str obf_url "/c/receive/download?url="(js/encodeURIComponent assertion_url)) :class "badge-download"}
          [:i {:class "fa fa-download"}]]]]]]))

(defn badge-grid [state]
  [:div {:class "row wrap-grid"
         :id "grid"}
   (doall (let [badges (:badges @state)
                order (:order @state)]
            (for [element-data (sort-by (keyword order) badges)]
              (if (badge-visible? element-data state)
                (grid-element element-data state)))))])

(defn export-to-pdf [state]
  (let [badges-to-export (:badges-selected @state)
        lang-option (:pdf-option @state)
        badge-url (if (empty? (rest badges-to-export))
                      (str "/obpv1/badge/export-to-pdf?badges[0]=" (first badges-to-export) "&lang-option="lang-option)
                      (str (clojure.string/join (cons (str "/obpv1/badge/export-to-pdf?badges[0]=" (first badges-to-export))
                           (map #(str "&badges["(.indexOf badges-to-export %)"]=" %) (rest badges-to-export)))) "&lang-option="lang-option))]

    (ajax/GET
      (path-for (str "/obpv1/badge/export-to-pdf")) ;badges-to-export "/" lang-option) true)
      {:params {:badges badges-to-export :lang-option lang-option }
       :handler (js-navigate-to badge-url)}
      )))


(defn export-to-pdf-modal [state]
   [:div {:id "badge-settings"}
     [:div.modal-body
      [:div.row
       [:div.col-md-12
        [:button {:type  "button"
                  :class  "close"
                  :data-dismiss "modal"
                  :aria-label   "OK"
                }
         [:span {:aria-hidden  "true"
               :dangerouslySetInnerHTML {:__html "&times;"}}]]]]
      [:div
       [:form {:class "form-horizontal"}
              [:div.form-group
             [:fieldset {:id "export-pdf" :class "col-md-12 checkbox"}
              [:div.col-md-12 [:label
               [:input {:type     "checkbox"
                        :on-change (fn [e]
                                     (if (.. e -target -checked)
                                         (swap! state assoc :pdf-option "all")(swap! state assoc :pdf-option "default")))}]
               (t :badge/ExportAllLang)]]]]]]]
    [:div.modal-footer
      [:button {:type         "button"
              :class        "btn btn-primary"
              :data-dismiss "modal"}
     (t :core/Close)]
      [:button {:type         "button"
              :class        "btn btn-primary"
              :on-click  #(export-to-pdf state)
              :data-dismiss "modal"
              }
     (t :badge/Export)]]])

(defn export-badges [state]
  (let [badges-to-export (:badges-selected @state)
        assertion-urls (map :assertion_url (filter (fn [b] (and (some #(= % (:id b)) badges-to-export)
                                                                (badge-visible? b state))) (:badges @state)))]
    (if-not (empty? badges-to-export)
      (.issue js/OpenBadges (clj->js assertion-urls)))))

(defn select-all [state]
  (if (:badges-all @state)
    (swap! state assoc :badges-selected [] :badges-all false)
    (swap! state assoc :badges-selected (map :id (:badges @state)) :badges-all true)))

(defn content [state]
  [:div {:id "export-badges"}
   [m/modal-window]
   [:h1.uppercase-header (t :badge/Exportordownload)]
   (if (not-activated?)
     (not-activated-banner)
     (if (:initializing @state)
       [:div.ajax-message
        [:i {:class "fa fa-cog fa-spin fa-2x "}]
        [:span (str (t :core/Loading) "...")]]
       [:div
        (if (or (empty? (:badges @state)) (empty? (:emails @state)))
          [:div {:class "alert alert-warning"}
           (cond
             (empty? (:emails @state)) [:span (t :badge/Nomozillaaccount) " " [:a {:href (path-for "/user/edit/email-addresses")} (t :badge/here) "."]]
             (empty? (:badges @state)) (t :badge/Nobadgestoexport))]
          [:div
           [badge-grid-form state]
           [:button {:class    "btn btn-primary"
                     :on-click #(select-all state)}
            (if (:badges-all @state) (t :badge/Clearall) (t :badge/Selectall))]
           (if (not (nil? (:backpack_id (first (filter #(= (:email %) (:email-selected @state)) (:emails @state))))))
             [:button {:class    "btn btn-primary"
                     :on-click #(export-badges state)
                     :disabled (= 0 (count (:badges-selected @state)))}
            (t :badge/Exportselected)])
           [:button {:class "btn btn-primary"
                     :on-click #(do (.preventDefault %)
                                     (swap! state assoc :pdf-option "default")
                                         (m/modal![export-to-pdf-modal state] {:size :lg}))

;;                      #(export-to-pdf state)
                     :disabled (= 0 (count (:badges-selected @state)))}
            (t :badge/Exporttopdf)]
           [badge-grid state]])]))])

(defn init-data [state]
  (ajax/GET
    (path-for "/obpv1/badge/export" true)
    {:handler (fn [{:keys [badges emails]} data]
                (let [exportable-badges (filter #(some (fn [e] (= e (:email %))) (map :email emails)) badges)]
                  (swap! state assoc :badges exportable-badges :emails emails :email-selected (:email (first emails)) :initializing false :permission "success")))}
    (fn [] (swap! state assoc :permission "error"))))

(defn handler [site-navi]
  (let [state (atom {:permission "initial"
                     :badges []
                     :emails []
                     :email-selected ""
                     :visibility "all"
                     :order "mtime"
                     :tags-all true
                     :tags-selected []
                     :badges-all false
                     :badges-selected []
                     :pdf-option "default"
                     :user-id (:id (session/get :user))
                     :initializing true})]
    (init-data state)
    (fn []
      (cond
        (= "initial" (:permission @state)) (layout/default site-navi [:div])
        (= "success" (:permission @state)) (layout/default site-navi (content state))
        :else (layout/default site-navi (err/error-content)))
      )))
