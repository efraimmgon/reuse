(ns reuse.routes.fsdb
  (:require
   [ring.util.http-response :as response]
   [reuse.utils.fsdb :as fsdb]))

(def fns
  {:get-by-id fsdb/get-by-id
   :get-all fsdb/get-all
   :select fsdb/select
   :create! fsdb/create!
   :create-raw! fsdb/create-raw!
   :update! fsdb/update!
   :upsert! fsdb/upsert!
   :delete! fsdb/delete!
   :delete-coll! fsdb/delete-coll!})


(defn dispatch
  [{:keys [fun params]}]
  (let [f (fns fun)
        result (f params)]
    (response/ok
     (if (seq result)
       result
       []))))