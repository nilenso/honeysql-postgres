(ns honeysql-postgres.format
  (:refer-clojure :exclude [format])
  (:require [honeysql.format :refer :all]))

;; Extension of the honeysql format functions specifically for postgreSQL

(def postgres-clause-priorities
  "Determines the order that clauses will be placed within generated SQL"
  {:with 30
   :with-recursive 40
   :create-view 45
   :select 50
   :insert-into 60
   :update 70
   :delete-from 80
   :columns 90
   :set 100
   :from 110
   :join 120
   :left-join 130
   :right-join 140
   :full-join 150
   :where 160
   :group-by 170
   :having 180
   :order-by 190
   :limit 200
   :offset 210
   :lock 215
   :values 220
   :upsert 225
   :on-conflict 230
   :on-conflict-constraint 230
   :do-update-set 235
   :do-update-set! 235
   :do-nothing 235
   :returning 240
   :query-values 250})

;; Not sure if this is the best way to implement this, but since the `clause-store` is being used
;; by format to decide the order of clauses, not really sure what would be a better implementation.
;; Override the default cluse priority set by honeysql
(doseq [[k v] postgres-clause-priorities]
  (register-clause! k v))

(defn- get-first
  "Returns the first element if the passed argument is a collection, else return the passed argument
   as is."
  [x]
  (if (coll? x)
    (first x)
    x))

(defn- comma-join-args
  "Returns the args comma-joined after applying to-sql to them"
  [args]
  (if (nil? args)
    ""
    (->> args
         (map to-sql)
         comma-join
         paren-wrap)))

;; TODO : Add create-table, drop-table and its clauses to the priority clause map

;; fn-handler multimethods are called while using `types/call` like -> (types/call :name & args)
;; these are mostly use with sql function calls.

;; takes :vale as an argument and -> "NOT value"
;; eg - (types/call :not nil) -> "NOT NULL"
(defmethod fn-handler "not" [_ value]
  (str "NOT " (to-sql value)))

;; takes :size as an argument and -> "char(size)"
(defmethod fn-handler "char" [_ size]
  (str "char(" (to-sql size) ")"))

;; takes :size as an argument and -> "varchar(size)"
(defmethod fn-handler "varchar" [_ size]
  (str "varchar(" (to-sql size) ")"))

;; takes 0 or more args as an argument and ->
;; -> "PRIMARY KEY" if no args supplied
;; -> "PRIMARY KEY (arg1, arg2, ... )" if args
(defmethod fn-handler "primary-key" [_ & args]
  (str "PRIMARY KEY" (comma-join-args args)))

;; takes 0 or more args as an argument and ->
;; -> "UNIQUE" if no args supplied
;; -> "UNIQUE (arg1, arg2, ... )" if args
(defmethod fn-handler "unique" [_ & args]
  (str "UNIQUE" (comma-join-args args)))

;; takes 0 or more args as an argument and ->
;; -> "FOREIGN KEY" if no args supplied
;; -> "FOREIGN KEY (arg1, arg2, ... )" if args
(defmethod fn-handler "foreign-key" [_ & args]
  (str "FOREIGN KEY" (comma-join-args args)))

;; takes reftable and refcolumn as argument and -> "REFERENCES reftable(refcolumn)"
(defmethod fn-handler "references" [_ reftable refcolumn]
  (str "REFERENCES " (to-sql reftable) (paren-wrap (to-sql refcolumn))))

;; takes name as an argument and -> "CONSTRAINT name"
(defmethod fn-handler "constraint" [_ name]
  (str "CONSTRAINT " (to-sql name)))

;; format-clause multimethods used to format various sql clauses as defined

;; takes :constraint as argument -> "ON CONFLICT ON CONSTRAINT constraint"
(defmethod format-clause :on-conflict-constraint [[_ k] _]
  (str "ON CONFLICT ON CONSTRAINT " (-> k
                                        get-first
                                        to-sql)))

;; takes id1 id2 .. as argument and -> "ON CONFLICT (id1, id2, ...)"
(defmethod format-clause :on-conflict [[_ ids] _]
  (str "ON CONFLICT " (comma-join-args ids)))

;; returns "DO NOTHING"
(defmethod format-clause :do-nothing [_ _]
  "DO NOTHING")

;; Used when there is a need to update the columns with modified values if the
;; row(id) already exits - accepts a map of column and value
(defmethod format-clause :do-update-set! [[_ values] _]
  (str "DO UPDATE SET " (comma-join (for [[k v] values]
                                      (str (to-sql k) " = " (to-sql v))))))

;; Used when it is a simple upsert - accepts a vector of columns
(defmethod format-clause :do-update-set [[_ values] _]
  (str "DO UPDATE SET "
       (comma-join (map #(str (to-sql %) " = EXCLUDED." (to-sql %))
                        values))))

;; format the map passed to upsert
(defn format-upsert-clause [upsert]
  (let [ks (keys upsert)]
    (map #(format-clause % (find upsert %)) upsert)))

;; Accepts a map with the following possible keys
;; :on-conflict, :do-update-set or :do-update-set! or :do-nothing
(defmethod format-clause :upsert [[_ upsert] _]
  (space-join (format-upsert-clause upsert)))

;; takes fields as argument and -> "RETURNING field1, field2 , ..."
(defmethod format-clause :returning [[_ fields] _]
  (str "RETURNING " (comma-join (map to-sql fields))))

;; takes viewname as argument and -> "CREATE VIEW viewname AS"
(defmethod format-clause :create-view [[_ viewname] _]
  (str "CREATE VIEW " (-> viewname
                          get-first
                          to-sql) " AS"))

;; takes tablename as an argument and -> "CREATE TABLE tablename"
(defmethod format-clause :create-table [[_ tablename] _]
  (str "CREATE TABLE " (-> tablename
                           get-first
                           to-sql)))

;; :with-columns is used to format the columns and the column properties
;; this multimethod takes in a vector of vectors (column properties)
(defmethod format-clause :with-columns [[_ columns] _]
  (paren-wrap (->> columns
                   get-first
                   (map #(space-join (map to-sql %)))
                   comma-join)))

;; takes tables as argument and -> "DROP TABLE table1, table2, ..."
(defmethod format-clause :drop-table [[_ tables] _]
  (str "DROP TABLE " (->> tables
                          (map to-sql)
                          comma-join)))
