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


(defn create-admin! []
  (when-not (fsdb/exists? {:coll :users})
    ;; create user `admin`, pass `admin`
    (fsdb/create!
      {:coll :users
       :data {:username "admin"
              :admin? true
              :active? true
              :email "example@gmail.com"
              :password "bcrypt+sha512$86186fc28f83b3e3db78bcf8350a3a57$12$8f215420e68fd7922561167b07354f05d8db6d49e212689e"}})))

(create-admin!)

(comment
  (fsdb/reset-db!))