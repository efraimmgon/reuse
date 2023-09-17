(ns reuse.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [reuse.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[reuse started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[reuse has shut down successfully]=-"))
   :middleware wrap-dev})
