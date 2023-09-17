(ns reuse.auth.views
  (:require
    [reuse.utils.components :as c :refer [form-group]]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [reuse.utils.forms :refer [input]]
    reuse.auth.handlers))



(defn login-form-body [path]
  [:div.card-body
   [:div.input-group
    [:div.input-group-prepend
     [:span.input-group-text [:i.material-icons "face"]]]
    [input {:type :text
            :name (conj path :users/username)
            :class "form-control"
            :auto-focus true
            :placeholder "Nome de usuário"}]]
   [:div.input-group
    [:div.input-group-prepend
     [:span.input-group-text
      [:i.material-icons "lock_outline"]]]
    [input {:type :password
            :name (conj path :users/password)
            :class "form-control"
            :placeholder "Senha"}]]])


(defn login-form-ui [current-user]
  (let [path [:auth/form]
        fields (rf/subscribe path)]
    (fn []
      [:div.card.card-login
       [:form.form
        [:div.card-header.card-header-primary.text-center
         [:h4.card-title "Login"]]
        [login-form-body path]
        [:div.footer.text-center
         [:a.btn.btn-primary.btn-link.btn-wd.btn-lg
          {:on-click #(rf/dispatch-sync
                        [:auth/login {:params fields
                                      :path path}])}
          "Entrar"]]]])))


(defn login-form [path]
  [:div
   [form-group
    "Username"
    [input {:type :text
            :name (conj path :users/username)
            :class "form-control"
            :auto-focus true}]]
   [form-group
    "Password"
    [input {:type :password
            :name (conj path :users/password)
            :class "form-control"}]]])


(defn login-modal []
  (let [path [:auth/form]
        fields (rf/subscribe path)]
    (fn []
      [c/modal
       {:attrs {:on-key-down (c/on-key-handler
                               {"Enter" #(rf/dispatch [:auth/login fields])
                                "Escape" #(rf/dispatch [:remove-modal])})}
        :header "Login"
        :body
        ;; TODO: validation
        [login-form path]
        :footer
        [:div.pull-left
         [:button.btn.btn-primary
          {:on-click #(rf/dispatch [:auth/login fields])}
          "Login"] " "
         [:button.btn.btn-danger
          {:on-click #(rf/dispatch [:remove-modal])}
          "Cancel"]]}])))


(defn register-form [path]
  [:div
   [form-group
    "Username"
    [input {:type :text
            :name (conj path :users/username)
            :class "form-control"
            :auto-focus true}]]
   [form-group
    "Password"
    [input {:type :password
            :name (conj path :users/password)
            :class "form-control"}]]

   [form-group
    "Confirm Password"
    [input {:type :password
            :name (conj path :users/password-confirm)
            :class "form-control"}]]])


(defn register-modal []
  (r/with-let [path [:auth/form]
               fields (rf/subscribe path)]
    [c/modal
     {:attrs {:on-key-down
              (c/on-key-handler
                {"Enter" #(rf/dispatch [:auth/register fields])
                 "Escape" #(rf/dispatch [:remove-modal])})}
      
      :header "Register"
      
      :body
      ;; TODO: validation
      [register-form path]
      
      :footer
      [:div
       [:button.btn.btn-primary
        {:on-click #(rf/dispatch [:auth/register fields])}
        "Register"] " "
       [:button.btn.btn-danger
        {:on-click #(rf/dispatch [:remove-modal])}
        "Cancel"]]}]))
