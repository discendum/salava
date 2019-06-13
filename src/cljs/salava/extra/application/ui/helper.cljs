(ns salava.extra.application.ui.helper
  (:require
   [reagent.core :refer [atom cursor create-class]]
   [reagent.session :as session]
   [salava.core.i18n :refer [t]]
   [clojure.string :refer [join blank?]]
   [dommy.core :as dommy :refer-macros [sel sel1]]
   [salava.core.ui.ajax-utils :as ajax]
   [salava.core.ui.helper :refer [path-for]]
   [clojure.string :refer [trim split]]))

(defn subs-hashtag [text]
  (if (re-find #"^#" text) (subs text 1) text))

(defn- process-tags [tags]
  (as-> (split tags #",") $
        (map #(subs-hashtag %) $)
        (join "," $)))

(defn query-params [base]
  {:country (get base :country "")
   :tags (process-tags (get base :tags ""))
   :name (get base :name "")
   :issuer-name (get base :issuer "")
   :order (get base :order "mtime")
   :id (get base :id nil)
   :followed (get base :followed false)})

(defn application-plugin? []
  (some #(= "extra/application" %) (session/get :plugins)))

(defn url-builder [params state]
  (let [params (remove (fn [[k v]] (blank? v)) params)
        query (join (cons (str (name (key (first params))) "=" (val (first params)))
                          (map #(str (if (not (coll? (val %)))
                                       (str "&" (name (key %)) "=" (val %))
                                       (if (empty? (val %)) "" (if (empty? (rest (val %)))
                                                                 (str "&" (name (key %)) "[0]=" (first (val %)))
                                                                 (join (cons (str "&" (name (key %)) "[0]=" (first (val %)))
                                                                             (map (fn [e] (str "&" (name (key %)) "[" (.indexOf (val %) e) "]=" e)) (rest (val %)))))))))
                               (rest params))))]
    (.replaceState js/history "" "Badge Gallery" (str "?" query))
    (swap! state assoc :query-param (.-href js/window.location))))

(defn str-cat [a-seq]
  (if (empty? a-seq)
    ""
    (let [a-seq (if (string? a-seq) (->> (split a-seq #",") (map #(if (re-find #"^#" %) % (str "#" %)))) a-seq)
          str-space (fn [str1 str2]
                      (str str1 " " str2))]
      (reduce str-space a-seq))))

(defn fetch-badge-adverts [state]
  (let [{:keys [user-id country-selected name issuer-name order tags show-followed-only]} @state
        params (->  (query-params (:params @state)) (assoc :followed show-followed-only))
        initial-params? (= (-> params (dissoc :followed)) (-> (:initial-query @state) (dissoc :followed)))]
    (if  (and initial-params? (false? show-followed-only))
      (swap! state assoc :show-featured true)
      (swap! state assoc :show-featured false))
    (ajax/GET
     (path-for (str "/obpv1/application/"))
     {:params  params
      :handler (fn [data]
                 (swap! state assoc :applications (:applications data))
                 (url-builder params state))})))

(defn taghandler
  "set tag with autocomplete value and accomplish searchs"
  [state value]
  (let [tags (cursor state [:params :tags])
        autocomplete-items (cursor state [:autocomplete :tags :items])]
    (reset! tags (apply str (interpose "," (vals (select-keys @autocomplete-items value)))))
    (fetch-badge-adverts state)))

(defn get-items-key [autocomplete-items tag]
  (key (first (filter #(= (str "#" tag) (val %)) autocomplete-items))))

(defn set-to-autocomplete [state tag]
  (let [key (get-items-key (:autocomplete @state) tag)]
    (if key
      (do
        (swap! state assoc :value #{key})
        (taghandler state #{key})))))

(defn add-to-followed
  "set advert to connections"
  [badge-advert-id data-atom state]
  (ajax/POST
   (path-for (str "/obpv1/application/create_connection_badge_application/" badge-advert-id))
   {:handler (fn [data]
               (if (= "success")
                 (do ;set current data-atom to followed true
                   (swap! data-atom assoc :followed 1)
                   (fetch-badge-adverts state))))}))

(defn remove-from-followed
  "remove advert from connections"
  ([badge-advert-id state]
   (remove-from-followed badge-advert-id nil state))
  ([badge-advert-id data-atom state]
   (ajax/DELETE
    (path-for (str "/obpv1/application/delete_connection_badge_application/" badge-advert-id))
    {:handler (fn [data]
                (if (= "success")
                  (do ;set current data-atom to followed false
                    (if data-atom
                      (swap! data-atom assoc :followed 0))
                    (fetch-badge-adverts state))))})))
