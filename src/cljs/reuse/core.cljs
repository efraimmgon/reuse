(ns reuse.core
  (:require
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]
   [reagent.dom :as rdom]
   [reuse.ajax :as ajax]
   [reuse.events]
   [reuse.router :as router]))


;;; ---------------------------------------------------------------------------
;;; Initialize app
;;; ---------------------------------------------------------------------------


(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'router/page] (.getElementById js/document "app")))


(defn init! []
  (router/start-router!)
  (ajax/load-interceptors!)
  (mount-components))
