(ns reuse.utils.fsdb
  (:require
   clojure.edn
   clojure.pprint
   [clojure.java.io :as io]
   [me.raynes.fs :as fs])
  (:import
   java.time.Instant))

;;; ----------------------------------------------------------------------------
;;; Filesystem-based Database
;;; ----------------------------------------------------------------------------

;;; ----------------------------------------------------------------------------
;;; Utils
;;; ----------------------------------------------------------------------------


;;; All the data is placed inside the resources/db folder, by default.
(def db-dir (io/file fs/*cwd* "resources" "db"))
(def settings-path (io/file db-dir "settings.edn"))


(defn load-edn
  "Load edn from an io/reader source (filename or io/resource).
   `source` is a java.io.File."
  [source]
  (try
    (with-open [r (io/reader source)]
      (clojure.edn/read
       {:readers {'inst #(Instant/parse %)}}
       (java.io.PushbackReader.
        r)))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))
    (catch RuntimeException e
      (printf "Error parsing edn file '%s': %s\n" source (.getMessage e)))))


(defn save-edn!
  "Save edn data to file.
   `file` is a java.io.File.
   `data` is a map.
   `opts` is a map of #{:pretty-print}."
  ([file data] (save-edn! file data nil))
  ([file data opts]
   (with-open [wrt (io/writer file)]
     (binding [*out* wrt]
       (if (:pretty-print? opts)
         (clojure.pprint/pprint data)
         (prn data))))
   data))


(declare settings)


(defn parse-coll
  [x]
  (condp #(%1 %2) x
    string? x
    keyword? (name x)
    symbol? (name x)
    (str x)))

(defn resolve-path
  "Returns a vector with the path to the collection's dir.
   `path` is a scalar or a vector of them."
  [path]
  (if (coll? path)
    (->> path
         (map parse-coll))
    [(parse-coll path)]))


(defn resource-path
  "Returns a java.io.File object with the parsed `path` applied to it.
   `path` is a scalar or a vector."
  [path]
  (apply io/file db-dir (resolve-path path)))

(defn make-vec [x]
  (if (coll? x)
    x
    [x]))

(defn get-resource
  "Takes the name of the collection and, optionally, the id (name) of the 
   document.
   Returns the an io/file if the resource exists, else returns nil."
  ([cname]
   (get-resource cname nil))
  ([cname doc-id]
   (let [cname (make-vec cname)
         params (if doc-id
                  (conj cname doc-id)
                  cname)
         file (resource-path params)]
     (when (fs/exists? file)
       file))))

(defn get-doc-file [coll id]
  (io/file (resource-path coll) (str id) "data.edn"))

(defn use-qualified-keywords?
  "Returns true if the settings file has the :use-qualified-keywords? 
   set to `true` It's `false` by default."
  []
  (:use-qualified-keywords? @settings))


(defn get-collection-key
  "Returns the keyword to be used in the collection's document.
   `cname` is the identity of the collection and can be a scalar or a vector.
   `field` is the field name and must be a keyword or string"
  [cname field]
  (if (use-qualified-keywords?)
    (keyword (-> (if (coll? cname)
                   (last cname)
                   cname)
                 name)
             (name field))
    field))

(defn get-id
  [coll data]
  (get data
       (get-collection-key coll :id)))



;;; ----------------------------------------------------------------------------
;;; Settings
;;; ----------------------------------------------------------------------------


; Management of id increment

(def settings
  "DB settings."
  (atom {}))


(defn load-settings! []
  (reset! settings (load-edn settings-path)))


(defn save-settings!
  "Unlike regular documents, the settings file is saved with
   pretty print, to allow for better readbility."
  []
  (save-edn! settings-path @settings {:pretty-print? true}))


(defn next-id!
  "Returns a str from a java.util.UUID object."
  []
  (str (java.util.UUID/randomUUID)))


(defn setup!
  "Checks if the db path and the settings file are set, otherwise will do it."
  [& [opts]]
  (when-not (fs/exists? db-dir)
    (fs/mkdirs db-dir))
  (when-not (fs/exists? settings-path)
    (let [opts (merge {:use-qualified-keywords? false}
                      opts)]
      (save-edn! settings-path opts)))
  (load-settings!))


(defn reset-db!
  "Deletes all data and settings."
  []
  (fs/delete-dir db-dir)
  (setup!))


(setup!)


;;; ----------------------------------------------------------------------------
;;; CREATE, DELETE TABLE
;;; ----------------------------------------------------------------------------


(defn delete-coll!
  "Deletes all data and settings related to the given collection."
  [{:keys [coll]}]
  (let [path (get-resource coll)]
    (when (fs/exists? path)
      (fs/delete-dir path))))


;;; ----------------------------------------------------------------------------
;;; GET, SAVE, DELETE
;;; ----------------------------------------------------------------------------
;;; All files are expected to contain edn objects, so we just use
;;; clojure.edn/read when loading them from the file.


(defn get-by-id
  "Returns the contents of the document queried, if exists."
  [{:keys [coll id]}]
  (some-> (get-resource coll id)
          (io/file "data.edn")
          load-edn))


(defn get-all
  "Reads and returns the contents of the given collection."
  [{:keys [coll]}]
  (some->> (get-resource coll)
           fs/list-dir
           (map fs/name)
           (map #(get-by-id {:coll coll :id %}))))


(defn order-by-helper [order-by-key documents]
  (case order-by-key
    :desc (reverse documents)
    :asc documents
    (sort-by order-by-key documents)))


(defn select
  "Returns a list of documents that match the given key/value pairs.
   
   (select 
     :users 
     {:where #(= (:name %) \"John\"),
      :offset 10, 
      :limit 10, 
      :order-by :age})}))"
  ([{:keys [coll where order-by offset limit]}]
   (let [result
         (some->> (get-all {:coll coll})
                  where (filter where)
                  order-by (order-by-helper order-by)
                  offset (drop offset)
                  limit (take limit))]
     (if (= limit 1)
       (first result)
       result))))


(defn create-raw!
  "Creates a new document. `data` must contain the id of the document. 
   Returns `data` if successful."
  [{:keys [coll data]}]
  (assert (contains? data (get-collection-key coll :id))
          (str
           "You must provide an `id` key, or use `create!` to have it "
           "automatically generated."))

  (let [id (get-id coll data)
        path (io/file (resource-path coll) (str id))]

    (fs/mkdirs path)

    (save-edn! (io/file path "data.edn")
               data)))


(defn create!
  "Creates a new document. Returns the data with the id."
  [{:keys [coll data]}]
  (let [id (next-id!)
        data (assoc data
                    (get-collection-key coll :id)
                    id)]
    (create-raw! {:coll coll :data data})))


(defn update!
  "Updates the document for the given id, only for the keys given in `data`.
    If a document can't be found, returns nil.
    Takes a map with the `coll`, `where` clause, and the `data`.
    
   `opts` is a map with `save-mode` which is set to `:merge` by default,
   but can be set to `:set`, which will replace the whole document with
   the content of `data`."
  [{:keys [coll where data opts]}]
  (let [save-mode (or (:save-mode opts) :merge)
        id (get-id coll where)
        doc-file (get-doc-file coll id)]

    (when (fs/exists? doc-file)
      (if (= save-mode :set)
        (save-edn! doc-file data)
        (save-edn! doc-file
                   (merge (get-by-id {:coll coll :id id})
                          data))))))


(defn upsert!
  "Updates the document if it exists, otherwise creates it.
   Takes a map with the `coll`, `where` clause, and the `data`."
  [{:keys [coll where data opts] :as params}]
  (let [id (get-id coll where)
        doc-file (get-doc-file coll id)]
    (if (fs/exists? doc-file)
      (update! params)
      (create! (select-keys params [:coll :data])))))


(defn delete!
  "Deletes the document. If successful returns true. If it doesn't exist, 
   returns false."
  [{:keys [coll id]}]

  ;; delete the dir at id: /{db-dir}/{coll}/---->{id}/<----
  (some-> (io/file (resource-path coll) (str id))
          fs/delete-dir))



(comment

  ; basic API:

  ; all functions expect a map. a common key is :coll, which is the name of the
  ; collection. other keys are specific to each function.

  ; use create! and create-raw! to create documents inside collections.
  ; collections are created automatically when you create a document inside 
  ; them.

  ; use get-by-id to get a document by its id.
  ; use get-all to get all documents in a collection.
  ; use select to get documents with finer control, using the keys
  ; where, order-by, offset, limit.

  ; use update! to update a document.

  ; use delete-coll! to delete a collection.
  ; use delete! to delete a document.

  ; nested collections are supported, but you must use vectors to specify the
  ; path to the collection.
  ; example: [:users user-id :profiles]
  ; all other functions work the same.

  "tests:"


  (reset-db!)

  ; turn on qualified keywords (it's false by default)
  (swap! settings assoc :use-qualified-keywords? true)
  (save-settings!)

  (create! {:coll :users, :data {:users/name "Guest"}})
  (create! {:coll :users, :data {:users/name "Us3r"}})

  (get-all {:coll :users})

  (get-by-id {:coll :users, :id (-> (get-all {:coll :users})
                                    first
                                    :users/id)})




  (create-raw! {:coll :profile,
                :data {:profile/id "Guest"
                       :profile/dob "2023-01-01"}})

  (get-by-id {:coll :profile :id "Guest"})

  (update! {:coll :profile,
            :where {:profile/id "Guest"},
            :data {:profile/dob "2025-01-01"}})


  ; removing keys from db:
  (update! {:coll :profile,
            :where {:profile/id "Guest"},
            :data {:profile/id "Guest"}
            :opts {:save-mode :set}})

  ; delete a document:
  (delete! {:coll :profile :id "Guest"})

  ; delete a collection:
  (delete-coll! {:coll :profile})


  ; you can nest collections by your hearts content using vectors:
  (def user-id "533daebb-cf2a-4bb0-b20a-55811b87a729")
  (create-raw!
   {:coll [:users user-id :profiles]
    :data {:profiles/id "Guest"}})

  ; all other operations work the same:
  (get-by-id {:coll [:users user-id :profiles]
              :id "Guest"})

  (get-all {:coll [:users user-id :profiles]})

  (update! {:coll [:users user-id :profiles]
            :where {:profiles/id "Guest"}
            :data {:profiles/dob "2023-01-01"}})

  (delete! {:coll [:users user-id :profiles]}))




;;;; =>> test with nested collections
;;;; =>> add assertions to basic helper functions
