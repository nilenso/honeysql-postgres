(ns honeysql-postgres.helpers
  (:refer-clojure :exclude [update partition-by])
  (:require [honeysql.helpers :refer [defhelper collify plain-map?]]))

;; Extension of the honeysql helper funcitons for postgreSQL

(defn do-nothing [m]
  "Accepts a map and append {:do-nothing []} to it"
  (assoc m :do-nothing []))

(defhelper do-update-set [m args]
  (assoc m :do-update-set (collify args)))

(defhelper do-update-set! [m args]
  (assoc m :do-update-set! args))

(defhelper on-conflict [m args]
  (assoc m :on-conflict args))

(defhelper on-conflict-constraint [m args]
  (assoc m :on-conflict-constraint args))

(defhelper upsert [m args]
  (if (plain-map? args)
    (assoc m :upsert args)
    (assoc m :upsert (first args))))

(defhelper returning [m fields]
  (assoc m :returning (collify fields)))

(defhelper create-view [m viewname]
  (assoc m :create-view (collify viewname)))

(defhelper create-table [m tablename]
  (assoc m :create-table (collify tablename)))

(defhelper with-columns [m args]
  (assoc m :with-columns args))

(defhelper drop-table [m tablenames]
  (assoc m :drop-table (collify tablenames)))

(defhelper over [m args]
  (assoc m :over (collify args)))

(defhelper window [m args]
  (assoc m :window args))

(defhelper partition-by [m fields]
  (assoc m :partition-by (collify fields)))

(defhelper alter-table [m fields]
  (assoc m :alter-table (collify fields)))

(defhelper add-column [m fields]
  (assoc m :add-column (collify fields)))

(defhelper drop-column [m fields]
  (assoc m :drop-column (collify fields)))

(defhelper rename-column [m fields]
  (assoc m :rename-column fields))

(defhelper rename-table [m fields]
  (assoc m :rename-table (collify fields)))

(defhelper insert-into-as [m fields]
  (assoc m :insert-into-as (collify fields)))
