(ns salava.test-utils
  (:require [clj-http.client :as client]
            [clj-http.cookies :as cookies]
            [slingshot.slingshot :refer :all]
            [salava.core.components.config :as c]))

(def api-root
  (let [config (c/load-config :core)
        host (get-in config [:http :host])
        port (get-in config [:http :port])]
    (str "http://" host ":"port "/obpv1" )))

(def ^:dynamic *cookie-store*
  (cookies/cookie-store))

(defn login! [email password]
  (client/request {:url (str api-root "/user/login")
                   :method :post
                   :form-params {:email email :password password}
                   :as :json
                   :content-type :json
                   :cookie-store *cookie-store*}))

(defn logout! []
  (client/request {:url (str api-root "/user/logout")
                   :method :post
                   :as :json
                   :content-type :json
                   :cookie-store *cookie-store*}))

(defn test-api-request
  ([method url] (test-api-request method url {}))
  ([method url post-params]
   (try+
     (client/request
       {:method       method
        :url          (str api-root url)
        :content-type :json
        :as :json
        :form-params  post-params
        :throw-exceptions false
        :cookie-store *cookie-store*})
     (catch Exception _))))

(defn test-upload [url upload-data]
  (try+
    (client/request
      {:method           :post
       :url              (str api-root url)
       :multipart        upload-data
       :as               :json
       :throw-exceptions false
       :cookie-store *cookie-store*})
    (catch Exception _)))

(def test-users
  [{:id 1
    :email "test-user@example.com"
    :password "testtest"}
   {:id 2
    :email "another-user@example.com"
    :password "testtest"}
   {:id 3
    :email "third-user@example.com"
    :password "testtest"}])

(defn test-user-credentials [user-id]
  (let [user (->> test-users
                  (filter #(= (:id %) user-id))
                  first)]
    [(:email user) (:password user)]))


