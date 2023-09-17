(ns reuse.utils.fsdb)



(defn get-by-id
  [{:keys [coll id] :as params}]
  {:fn :get-by-id
   :params params})


(defn get-all
  [{:keys [coll] :as params}]
  {:fn :get-all
   :params params})


(defn select
  [{:keys [coll where order-by offset limit] :as params}]
  {:fn :select
   :params params})


(defn create!
  [{:keys [coll data] :as params}]
  {:fn :create!
   :params params})


(defn create-raw!
  [{:keys [coll data] :as params}]
  {:fn :create-raw!
   :params params})


(defn update!
  [{:keys [coll where data opts] :as params}]
  {:fn :update!
   :params params})


(defn upsert!
  [{:keys [coll where data opts] :as params}]
  {:fn :upsert!
   :params params})


(defn delete!
  [{:keys [coll id] :as params}]
  {:fn :delete!
   :params params})


(defn delete-coll!
  [{:keys [coll] :as params}]
  {:fn :delete-coll!
   :params params})
