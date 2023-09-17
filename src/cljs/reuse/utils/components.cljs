(ns reuse.utils.components
  (:require
    cljs.pprint
    [re-frame.core :as rf]))


;;; ---------------------------------------------------------------------------
;;; UTILS


(defn on-key-handler
  "Takes a map of .-key's and functions. Returns a matching function. If
  the event.key str is present in the map, it calls the respective function."
  [keymap]
  (fn [event]
    (when-let [f (get keymap
                   (.-key event))]
      (f))))


; ------------------------------------------------------------------------------
; Debugging
; ------------------------------------------------------------------------------


(defn pretty-display [data]
  [:pre
   (with-out-str
     (cljs.pprint/pprint data))])


; ------------------------------------------------------------------------------
; Forms
; ------------------------------------------------------------------------------


(defn form-group
  "Bootstrap's `form-group` component."
  [label & input]
  [:div.form-group
   [:label.label-control label]
   (into
     [:div]
     input)])


; ------------------------------------------------------------------------------
; Modal
; ------------------------------------------------------------------------------


; Note: on-key-down won't work unless the modal is focused.
(defn modal [{:keys [attrs header body footer]}]
  [:div
   (assoc attrs
     :tab-index 0)
   [:div.modal-dialog
    [:div.modal-content
     (when header
       [:div.modal-header
        [:div.modal-title
         header]])
     (when body [:div.modal-body body])
     (when footer
       [:div.modal-footer
        footer])]]
   [:div.modal-backdrop
    {:on-click #(rf/dispatch [:remove-modal])}]])

