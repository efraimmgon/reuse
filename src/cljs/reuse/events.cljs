(ns reuse.events
  (:require
   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   [reuse.utils.events :refer [base-interceptors]]))


;;; ---------------------------------------------------------------------------
;;; HANDLERS
;;; ---------------------------------------------------------------------------

;;; ---------------------------------------------------------------------------
;;; Misc


(rf/reg-event-db
 :assoc-in
 base-interceptors
 (fn [db [path v]]
   (assoc-in db path v)))


(rf/reg-event-db
 :update-in
 base-interceptors
 (fn [db [path f & args]]
   (apply update-in db path f args)))


(rf/reg-event-db
 :modal
 base-interceptors
 (fn [db [comp]]
   (js/window.scrollTo #js {"top" 0 "left" 0 "behavior" "smooth"})
   (let [modal-stack (:modal db)]
     (if (seq modal-stack)
       (update db :modal conj comp)
       (assoc db :modal [comp])))))


(rf/reg-event-db
 :common/navigate
 (fn [db [_ match]]
   (let [old-match (:common/route db)
         new-match (assoc match :controllers
                          (rfc/apply-controllers (:controllers old-match) match))]
     (assoc db :common/route new-match))))


(rf/reg-fx
 :common/navigate-fx!
 (fn [[k & [params query]]]
   (rfe/push-state k params query)))


(rf/reg-event-fx
 :navigate!
 base-interceptors
 (fn [_ [url-key params query]]
   {:common/navigate-fx! [url-key params query]}))


(rf/reg-event-db
 :common/set-error
 base-interceptors
 (fn [db [error]]
   (assoc db :common/error error)))


(rf/reg-event-fx
 :common/log
 base-interceptors
 (fn [_ [error]]
   (js/console.log error)))


(rf/reg-event-fx
 :fsdb/query
 base-interceptors
 (fn [_ [{:keys [params on-success on-failure]}]]
   {:http-xhrio {:method :post
                 :uri "/api/fsdb"
                 :params params
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success on-success
                 :on-failure (or on-failure [:common/log])}}))


;;; ---------------------------------------------------------------------------
;;; SUBSCRIPTIONS
;;; ---------------------------------------------------------------------------


(rf/reg-sub
 :common/route
 (fn [db _]
   (-> db :common/route)))


(rf/reg-sub
 :common/page-id
 :<- [:common/route]
 (fn [route _]
   (-> route :data :name)))


(rf/reg-sub
 :common/page
 :<- [:common/route]
 (fn [route _]
   (-> route :data :view)))


(rf/reg-sub
 :docs
 (fn [db _]
   (:docs db)))


(rf/reg-sub
 :common/error
 (fn [db _]
   (:common/error db)))
