(ns reuse.routes.services
  (:require
   [buddy.auth :refer [authenticated?]]
   [clojure.spec.alpha :as s]
   [reitit.coercion.spec :as spec-coercion]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reuse.middleware.exception :as exception]
   [reuse.middleware.formats :as formats]
   [reuse.routes.fsdb :as fsdb]
   [ring.util.http-response :refer :all]))

(s/def :result/result keyword?)
(s/def :result/message (or string? keyword?))


(s/def :result/Result
  (s/keys :req-un [:result/result]
          :opt-un [:result/message]))

;;; -----------------------------------------------------------------------
;;; Utils
;;; -----------------------------------------------------------------------


(defn admin?
  "Takes a request and returns true if the user is an admin."
  [{:keys [identity]}]
  (and identity
       (:user/admin identity)))


(defn forbidden-error []
  (forbidden
   {:error "Action not permitted for this user."}))


;;; -----------------------------------------------------------------------
;;; Middlewares
;;; -----------------------------------------------------------------------


(defn admin-middleware
  "Return a middleware that checks if the user is an admin. If not, return a
   forbidden error."
  [handler]
  (fn [req]
    (if (admin? req)
      (handler req)
      (forbidden-error))))


(defn auth-middleware
  "Return a middleware that checks if the user is authenticated. If not, return
   an unauthorized error."
  [handler]
  (fn [req]
    (if (authenticated? req)
      (handler req)
      (unauthorized
       {:error "You must be logged in to perform this action."}))))


(defn users-any-granted?
  "Return true if the user is an admin or the user is the owner of the"
  [{:keys [parameters identity]}]
  (when identity
    (or (:user/admin identity)
        (= (:user/id identity)
           (get-in parameters [:path :user/id])))))


(defn user-any-granted-middleware
  "Return a middleware that checks if the user is an admin or the user is the
   owner of the resource. If not, return a forbidden error."
  [handler]
  (fn [req]
    (if (users-any-granted? req)
      (handler req)
      (forbidden-error))))

;;; -----------------------------------------------------------------------
;;; Security rules
;;; -----------------------------------------------------------------------
; Similar to Firestore security rules.

; A set of rules that can be used to check if a user is allowed to perform
; an action. The rules are functions that take a request and return true if
; the user is allowed to perform the action, or false otherwise.

; Since it is raw clj code, it can be extended to do more complex checks, like
; per tname, id, etc.

(def GET #{:get-by-id
           :get-all
           :select})

(def CREATE #{:create!
              :create-raw!
              :upsert!})

(def UPDATE #{:upsert!
              :update!})

(def DELETE #{:delete!
              :delete-coll!})



(defn GET? [fsdb-req]
  (contains? GET (:fun fsdb-req)))

(defn CREATE? [fsdb-req]
  (contains? CREATE (:fun fsdb-req)))

(defn UPDATE? [fsdb-req]
  (contains? UPDATE (:fun fsdb-req)))

(defn DELETE? [fsdb-req]
  (contains? DELETE (:fun fsdb-req)))



(defn READ?
  "Return true if the :fun requested is a read operation."
  [fsdb-req]
  (GET? fsdb-req))

(defn WRITE?
  "Return true if the :fun requested is a write operation."
  [fsdb-req]
  (or (CREATE? fsdb-req)
      (UPDATE? fsdb-req)
      (DELETE? fsdb-req)))

;;; -----------------------------------------------------------------------
;;; Admin

(defn allow-read-if-admin [req]
  (and (admin? req)
       (READ? (get-in req [:parameters :body]))))

(defn allow-write-if-admin [req]
  (and (admin? req)
       (WRITE? (get-in req [:parameters :body]))))

;;; -----------------------------------------------------------------------
;;; Authenticated

(defn allow-read-if-authenticated [req]
  (and (authenticated? req)
       (READ? (get-in req [:parameters :body]))))

(defn allow-write-if-authenticated [req]
  (and (authenticated? req)
       (WRITE? (get-in req [:parameters :body]))))

;;; -----------------------------------------------------------------------
;;; Resource owner

(defn allow-read-if-owner [req]
  (and (users-any-granted? req)
       (READ? (get-in req [:parameters :body]))))

(defn allow-write-if-owner [req]
  (and (users-any-granted? req)
       (WRITE? (get-in req [:parameters :body]))))


;;; -----------------------------------------------------------------------
;;; Routes

(s/def :fsdb/fun keyword?)
(s/def :fsdb/coll (s/or :keyword keyword? :string string? :vector vector?))
(s/def :fsdb/id (s/or :string string? :int int?))
(s/def :fsdb/data map?)
(s/def :fsdb/where map?)
(s/def :fsdb/order-by map?)
(s/def :fsdb/offset map?)
(s/def :fsdb/limit map?)
(s/def :fsdb/opts map?)

; :fsdb/Request
; a map that must contain :fsdb/fun and :fsdb/params, which
; is a map that must contain tname and  
; may contain :fsdb/id, :fsdb/data or :fsdb/opts

(s/def :fsdb/params
  (s/keys :req-un [:fsdb/coll]
          :opt-un [:fsdb/id
                   :fsdb/data
                   :fsdb/where
                   :fsdb/order-by
                   :fsdb/offset
                   :fsdb/limit
                   :fsdb/opts]))

(s/def :fsdb/Request
  (s/keys :req-un [:fsdb/fun :fsdb/params]))


(s/def :fsdb/Result (s/or :nil nil?
                          :map map?
                          :seq (s/coll-of map?)))


(defn fsdb-routes []
  ["/fsdb"
   {:parameters {:body :fsdb/Request}
    :post {:summary "A fsdb query"
           :responses {200 {:body :fsdb/Result}}
           :handler (fn [{:keys [parameters]}]
                      (fsdb/dispatch (:body parameters)))}}])

;; EXAMPLE ROUTER
#_(defn project-routes []
    ["/projects"
     [""
      {:get {:summary "Return all project records."
             :responses {200 {:body :project/projects}}
             :handler project/get-projects}
       :post {:summary "Create a project record in the db."
              :parameters {:body (s/keys :req-un [:project/title]
                                         :opt-un [:project/description])}
              :responses {200 {:body :project/Project}}
              :handler (fn [{:keys [parameters]}]
                         (project/create-project!
                          (:body parameters)))}}]

     ["/{project-id}"
      {:parameters {:path {:project-id int?}}}
      [""
       {:get {:summary "Return a project record by id."
              :responses {200 {:body :project/Project}}
              :handler (fn [{:keys [parameters]}]
                         (project/get-project
                          (get-in parameters [:path :project-id])))}
        :put {:summary "Update a project record with params."
              :parameters {:body (s/keys :req-un [:project/id
                                                  :project/title]
                                         :opt-un [:project/description])}
              :responses {200 {:body :project/Project}}
              :handler (fn [{:keys [parameters]}]
                         (project/update-project!
                          (:body parameters)))}
        :delete {:summary "Delete a project record by id."
                 :responses {200 {:body :result/Result}}
                 :handler (fn [{:keys [parameters]}]
                            (project/delete-project!
                             (get-in parameters [:path :project-id])))}}]
    ;; "/api/project/{project-id}/stories/"
      (story-routes)]])


(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}
    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
            {:url "/api/swagger.json"
             :config {:validator-url nil}})}]]

   (fsdb-routes)
   ;; "/projects/..."
   ;; Example routes
   #_(project-routes)])