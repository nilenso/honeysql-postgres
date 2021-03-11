(ns honeysql-postgres.helpers
  (:refer-clojure :exclude [partition-by filter])
  (:require [honeysql-postgres.format]
            [honeysql.helpers :as sqlh #?(:clj :refer :cljs :refer-macros) [defhelper]]))

;; Extension of the honeysql helper functions for postgreSQL

(defn do-nothing
  "Accepts a map and append {:do-nothing []} to it"
  [m]
  (assoc m :do-nothing []))

(defhelper do-update-set [m args]
  (assoc m :do-update-set (sqlh/collify args)))

(defhelper filter [m args]
  (assoc m :filter (sqlh/collify args)))

(defhelper do-update-set! [m args]
  (assoc m :do-update-set! args))

(defhelper on-conflict [m args]
  (assoc m :on-conflict args))

(defhelper on-conflict-constraint [m args]
  (assoc m :on-conflict-constraint args))

(defhelper upsert [m args]
  (if (sqlh/plain-map? args)
    (assoc m :upsert args)
    (assoc m :upsert (first args))))

(defhelper returning [m fields]
  (assoc m :returning (sqlh/collify fields)))

(defhelper create-view [m viewname]
  (assoc m :create-view (sqlh/collify viewname)))

(defhelper create-table [m tablename]
  (assoc m :create-table (sqlh/collify tablename)))

(defhelper with-columns [m args]
  (assoc m :with-columns args))

(defhelper drop-table [m tablenames]
  (assoc m :drop-table (sqlh/collify tablenames)))

(defhelper over [m args]
  (assoc m :over (sqlh/collify args)))

(defhelper window [m args]
  (assoc m :window args))

(defhelper partition-by [m fields]
  (assoc m :partition-by (sqlh/collify fields)))

(defhelper alter-table [m fields]
  (assoc m :alter-table (sqlh/collify fields)))

(defhelper add-column [m fields]
  (assoc m :add-column (sqlh/collify fields)))

(defhelper drop-column [m fields]
  (assoc m :drop-column (sqlh/collify fields)))

(defhelper rename-column [m fields]
  (assoc m :rename-column fields))

(defhelper rename-table [m fields]
  (assoc m :rename-table (sqlh/collify fields)))

(defhelper insert-into-as [m fields]
  (assoc m :insert-into-as (sqlh/collify fields)))

(defhelper within-group [m args]
  (assoc m :within-group (sqlh/collify args)))

(defhelper create-extension [m extension-name]
  (assoc m :create-extension (sqlh/collify extension-name)))

(defhelper drop-extension [m extension-name]
  (assoc m :drop-extension (sqlh/collify extension-name)))
