(ns reuse.auth.handlers
  (:require
    [ajax.core :as ajax]
    clojure.string
    [goog.crypt.base64 :as b64]
    [re-frame.core :as rf]
    [reuse.utils.events :refer [query base-interceptors]]))


;;; ---------------------------------------------------------------------------
;;; Utils
;;; ---------------------------------------------------------------------------


(defn encode-auth
  [& args]
  (->> args
    (interpose ":")
    (apply str)
    (b64/encodeString)
    (str "Basic ")))


;;; ---------------------------------------------------------------------------
;;; Handlers 
;;; ---------------------------------------------------------------------------


(def timeout-ms
  "Max duration of a user session, with no actions."
  ;; 30 minutes
  (* 1000 60 30))

(rf/reg-event-fx
  :auth/session-timer
  base-interceptors
  (fn [{:keys [db]} _]
    (let [current-user (rf/subscribe [:identity])
          user-event (rf/subscribe [:query [:user-event]])]
      
      (when @current-user
              
        (if @user-event
          (do
            (println "Setting session timer.")
            (js/setTimeout #(rf/dispatch [:auth/session-timer]) timeout-ms)
            {:db (assoc db :user-event nil)})
                
          (do 
            (println "Session timeout. Logging user out.")
            {:dispatch-n [[:auth/logout]
                          [:navigate! :home]]}))))))

; :auth/login

(rf/reg-event-fx
  :auth/login-success
  base-interceptors
  (fn [{:keys [db]} [path user]]
    {:db (assoc-in db path nil)
     :dispatch-n [[:set-identity user]]}))


(rf/reg-event-fx
  :auth/login
  base-interceptors
  (fn [_ [{:keys [params path]}]]
    {:http-xhrio {:uri "/api/login"
                  :method :post
                  :headers {"Authorization"
                            (encode-auth (clojure.string/trim (:users/username @params))
                              (:users/password @params))}
                  :params @params
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:auth/login-success path]
                  :on-failure [:common/set-error]}}))


; :auth/password

(rf/reg-event-fx
  :auth/update-password
  base-interceptors
  (fn [{:keys [db]} [path password]]
    {:db (-> db
           (assoc-in [:identity :users/password] password) 
           (assoc-in path nil))
     :dispatch [:remove-modal]}))


(rf/reg-event-fx
  :auth/update-password
  base-interceptors
  (fn [_ [{:keys [fields path]}]]
    {:http-xhrio {:method          :get
                  :uri             "https://api.github.com/orgs/day8"
                  :params fields
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:auth/update-password path]
                  :on-failure      [:common/set-error]}}))


; :auth/logout

(rf/reg-event-fx
  :auth/logout
  base-interceptors
  (fn [_ _]
    {:http-xhrio {:method :post
                  :uri   "/api/logout"
                  :on-success [:set-identity nil]
                  :on-failure [:common/set-error]}}))


;; :auth/register

(rf/reg-event-fx
  :auth/register-success
  base-interceptors
  (fn [_ [user]]
    {:dispatch-n [[:set-identity user] 
                  [:remove-modal]]}))


(rf/reg-event-fx
  :auth/register
  base-interceptors
  (fn [_ [fields]]
    ;; TODO: validation
    {:http-xhrio {:method :post
                  :uri   "/api/register"
                  :params @fields
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [:auth/register-success]
                  :on-failure [:common/set-error]}}))


;;; ---------------------------------------------------------------------------
;;; Subscriptions
;;; ---------------------------------------------------------------------------


(rf/reg-sub :auth/form query)