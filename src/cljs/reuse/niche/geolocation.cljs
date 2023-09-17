(ns reuse.niche.geolocation
  (:require
    [ajax.core :as ajax]
    [reuse.utils.events :refer [base-interceptors]]
    [re-frame.core :as rf]))


;;; ---------------------------------------------------------------------------
;;; Helpers 
;;; ---------------------------------------------------------------------------


(defn loading-msg [msg]
  (rf/dispatch [:assoc-in [:auth.check.notify/loading] msg]))


(defn geo-loc-fail []
  (rf/dispatch [:assoc-in [:auth.checkin/geolocation-off?] true]))


(defn clear-geo-loc-msg []
  (rf/dispatch [:assoc-in [:auth.checkin/geolocation-off?] nil]))


(defn get-current-location [geolocation success fail]
  (loading-msg "Carreando, aguarde...")
  (.getCurrentPosition geolocation success fail))


(defn distance
  "Takes two points (lat, lng) and returns the distance in meters between them."
  [[lat1 lng1] [lat2 lng2]]
  (let [sqr (fn [x] (* x x))
        cos-deg (fn [x] (js/Math.cos (/ (* x js/Math.PI) 180.0)))
        earth-cycle-perimeter (* 40000000.0
                                (cos-deg (/ (+ lat1 lat2)
                                           2.0)))
        dx (/ (* (- lng1 lng2) 
                earth-cycle-perimeter)
             360.0)
        dy (/ (* 39940651.0
                (- lat1 lat2))
             360.0)]
    (js/Math.sqrt (+ (sqr dx)
                    (sqr dy)))))


(defn gcl-success
  [{:keys [location-timeout url handler current-user]}]
  (fn [position]
    (js/clearTimeout location-timeout)
    
    (let [settings (rf/subscribe [:admin/settings])
          lat (-> position .-coords .-latitude)
          lng (-> position .-coords .-longitude)
          d (distance ((juxt :office/latitude :office/longitude) @settings)
              [lat lng])]
      
      (if (or ;; The checkedin user can checkout from any place.
            (:users/is-checkedin @current-user) 
            ;; Minimum distance for the user to checkin (in meters).
            (<= d 500))
        
        (ajax/ajax-request 
          {:uri url
           :method :post
           :params {:office-hours/user-id (:users/id @current-user)
                    :office-hours/lat lat
                    :office-hours/lng lng}
           :handler handler
           :error-handler #(rf/dispatch [:set-error (str (:response %))])
           :finally #(loading-msg nil)
           :format (ajax/json-request-format)
           :response-format (ajax/json-response-format {:keywords? true})})
        
        (loading-msg 
          "Can't checking without being close to the office.")))))


(defn gcl-fail [location-timeout]
  (fn [_error]
    (js/clearTimeout location-timeout)
    (geo-loc-fail)))


;;; ---------------------------------------------------------------------------
;;; Events 
;;; ---------------------------------------------------------------------------


;;; Example usage for geolocation


;; Check if the user is close to the office's lcoation. 
;; Send the user's location to the server, and check him in.
(rf/reg-event-fx
  :geolocation/checkin!
  base-interceptors
  (fn [{:keys [_db]} [current-user]]
    
    (clear-geo-loc-msg)
    
    (if-let [geolocation (.-geolocation js/navigator)]
      (let [location-timeout (js/setTimeout geo-loc-fail 10000)]
        (get-current-location 
          geolocation 
          (gcl-success
            {:location-timeout location-timeout 
             :current-user current-user
             :url "/api/checkin" 
             :handler #(rf/dispatch [:assoc-in [:identity :users/is-checkedin] true])})
          (gcl-fail location-timeout)))
      
      (do 
        (loading-msg nil)
        (geo-loc-fail)))
    nil))


;; Check the user out, sending his location to the server.
(rf/reg-event-fx
  :geolocation/checkout!
  base-interceptors
  (fn [_ [current-user]]
    
    (clear-geo-loc-msg)
    
    (if-let [geolocation (.-geolocation js/navigator)]
      (let [location-timeout (js/setTimeout geo-loc-fail 10000)]
        (get-current-location 
          geolocation 
          (gcl-success 
            {:location-timeout location-timeout 
             :current-user current-user
             :url "/api/checkout" 
             :handler #(rf/dispatch [:assoc-in [:identity :users/is-checkedin] false])})
          (gcl-fail location-timeout)))
      
      (geo-loc-fail))
    nil))
