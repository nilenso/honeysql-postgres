
(ns ^{:doc "Postgres Honeysql utils"}
    honeysql-postgres.util
  (:refer-clojure :exclude [format partition-by])
  (:require [honeysql.format :refer [to-sql comma-join paren-wrap]]))

(defn get-first
  "Returns the first element if the passed argument is a collection, else return the passed argument
   as is."
  [x]
  (if (sequential? x)
    (first x)
    x))

(defn comma-join-args
  "Returns the args comma-joined after applying to-sql to them"
  [args]
  (if (nil? args)
    ""
    (->> args
         (map to-sql)
         comma-join
         paren-wrap)))

(defn prep-check
  "Adds a logical `:and` operation if args has multiple vectors
   Eg - (prep-check '([:= :a :b])) => [:= :a :b]
        (prep-check '([:= :a :b] [:< :a :c])) => [:and [:= :a :b] [:< :a :c]]"
  [args]
  (let [preds (if (= 1 (count args))
                  (first args)
                  args)
        [logic-op preds] (if (keyword? (first preds))
                           [(first preds) (rest preds)]
                           [:and preds])]
    (if (= 1 (count preds))
      (first preds)
      (into [logic-op] preds))))
