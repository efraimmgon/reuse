(ns reuse.db.core
  (:require
   [monger.core :as mg]
   [monger.collection :as mc]
   [monger.operators :refer :all]
   [mount.core :refer [defstate]]
   [reuse.config :refer [env]]))

#_(defstate db*
    :start (-> env :database-url mg/connect-via-uri)
    :stop (-> db* :conn mg/disconnect))

#_(defstate db
    :start (:db db*))