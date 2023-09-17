(ns reuse.utils.views
  (:require
    [ebs.app.timer.views :as timer-views]
    [ebs.utils.components :as c]
    [ebs.utils.events :refer [<sub]]
    [re-frame.core :as rf]
    [reagent.core :as r]))


;;; ---------------------------------------------------------------------------
;;; Modal


(defn modal-ui
  "Component to display the currently active modal."
  []
  (when-let [modal @(rf/subscribe [:modal])]
    [modal]))


(defn error-modal-ui
  "Component to display the currently error (on a modal)."
  []
  (when-let [error-msg (<sub [:common/error])]
    [c/modal
     {:header
      "An error has occured"
      :body [c/pretty-display error-msg]
      :footer
      [:div
       [:button.btn.btn-sm.btn-danger
        {:on-click #(rf/dispatch [:common/set-error nil])}
        "OK"]]}]))


;;; ---------------------------------------------------------------------------
;;; Base


(defn navbar [])


(defn sidebar [])


(defn base-ui [& components]
  (r/with-let [_user (rf/subscribe [:identity])]
    [:div
     [navbar]
     [:div.container-fluid
      [modal-ui]
      [error-modal-ui]
      [:div.row
       [sidebar]
       [:main.col-md-9.ml-sm-auto.col-lg-10.pt-3.px-4
        {:role "main"}
        (into [:div]
          components)]]]]))
