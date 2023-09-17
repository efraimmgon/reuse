(ns reuse.utils.events
  (:require
    [cognitect.transit :as transit]
    [re-frame.core :as rf]))


(def base-interceptors
  [(when ^boolean js/goog.DEBUG rf/debug)
   rf/trim-v])


(defn query [db [event]]
  (get db event))


(defn <sub [query-v]
  (deref (rf/subscribe query-v)))


(defn dispatch-n [& events]
  (doseq [evt events]
    (rf/dispatch evt)))


(defn js->edn [js-objects]
  (let [reader (transit/reader :json)
        js-objects-str (js/JSON.stringify js-objects)
        edn-data (transit/read reader js-objects-str)]
    edn-data))