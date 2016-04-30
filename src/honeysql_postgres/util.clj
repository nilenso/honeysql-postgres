(ns ^{:doc "Postgres Honeysql utils"}
    honeysql-postgres.util
  (:refer-clojure :exclude [format partition-by])
  (:require [honeysql.format :refer :all]))

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
