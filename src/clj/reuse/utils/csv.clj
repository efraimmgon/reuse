(ns utils.csv
  (:require
    [clojure.string :as string]
    [clojure.walk :as walk]))

(defn csv->map
  "Takes colls of csv data, the first being the headers.
  Takes the headers row and assocs each keywordized header to its value."
  [csv-data]
  (let [[header & rows] csv-data]
    (map (fn [row]
           (-> (zipmap
                 (map (comp string/lower-case #(string/replace % #" " "-")) header)
                 row)
             (walk/keywordize-keys)))
      rows)))

(defn to-csv- [colls]
  (->> (map-indexed
         (fn [_i coll]
           (string/join "," coll))
         colls)
    (string/join "\r\n")))

(defn to-csv-string
  "Takes colls and turn them into a csv string.
  If the colls' values are maps, then one must provide the :with-headers option."
  ([colls] (to-csv-string colls nil))
  ([colls opts]
   (if (:with-headers opts)
     (to-csv-
       (cons (map name (keys (first colls)))
         (map vals colls)))
     (to-csv- colls))))
