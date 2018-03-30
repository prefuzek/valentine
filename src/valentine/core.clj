(ns valentine.core
  (:require
   [clojure.walk :refer [walk postwalk]]))

(defonce debug (atom true))

(declare conforms? conforms*?)

(defprotocol Matchable
  (match [this obj]))

(defrecord Matcher [match-fn]
  Matchable
  (match [_ obj] (match-fn obj)))

(defrecord Binding [conforms bindings])

(defn binding? [x] (= (type x) valentine.core.Binding))

;; utils
;; -----

(defn multi-reduce
  "Reduces over multiple collections. f is a function of n+1 variables
  where n is the number of collections."
  [f init & cols]
  (loop [cols cols result init]
    (let [firsts (map first cols)
          rests (map rest cols)]
      (if (some nil? firsts)
        result
        (recur rests (apply f result firsts))))))

(defn merge-bindings [b1 b2]
  (Binding. (and (:conforms b1) (:conforms b2))
            (into (:bindings b1) (:bindings b2))))

(defn matches-coll
  ([obj matcher]
   (matches-coll obj matcher coll?))
  ([obj matcher coll-type]
   (and (coll-type obj)
        (= (count obj) (count matcher))
        (multi-reduce #(merge-bindings %1 (conforms*? %2 %3))
                        #_#(and %1 (conforms*? %2 %3))
                        {:conforms true :bindings []}
                        obj matcher))))

(defn coll-of-fn [matcher]
  (fn [obj] (reduce #(merge-bindings %1 (conforms*? %2 matcher))
                    #_#(and %1 (conforms*? %2 matcher))
                    (Binding. true [])
                    obj)))

;;   conforms? multimethod
;;   ---------------------

(defn conforms? [obj matcher]
  (boolean (:conforms (conforms*? obj matcher))))

(defn- dispatch
  "Dispatch fn for conforms?"
  [_ matcher]
  (cond (satisfies? Matchable matcher) :matchable
        (fn? matcher) :fn
        (map? matcher) :map
        (map-entry? matcher) :map-entry
        (list? matcher) :list
        (vector? matcher) :vector
        (set? matcher) :set
        :else (type matcher)))

(defmulti conforms*? dispatch)

(defmethod conforms*? :matchable [obj matcher]
  (if @debug (println "matchable"))
  (match matcher obj))

(defmethod conforms*? :fn [obj matcher]
  (if @debug (println "fn"))
  (try (Binding. (matcher obj) [])
       (catch java.lang.IllegalArgumentException e
         false)
       (catch java.lang.ClassCastException e
         false)))

(defmethod conforms*? :list [obj matcher]
  (if @debug (println "list"))
  (matches-coll obj matcher list?))

(defmethod conforms*? :vector [obj matcher]
  (if @debug (println "vector"))
  (matches-coll obj matcher vector?))

(defmethod conforms*? :map-entry [obj matcher]
  (if @debug (println "map-entry"))
  (and (map-entry? obj)
       (conforms*? (key obj) (key matcher))
       (conforms*? (val obj) (val matcher))))

(defmethod conforms*? :map [obj matcher]
  (if @debug (println "map"))
  (matches-coll obj matcher map?))

(defmethod conforms*? :set [obj matcher]
  (if @debug (println "set"))
  (and (set? obj)
       (= (count obj) (count matcher))
       (reduce (fn [b m] (and b (some #(conforms*? % m) obj))) true matcher)
       (reduce (fn [b o] (and b (some #(conforms*? o %) matcher))) true obj)))

(defmethod conforms*? :default [obj matcher]
  (if @debug (println "default"))
  (Binding. (= obj matcher) []))


;; Matcher record functions
;; ------------------------

(defn literal [obj]
  (Matcher.  #(if (= % obj) (Binding. true [])
                  (Binding. false []))))

(defn coll [& args]
  (Matcher. #(matches-coll % args)))

(defn coll-of [matcher]
  (Matcher. (coll-of-fn matcher)))

(defn vec-of [matcher]
  (Matcher. (fn [obj] (and (vector? obj) ((coll-of-fn matcher) obj)))))

(defn list-of [matcher]
  (Matcher. (fn [obj] (and (list? obj) ((coll-of-fn matcher) obj)))))
  
(defn set-of [matcher]
  (Matcher. (fn [obj] (and (set? obj) ((coll-of-fn matcher) obj)))))

(defn mset [& args]
  "Matches unordered sets, but allows duplicate arguments."
  (Matcher.
   (fn [obj]
     (and (set? obj)
          (= (count obj) (count args))
          (let [matcher-counts (group-by identity args)]
            (reduce (fn [b pair]
                      (and b (<= (count (val pair))
                                 (count (filter #(conforms*? % (key pair))
                                                obj)))))
                    true
                    matcher-counts))))))

(defmacro as [matcher binding]
  `(Matcher. (fn [obj#] (if (conforms? obj# ~matcher)
                         (Binding. true {~(str binding) obj#})
                         (Binding. false [])))))

;; Matches fn
;; ----------


(defn matches [obj matcher]
  (if (coll? obj)
    (into (reduce #(into %1 (matches %2 matcher)) '() obj)
          (when (conforms? obj matcher) (list obj)))
    (when (conforms? obj matcher) (list obj))))


;; Bindings fns
;; ------------

(defmacro with [obj matcher & forms]
  (let [conforms (conforms*? obj (eval matcher))]
    (when (:conforms conforms)
      (let [bindings (:bindings conforms)
            key-bindings (map symbol (keys bindings))
            val-bindings (vals bindings)
            binding-form (vec (interleave key-bindings val-bindings))]
        `(let ~binding-form ~@forms)))))

(defmacro update-with [obj matcher f]
  (postwalk #(let [conforms (conforms*? % (eval matcher))]
               (if (:conforms conforms)
                 (let [bindings (:bindings conforms)
                       key-bindings (map symbol (keys bindings))
                       val-bindings (vals bindings)
                       binding-form (vec (interleave key-bindings val-bindings))]
                   `(let ~binding-form (~f ~%)))
                 %))
            obj))

(defmacro replace-with [obj matcher new]
  (postwalk #(let [conforms (conforms*? % (eval matcher))]
               (if (:conforms conforms)
                 (let [bindings (:bindings conforms)
                       key-bindings (map symbol (keys bindings))
                       val-bindings (vals bindings)
                       binding-form (vec (interleave key-bindings val-bindings))]
                   `(let ~binding-form ~new))
                 %))
            obj))
