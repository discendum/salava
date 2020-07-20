(ns salava.extra.spaces.schemas
  #? (:clj (:require
            [schema.core :as s]
            [clojure.string :refer [blank?]])
      :cljs (:require
             [schema.core :as s :include-macros true]
             [clojure.string :refer [blank?]])))

(s/defschema space-properties {:css (s/maybe {:p-color (s/maybe s/Str)})})
                                              ;:s-color (s/maybe s/Str)
                                              ;:t-color (s/maybe s/Str)})})

(s/defschema space {:uuid s/Str
                    :name (s/conditional #(not (blank? %)) s/Str)
                    :alias (s/conditional #(not (blank? %)) s/Str)
                    :description (s/conditional #(not (blank? %)) s/Str)
                    :url (s/conditional #(not (blank? %)) s/Str)
                    :logo (s/conditional #(not (blank? %)) s/Str)
                    (s/optional-key :banner) (s/maybe s/Str)
                    :visibility (s/enum "public" "private")
                    :status (s/enum "active" "suspended")
                    (s/optional-key :css) (:css space-properties)
                    (s/optional-key :admins) [s/Int]
                    (s/optional-key  :valid_until) s/Str
                    :ctime s/Int
                    :mtime s/Int
                    (s/optional-key :messages) (s/maybe {(s/optional-key :messages_enabled) s/Bool
                                                         (s/optional-key :enabled_issuers) [(s/maybe s/Str)]})})

(s/defschema create-space (dissoc space :uuid :ctime :mtime :status :visibility))
(s/defschema edit-space (-> create-space (assoc :id s/Int)))