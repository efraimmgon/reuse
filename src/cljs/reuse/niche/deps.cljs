(ns reuse.niche.deps
  (:require
    [clojure.set :refer [difference intersection]]
    [clojure.string :as string]
    [dommy.core :as dommy :refer-macros [sel1]]
    [oops.core :as oops]
    [reagent.core :as r]
    [re-frame.core :as rf]))


; Script loaders defer script execution  until the file and any 
; required dependencies needed by the module are loaded. Most caches 
; the modules as well, so it's loaded only once, no matter how many times 
; it's requested.

;;; ---------------------------------------------------------------------------
;;; Topological search
;;; ---------------------------------------------------------------------------


(defn without
  "Returns set s with x removed."
  [s x] (clojure.set/difference s #{x}))


(defn take-1
  "Returns the pair [element, s'] where s' is set s with element removed."
  [s] 
  {:pre [(seq s)]}
  (let [item (first s)]
    [item (without s item)]))


(defn no-incoming
  "Returns the set of nodes in graph g for which there are no incoming
  edges, where g is a map of nodes to sets of nodes."
  [g]
  (let [nodes (set (keys g))
        have-incoming (apply clojure.set/union (vals g))]
    (clojure.set/difference nodes have-incoming)))


(defn normalize
  "Returns g with empty outgoing edges added for nodes with incoming
  edges only.  Example: {:a #{:b}} => {:a #{:b}, :b #{}}"
  [g]
  (let [have-incoming (apply clojure.set/union (vals g))]
    (reduce #(if (get % %2) % (assoc % %2 #{})) g have-incoming)))


(defn topological-sort
  "Proposes a topological sort for directed graph g using Kahn's
   algorithm, where g is a map of nodes to sets of nodes. If g is
   cyclic, returns nil."
  ([g]
   (topological-sort (normalize g) [] (no-incoming g)))
  ([g l s]
   (if (empty? s)
     (when (every? empty? (vals g)) l)
     (let [[n s'] (take-1 s)
           m (g n)
           g' (reduce #(update-in % [n] without %2) g m)]
       (recur g' 
         (conj l n) 
         (clojure.set/union s' 
           (clojure.set/intersection 
             (no-incoming g') m)))))))


;;; ---------------------------------------------------------------------------
;;; Core
;;; ---------------------------------------------------------------------------


(defonce config 
  (r/atom {:deps/deps {}
           :deps/dependency-graph []
           :deps/loaded #{}}))


(defn add-dep! 
  "Add/update a depencency in the config atom :deps/deps map."
  [d]
  (let [dmap {(:dep/id d) d}]
    (swap! config update :deps/deps
      merge dmap)))


(defn add-deps! 
  "Define a dependency for loading later. Takes a set of maps 
  of :dep/id, :dep/src (if script), :dep/href (if css), :dep/deps."
  [deps]
  (doseq [d deps]
    (add-dep! d)))


(defn remove-dep! [dep]
  (swap! config update :deps/deps 
    #(dissoc % dep)))


(defn unload-deps! 
  "Takes a coll of deps ids and removes them from the DOM."
  [deps]
  (doseq [id deps]
    (dommy/remove! (sel1 (keyword (str "#" id))))))


(defn load-css! 
  "Load a css dep on the DOM."
  [{:keys [id dep]}]
  (let [onload (fn []
                 (swap! config update :deps/loaded
                   conj id)
                 (println "Loaded:" id))
        link-elt 
        (reduce-kv dommy/set-attr!
          (dommy/create-element :link)
          {:id (:dep/id dep)  
           :href (:dep/href dep)
           :rel "stylesheet"
           :type "text/css"})]
    (dommy/append! (sel1 :body) link-elt)
    ;; img elt
    (reduce-kv dommy/set-attr!
      (dommy/create-element :img)
      {:src (:dep/href dep)
       :onerror onload})))


(defn load-script! 
  "Load a script dep on the DOM."
  [{:keys [id dep]}]
  (let [onload (fn []
                 (swap! config update :deps/loaded
                   conj id)
                 (println "Loaded:" id))
        script-elt
        (reduce-kv dommy/set-attr!
          (dommy/create-element :script)
          {:id (:dep/id dep) :src (:dep/src dep)})]
    (oops/oset! script-elt "onload"
      (fn []
        (swap! config update :deps/loaded
          conj id)
        (println "Loaded:" id)))
    (dommy/append! (sel1 :body) script-elt)))


; - check if all dependencies are available
; - if not, log, wait 1 sec, and try again
(defn load-deps! 
  "Load the set of deps on the DOM in the correct order."
  [deps]
  (let [get-dep #(-> @config :deps/deps (get %))
        dep-loaded? #(get (:deps/loaded @config) %)]
    (doseq [id (->> deps
                 (select-keys (:deps/deps @config))
                 (reduce-kv #(assoc % %2 (:dep/deps %3)) {})
                 topological-sort
                 reverse)]
      (when-not (dep-loaded? id)
        (let [dep (get-dep id)
              m {:id id :dep dep}]
          (if (:dep/src dep)
            (load-script! m)
            (load-css! m)))))))


(defn unavailable-deps 
  "The set of required deps that has not been declared yet"
  [required-deps]
  (->> required-deps
    (remove #(get (:deps/deps @config) %))))
  

; - The order of the values is not important, because if any of the deps
; rely on ther deps, this was already declared at add-deps!.
; - Each dependency may have a dependency, and so forth. Therefore it is 
; necessary to build a tree of the correct order to load them.
; - Keep in mind that a dependency may already be loaded.
; - TODO: make it assynchronous.
; - NOTE: What do I do about css classes? Often stylesheets will interfere with
; one another. The simpler solution seems to load only what is 
; stricly required for each page to function.
; The issue with is that it might be inefficient.

(defn with-deps 
  "Assynchronously load dependencies to the window. Takes a set of deps.
  Its values must be the desired ids provided to `add-deps!`.
  :dep/id must be a valid keyword string.
  Arg map: {:deps dep-set       ; A set of dep/id
            :loading component
            :loaded component}"
  [{:keys [deps _loading _loaded]}]
  
  (when (seq deps)
    ;; Unload deps from the page that won't be required.
    (unload-deps! (difference (:deps/loaded @config) deps))
    (swap! config update :deps/loaded intersection deps))
  
  (fn [{:keys [deps _loading _loaded]}]
    
    (r/create-class
      {:display-name "with-deps"
       
       :component-did-mount
       (fn [_]
         (letfn [(maybe-load-deps! []
                   (if-let [xs (seq (unavailable-deps deps))]
                     (do 
                       (apply println "Waiting for deps to be available:" xs)
                       (try-again-in-a-moment))
                     (load-deps! deps)))
                 
                 (try-again-in-a-moment []
                   (js/window.setTimeout maybe-load-deps! 1000))]
           (maybe-load-deps!))) 
       
       :reagent-render
       (fn [{:keys [deps loading loaded]}]
         (cond (empty? deps) loaded
           (= deps (:deps/loaded @config)) loaded
           :else loading))})))