(ns salava.extra.application.ui.application
  (:require [reagent.core :refer [atom cursor create-class]]
            [reagent.session :as session]
            [reagent-modals.modals :as m :refer [close-modal!]]
            [clojure.string :as s :refer [trim blank? split]]
            [clojure.walk :refer [keywordize-keys]]
            [salava.core.ui.grid :refer [grid-radio-buttons]]
            [salava.core.ui.ajax-utils :as ajax]
            [salava.core.ui.layout :as layout]
            [salava.core.ui.helper :refer [path-for unique-values current-path navigate-to not-activated? disable-background-image]]
            [komponentit.autocomplete :refer [multiple-autocomplete]]
            [cemerick.url :as url]
            [salava.core.i18n :as i18n :refer [t]]
            [salava.core.ui.modal :as mo]
            [salava.extra.application.ui.issuer :as i :refer [issuer-info-grid all-issuers init-issuer]]
            [salava.core.ui.popover :refer [info]]
            [salava.extra.application.ui.helper :refer [share url-builder taghandler fetch-badge-adverts query-params str-cat remove-from-followed add-to-followed]]))


(def initial-query-params (atom {}))

(defn autocomplete-search [state country]
  (swap! state assoc :autocomplete {:tags {:value #{} :items #{}}})
  (ajax/GET
   (path-for "/obpv1/application/autocomplete")
   {:params {:country  country}
    :handler (fn [data]
               (let [{:keys [tags names]} data]
                 (swap! state assoc-in [:autocomplete :tags :items] (into (sorted-map) (map-indexed (fn [i v] [(inc i) (str "#" (:tag v))]) tags)))))}))

(defn open-modal [id state]
  (ajax/GET
   (path-for (str "/obpv1/application/public_badge_advert_content/" id))
   {:handler (fn [data]
               (do
                 (mo/open-modal [:application :badge] {:id (:init-id @state) :state state :data data})))}))

(defn search-timer [state]
  (let [timer-atom (cursor state [:timer])]
    (if @timer-atom
      (js/clearTimeout @timer-atom))
    (reset! timer-atom (js/setTimeout (fn []
                                        (fetch-badge-adverts state)) 500))))

(defn text-field [key label placeholder state]
  (let [search-atom (cursor state [:params key])
        field-id (str key "-field")]
    [:div.form-group
     (when label [:label {:class "control-label col-sm-2" :for field-id} (str label ":")])
     [:div.col-sm-8 ;.col-sm-10
      [:input {:class       (str "form-control")
               :id          field-id
               :type        "text"
               :placeholder placeholder
               :value       @search-atom
               :on-change   #(do
                               (reset! search-atom (.-target.value %))
                               (search-timer state))}]]]))

(defn country-selector [state]
  (let [country (cursor state [:params :country]) #_(cursor state [:country-selected])]
    [:div.form-group
     [:label {:class "control-label col-sm-2" :for "country-selector"} (str (t :gallery/Country) ":") [info {:content (t :extra-application/Filterbycountry) :placement "top"}]]
     [:div.col-sm-6 ;.col-sm-10
      [:select {:class     "form-control"
                :id        "country-selector"
                :name      "country"
                :value     @country
                :on-change #(do
                              (reset! country (.-target.value %))
                             (swap! state assoc-in [:issuer :show-issuer-info] false)
                             (swap! state assoc-in [:issuer :issuer-content] {:name (t :core/All)})
                             (swap! state assoc :params (query-params {:country @country}))
                             (autocomplete-search state @country)
                             (fetch-badge-adverts state))}
       [:option {:value "all" :key "all"} (t :core/All)]
       (for [[country-key country-name] (map identity (:countries @state))]
         [:option {:value country-key
                   :key country-key} country-name])]]]))

(defn order-radio-values []
  [{:value "mtime" :id "radio-date" :label (t :core/bydate)}
   {:value "name" :id "radio-name" :label (t :core/byname)}
   {:value "issuer_content_name" :id "radio-issuer-name" :label (t :core/byissuername)}])


(defn autocomplete [state]
  (let [value  (cursor state [:autocomplete :tags :value])
        autocomplete-items (cursor state [:autocomplete :tags :items])]
    (fn []
      [:div.form-group
       [:label {:class "control-label col-sm-2" :for "autocomplete"} (str (t :extra-application/Keywords) ":")]
       [:div.col-sm-6 ;.col-sm-10
        [multiple-autocomplete
         {:value     @value
          :on-change (fn [item] (do
                                  (swap! value conj (:key item))
                                  (taghandler state @value)))
          :on-remove (fn [x] (do
                               (swap! value disj x)
                               (taghandler state @value)))
          :search-fields   [:value]
          :items           @autocomplete-items
          :no-results-text (t :extra-application/Notfound)
          :control-class   "form-control"}]]])))

(defn follow-grid-item [show-followed-only-atom state]
  [:div {:class "form-group wishlist-buttons"}
   [:label {:for   "input-email-notifications"
            :class "col-md-2"}
    (str (t :core/Show) ":")]
   [:div.col-md-10
    [:div.buttons
     [:button {:class (str "btn btn-default btn " (if-not @show-followed-only-atom "btn-active"))
               :id "btn-all"
               :on-click #(do
                            (reset! show-followed-only-atom (if @show-followed-only-atom false true))
                            (fetch-badge-adverts state))}
      (t :core/All)]
     [:button {:class (str "btn btn-default " (if @show-followed-only-atom "btn-active"))
               :id "btn-all"
               :on-click #(do
                            (reset! show-followed-only-atom (if @show-followed-only-atom false true))
                            (fetch-badge-adverts state))}
      [:i {:class "fa fa-bookmark"}] (str " " (t :extra-application/Favourites))]]]])

(defn gallery-grid-form [state]
  (let [show-advanced-search (cursor state [:advanced-search])
        show-followed-only-atom (cursor state [:show-followed-only])
        link-or-embed-atom (cursor state [:show-link-or-embed-code])
        user (session/get :user)]
    [:div {:id "grid-filter"
           :class "form-horizontal"}
     (when (= "admin" (:role user))
           [:div [share link-or-embed-atom (or (:query-param @state)(.-href js/window.location))]])
     [text-field :name  nil #_(t :gallery/Badgename) (t :gallery/Searchbybadgename) state]
     [:div {:style {:margin-bottom "10px"}}
      [:a {:on-click #(reset! show-advanced-search (not @show-advanced-search))
           :href "#"}
       (if @show-advanced-search
         (t :gallery/Hideadvancedsearch)
         (t :gallery/Showadvancedsearch))]]
     (when @show-advanced-search
      [:div
       [country-selector state]
       [autocomplete state]
       (when user [follow-grid-item show-followed-only-atom state])])

     [grid-radio-buttons (str (t :core/Order) ":") "order" (order-radio-values) [:params :order] state fetch-badge-adverts]
     (when @show-advanced-search [issuer-info-grid state])]))

(defn badge-grid-element [element-data state]
  (let [{:keys [id image_file name  issuer_content_name issuer_content_url recipients badge_content_id followed issuer_tier]} element-data
        badge-id (or badge_content_id id)]
    [:div {:class "media grid-container"}
     (if (pos? followed)
       [:a.following-icon {:href "#" :on-click #(remove-from-followed id state) :title (t :extra-application/Removefromfavourites)} [:i {:class "fa fa-bookmark"}]])
     [:div.media-content
      [:a {:href "#" :on-click #(do (.preventDefault %) (mo/open-modal [:application :badge] {:id id  :state state}))}
       (if image_file
         [:div.media-left
          [:img {:src (str "/" image_file)
                 :alt name}]])
       [:div.media-body
        [:div {:class "media-heading"}
         [:span name]]
        [:div.media-issuer
         [:p issuer_content_name]]
        [:div.media-getthis
         [:span [:i.apply-now-icon {:class "fa fa-angle-double-right"}] (str " " (t :extra-application/Getthisbadge))]]]]
      [:div.media-bottom]]]))

(defn shuffle-featured-badges [n state]
  (let [badges (filter #(= (:issuer_tier %) "pro") (:applications @state))]
    (take n (distinct (shuffle badges)))))

(defn gallery-grid [state]
  (let [badges (:applications @state)
        tags (cursor state [:params :tags])
        show-issuer-info-atom (cursor state [:show-issuer-info])
        show-featured (cursor state [:show-featured])
        featured-badges (shuffle-featured-badges 4 state)
        deficit (- 4 (count featured-badges))
        unfeatured-badges (remove (fn [app] (some #(identical? % app) featured-badges)) badges)
        featured (if (= 1 (count badges))
                   badges
                   (if (< (count featured-badges) 4) (into featured-badges (take deficit (shuffle (distinct unfeatured-badges)))) featured-badges))
        grid-badges (if (and @show-featured (not @show-issuer-info-atom)) (remove (fn [app] (some #(identical? % app) featured)) badges) badges)]
    [:div
     (when (and (seq badges) (not @show-issuer-info-atom) @show-featured)
       [:div.panel.featured-gallery {:class "row wrap-grid"
                                     :id    "grid"}

        [:div.close {:style {:opacity 1}};.close-button
         [:a {:aria-label "OK"
              :on-click   #(do
                             (.preventDefault %)
                             (swap! state assoc :show-featured false))}
          [:i.fa.fa-remove {:title (t :core/Cancel)}]]]
        [:h3.panel-heading {:style {:text-align "center"}} (t :extra-application/Featured)]
        [:div
         (into [:div.adcontainer]
               (for [element-data featured]
                 (badge-grid-element element-data state)))]])

     [:h3 (str-cat @tags)]
     (into [:div {:class "row wrap-grid"
                  :id    "grid"}]
           (for [element-data grid-badges]
             (badge-grid-element element-data state)))]))

(defn content [state]
  (create-class {:reagent-render (fn []
                                   [:div {:id "badge-advert"}

                                    [m/modal-window]
                                    [gallery-grid-form state]
                                    [gallery-grid state]])
                 :component-did-mount (fn []
                                        (disable-background-image)
                                        (if (:init-id @state) (open-modal (:init-id @state) state)))}))

(defn- init-autocomplete
  "initialize autocomplete when url params include tag(s)"
  [tags state]
  (when-not (blank? tags)
    (swap! state assoc :tags tags)
    (let [tag-coll (->> (clojure.string/split tags #",")
                        (mapv #(str "#" %)))
          autocomplete-items (cursor state [:autocomplete :tags :items])]
      (-> (clojure.set/map-invert @autocomplete-items) (select-keys tag-coll) vals set))))

(defn init-data [params state]
 (let [{:keys [order tags name issuer country followed]} params]
  (all-issuers state params)
  (autocomplete-search state country)
  (when (= true followed)(swap! state assoc :show-followed-only followed))
  (ajax/GET
   (path-for "/obpv1/application/")
   {:params params
    :handler (fn [data]
              (when (or (not (blank? tags))  (= true followed) (= "true" followed) #_(not= "all" country)) (swap! state assoc :advanced-search true))
              (when (or (not= order "mtime") (not (empty? tags)) followed (not (blank? name)) (not (blank? issuer)))
                 (swap! state assoc :show-featured false))
              (swap! state assoc :applications (:applications data)
                                 :countries (:countries data)
                                 :initial-query @initial-query-params)

              (init-issuer issuer state)
              (swap! state assoc-in [:autocomplete :tags :value] (or (init-autocomplete tags state) #{})))})))

(defn handler [site-navi params]
  (let [query (as-> (-> js/window .-location .-href url/url :query keywordize-keys) $
                    (if (:country $)
                      $
                      (assoc $ :country (session/get-in [:filter-options :country]
                                                        (session/get-in [:user :country] "all")))))
        {:keys [badge_content_id user-id]} params

        params (query-params query)
        state (atom {:params params
                     :init-id (:id params)
                     :user-id user-id
                     :issuer {:issuer-search false :search-result [] :show-issuer-info false :issuer-content {:name (t :core/All)}}
                     :autocomplete {:tags {:value #{} :items #{}}}
                     :applications []
                     :countries []
                     :country-selected  (session/get-in [:filter-options :country] (:country params))
                     :show-followed-only false
                     :show-featured true
                     :advanced-search false
                     :timer nil
                     :ajax-message nil
                     :initial-query nil
                     :show-link-or-embed-code nil})]
   (reset! initial-query-params params)
   (init-data params state)
   (fn []
     (if (session/get :user)
       (layout/default site-navi [content state badge_content_id])
       (layout/landing-page site-navi [content state badge_content_id])))))

(defn ^:export latestearnablebadges []
  (let [params (query-params {:country "all"})
        state (atom {})]
    (init-data params state #_{:country "all" :order "mtime" :issuer-name "" :name ""})
    (fn []
      [:div.badges {:style {:display "inline-block"}}
       [:p.header (t :social/Newestearnablebadges)]
       (reduce (fn [r application]
                 (conj r [:a {:href "#" :on-click #(do
                                                     (.preventDefault %)
                                                     (mo/open-modal [:application :badge] {:id (:id application) :state state}))}
                          [:img {:src (str "/" (:image_file application))}]]))
               [:div] (take 5 (:applications @state)))])))

(defn ^:export button [opt]
  (fn []
    (case opt
      "button" [:div [:a.btn.button {:href (path-for "/badge/application")} (t :extra-application/Application)]]
      "link" [:a {:href (str (path-for "/badge/application"))} [:p (t :social/Iwanttoearnnewbadges)]]
      [:div [:a.btn.button {:href (path-for "/badge/application")} (t :extra-application/Application)]])))
