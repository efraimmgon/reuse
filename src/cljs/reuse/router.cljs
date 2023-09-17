(ns reuse.router
  (:require
   [re-frame.core :as rf]
   [reitit.core :as reitit]
   [reitit.frontend.easy :as rfe]))


(defn page []
  (when-let [page @(rf/subscribe [:common/page])]
    [:div
     [page]]))


(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))


; TODO: remove
(defn- home-page []
  [:h1 "Hello wold!!!!"])

(def router
  (reitit/router
   [["/" {:name        :home
          :view        #'home-page
          :controllers []}]]))

(defn start-router! []
  (rfe/start!
   router
   navigate!
   {}))
