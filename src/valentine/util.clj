(ns valentine.util)

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
