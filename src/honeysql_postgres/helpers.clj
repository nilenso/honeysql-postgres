(ns honeysql-postgres.helpers
  (:refer-clojure :exclude [update])
  (:require [honeysql.helpers :refer :all]))

;; Extension of the honeysql helper funcitons for postgreSQL

;; Eg - (do-nothing {})
(defn do-nothing [m]
  "Accepts a map and append {:do-nothing []} to it"
  (assoc m :do-nothing []))

;; assoc into m {:do-update-set [args]}
;; Eg - (do-update-set :id)
(defhelper do-update-set [m args]
  (assoc m :do-update-set (collify args)))

;; assoc into m {:do-update-set! args}
;; Eg - (on-update-set! "id = EXCLUDED.id")
(defhelper db-update-set! [m args]
  (assoc m :do-update-set! args))

;; assoc into m {:on-conflict args}
;; Eg - (on-conflict :id)
(defhelper on-conflict [m args]
  (assoc m :on-conflict args))

;; assoc into m {:on-conflict-constraint args}
;; Eg - (on-conflict-constraint :constraint)
(defhelper on-conflict-constraint [m args]
  (assoc m :on-conflict-constraint args))

;; assoc into m {:upsert args}
;; Upsert can have various maps as input such as
;; on-conflict, on-conflict-constraint, do-update-set, do-nothing
(defhelper upsert [m args]
  (if (plain-map? args)
    (assoc m :upsert args)
    (assoc m :upsert (first args))))

;; assoc into m {:returning [fields]}
;; the args can be one or more field values
(defhelper returning [m fields]
  (assoc m :returning (collify fields)))

;; assoc into m {:create-view viewname}
;; Eg - (create-view :viewname)
(defhelper create-view [m viewname]
  (assoc m :create-view (collify viewname)))

;; assoc into m {:create-table tablename}
;; Eg: (create-table :table1)
(defhelper create-table [m tablename]
  (assoc m :create-table (collify tablename)))

;; assoc into m {:with-columns args}
;; the args are supposed to be a vector of vectors with each vector holding
;; the colum definition/properties
(defhelper with-columns [m args]
  (assoc m :with-columns args))

;; assoc into m {:drop-table tablenames}
;; Can be supplied wtih 1 or more args - Eg : (drop-table :table1 :table2)
(defhelper drop-table [m tablenames]
  (assoc m :drop-table (collify tablenames)))

;;
(defhelper over [m args]
  (assoc m :over args))

;;
(defhelper partition-by [m fields]
  (assoc m :partition-by (collify fields)))
