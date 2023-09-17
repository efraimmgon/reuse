(ns reuse.utils.input)


;;; ---------------------------------------------------------------------------
;;; Utilities
;;; ---------------------------------------------------------------------------


(defn clean-attrs [attrs]
  (dissoc attrs
    :doc
    :default-value
    :save-fn
    :display-fn))

; NOTE: Reason for `""`: 
; https://zhenyong.github.io/react/tips/controlled-input-null-value.html
(defn value-attr [value]
  (or value ""))


(defn target-value [event]
  (-> event
    .-target
    .-value))


(defn parse-number [string]
  (when-not (empty? string)
    (let [parsed (js/parseFloat string)]
      (when-not (js/isNaN parsed)
        parsed))))


;;; ---------------------------------------------------------------------------
;;; Core components
;;; ---------------------------------------------------------------------------


(defn text-input
  [{:keys [doc name] :as attrs}]
  (let [edited-attrs
        (merge {:on-change #(swap! doc assoc name (target-value %))}
          (-> attrs
            clean-attrs))]
    (fn []
      [:input
       (assoc edited-attrs
         :value (value-attr (get @doc name))
         :type :text)])))


(defn number-input
  [{:keys [doc name] :as attrs}]
  (let [edited-attrs
        (merge {:on-change (fn [event]
                             (let [value (-> event .-target .-value)]
                               (swap! doc assoc name (parse-number value))))}
          (-> attrs
            clean-attrs))]
    (fn []
      [:input
       (assoc edited-attrs
         :value (value-attr (get @doc name))
         :type :number)])))


(defn textarea
  [{:keys [doc name] :as attrs}]
  (let [edited-attrs
        (merge {:on-change (fn [event]
                             (let [value (-> event .-target .-value)]
                               (swap! doc assoc name value)))}
          (-> attrs
            clean-attrs))]
    (fn []
      [:textarea
       (assoc edited-attrs
         :value (value-attr (get @doc name)))])))


(defn datetime-input
  [{:keys [doc name save-fn display-fn] :as attrs}]
  (let [temporary-value (atom nil)
        save-fn (or save-fn identity)
        display-fn (or display-fn identity)
        edited-attrs
        (merge {:on-change (fn [event]
                             (let [value (-> event .-target .-value)]
                               (reset! temporary-value value)))
                :on-blur (fn [_]
                           (let [value (save-fn
                                         @temporary-value)]
                             (swap! doc assoc name value)))}
          (-> attrs
            clean-attrs))]
    (fn []
      [:input
       (assoc edited-attrs
         :value (value-attr (display-fn (get @doc name)))
         :type :datetime-local)])))


(defn select
  [{:keys [doc name default-value save-fn] :as attrs}
   options]
  (let [save-fn (or save-fn identity)
        edited-attrs
        (merge {:on-change (fn [event]
                             (let [value (-> event .-target .-value)]
                               (swap! doc assoc name (save-fn value))))}
          (-> attrs
            clean-attrs))]
    (when (and default-value (nil? (get @doc name)))
      (swap! doc assoc name default-value))
    (fn []
      (into [:select
             (assoc edited-attrs
               :value (value-attr (get @doc name)))]
        options))))


(comment
  "Checkbox component. 
   - `value` is used, instead of e.target.value.
   - The values of the checkbox are stored in a set. If it is not a set, then
   it is coerced into a set.
   - `default-checked` is a boolean that determines whether the checkbox is
   checked by default.")
(defn checkbox-input
  [{:keys [doc name value] :as attrs}]
  (let [f (fn [acc]
            (let [acc (cond (empty? acc) #{}
                        (set? acc) acc
                        (coll? acc) (set acc)
                        :else #{acc})]
              (if (get acc value)
                (disj acc value)
                (conj acc value))))
        edited-attrs
        (merge {:on-change #(swap! doc update name f value)}
          (clean-attrs attrs))]
    (fn []
      [:input (assoc edited-attrs
                :checked (boolean (get (get @doc name) value)))])))

(defn checkbox-comp
  "Checkbox component, with common boilerplate."
  [{:keys [class label] :as attrs}]
  [:div.form-check
   [:label.form-check-label
    [checkbox-input
     (assoc attrs
       :type :checkbox
       :class (or class "form-check-input"))]
    label
    [:span.form-check-sign>span.check]]])