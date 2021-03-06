(ns salava.badgeIssuer.util
  (:require
   [clojure.tools.logging :as log]
   [salava.core.util :refer [bytes->base64 get-db-1 get-db]]
   [salava.core.i18n :refer [t]]
   [slingshot.slingshot :refer :all]
   [yesql.core :refer [defqueries]])
  (:import
    [java.io ByteArrayOutputStream]
    [javax.imageio ImageIO]))

(defqueries "sql/badgeIssuer/main.sql")

(defn rand-num [start end]
  (+ start (rand-int (- end start))))

(defn is-badge-creator? [ctx id user-id]
  (let [creator-id (-> (get-selfie-badge-creator {:id id} (get-db-1 ctx)) :creator_id)]
    (= user-id creator-id)))

(defn is-badge-issuer? [ctx user-badge-id issuer-id]
  (= issuer-id (select-selfie-issuer-by-badge-id {:id user-badge-id} (into {:result-set-fn first :row-fn :issuer_id} (get-db ctx)))))

(defn already-issued? [ctx id]
  (if-let [id (check-badge-issued {:id id} (into {:result-set-fn first :row-fn :id} (get-db ctx)))]
    true false))

(defn issuable-from-gallery? [ctx gallery_id]
  (check-badge-issuable {:id gallery_id} (get-db-1 ctx)))

(defn badge-valid?
  "Check if badge exists, has been deleted by owner or is revoked"
  [ctx user-badge-id]
  (some-> (select-issued-badge-validity-status {:id user-badge-id} (into {:result-set-fn first} (get-db ctx)))))

(defn selfie-id []
  (str (java.util.UUID/randomUUID)))

(defn image->base64str [canvas]
  (let [out (ByteArrayOutputStream.)]
    (ImageIO/write canvas "png" out)
    (str "data:image/png;base64," (bytes->base64 (.toByteArray out)))))

(defn- check-image [width height]
 (and
  (some #(= width %) (range (- height 10) (+ height 10)))
  (some #(= height %) (range (- width 10) (+ width 10)))))

(defn upload-image [ctx user file]
  (let [{:keys [size tempfile content-type]} file
        max-size 250000] ;;250kb
    (try+
     (when-not (= "image/png" content-type)
       (throw+ {:status "error" :message "badgeIssuer/FilenotPNG"}))
     (when (> size max-size)
       (throw+ {:status "error" :message "badgeIssuer/Filetoobig"}))
     (let [image (ImageIO/read tempfile) ;;NB Reading image with ImageIO/read strips metadata -> https://stackoverflow.com/questions/31075444/how-to-remove-exif-content-from-a-image-file-of-tiff-png-using-a-java-api
           width (.getWidth image)
           height (.getHeight image)]
       (when-not (check-image width height) ;(= width height)
         (throw+ {:status "error" :message "badgeIssuer/Imagemustbesquare"}))
       {:status "success" :url (image->base64str image)})
     (catch Object _
       (log/error _)
       {:url "" :status "error" :message (:message _)}))))
