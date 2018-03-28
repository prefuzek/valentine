(ns valentine.core
  (:require
   [valentine.util :as u]))

(defprotocol Matchable
  (match [this obj]))

(defrecord Matcher [match-fn]
  Matchable
  (match [_ obj] (match-fn obj)))

;; utils
;; *****

(defn matches-coll
  ([obj matcher]
   (matches-coll obj matcher coll?))
  ([obj matcher coll-type]
   (and (coll-type obj)
        (= (count obj) (count matcher))
        (u/multi-reduce #(and %1 (conforms? %2 %3)) true obj matcher))))

(defn coll-of-fn [matcher]
  (fn [obj] (reduce #(and %1 (conforms? %2 matcher)) true obj)))
  
(defn set-matches?
  "Checks unordered matching bijectively for set obj. Matcher not necessarily
  a set literal, may be created by core/mset."
  [obj matcher]
  (and (set? obj)
       (= (count obj) (count matcher))
       (reduce (fn [b m] (and b (some #(conforms? % m) obj))) true matcher)
       (reduce (fn [b o] (and b (some #(conforms? o %) matcher))) true obj)))

;;   conforms? multimethod
;;   ********************

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

(defmulti conforms? dispatch)

(defmethod conforms? :matchable [obj matcher]
  (match matcher obj))

(defmethod conforms? :fn [obj matcher]
  (try (matcher obj)
       (catch java.lang.IllegalArgumentException e
         false)
       (catch java.lang.ClassCastException e
         false)))

(defmethod conforms? :list [obj matcher]
  (matches-coll obj matcher list?))

(defmethod conforms? :vector [obj matcher]
  (matches-coll obj matcher vector?))

(defmethod conforms? :map-entry [obj matcher]
  (and (map-entry? obj)
       (conforms? (key obj) (key matcher))
       (conforms? (val obj) (val matcher))))

(defmethod conforms? :map [obj matcher]
  (matches-coll obj matcher map?))

(defmethod conforms? :set [obj matcher]
  (and (set? obj)
       (= (count obj) (count matcher))
       (reduce (fn [b m] (and b (some #(conforms? % m) obj))) true matcher)
       (reduce (fn [b o] (and b (some #(conforms? o %) matcher))) true obj)))

(defmethod conforms? :default [obj matcher]
  (= obj matcher))


;; Matcher record functions
;; *******************

(defn literal [obj]
  (Matcher. #(= % obj)))

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
                                 (count (filter #(conforms? % (key pair))
                                                obj)))))
                    true
                    matcher-counts))))))
          

;; Matches fn
;; **********

(defn matches [obj matcher]
  (if (coll? obj)
    (into (reduce #(into %1 (matches %2 matcher)) [] obj)
          (when (conforms? obj matcher) [obj]))
    (when (conforms? obj matcher) [obj])))
    

