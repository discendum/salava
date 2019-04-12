(ns salava.core.ui.field
  (:require [cljs-uuid-utils.core :refer [make-random-uuid uuid-string]]
            [salava.core.helper :refer [dump]]))

(defn random-key []
  (-> (make-random-uuid)
      (uuid-string)))

(defn add-data [fields-atom key data]
  (swap! fields-atom assoc key data))

(defn add-field
  ([fields-atom new-field] (dump "called 2") (add-field fields-atom new-field (count @fields-atom)))
  ([fields-atom new-field index]
   (dump "called 3")
   (dump new-field)
    (let [[before-blocks after-blocks] (split-at index @fields-atom)]
      (reset! fields-atom (vec (concat before-blocks [(assoc new-field :key (random-key))] after-blocks)))))
  ([fields-atom new-field key data]
   ;(dump data)
   ;(dump @new-field)
   (dump "called 4")
   (dump new-field)
     ;(dump key)
     ;(add-data fields-atom key data)
     #_(add-field fields-atom new-field (count @fields-atom)))
  ([fields-atom new-field index key data]
     (dump "called 5")
     ;(add-data fields-atom key data)

     #_(add-field fields-atom new-field index)))


(defn remove-field [fields-atom index]
  (let [fields @fields-atom
        start (subvec fields 0 index)
        end (subvec fields (inc index) (count fields))]
    (reset! fields-atom (vec (concat start end)))))

(defn move-field [direction fields-atom old-position]
  (let [new-position (cond
                       (= :down direction) (if-not (= old-position (- (count @fields-atom) 1))
                                             (inc old-position))
                       (= :up direction) (if-not (= old-position 0)
                                           (dec old-position)))]
    (if new-position
      (swap! fields-atom assoc old-position (nth @fields-atom new-position)
                               new-position (nth @fields-atom old-position)))))
