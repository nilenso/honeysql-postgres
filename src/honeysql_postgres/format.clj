
(ns ^{:doc "Extension of the honeysql format functions specifically for postgreSQL"}
    honeysql-postgres.format
  (:refer-clojure :exclude [format partition-by])
  (:require [honeysql.format :refer :all]
            [honeysql-postgres.util :refer [get-first comma-join-args prep-check]]))

(def ^:private custom-additions
  {:create-table 10
   :drop-table 10
   :alter-table 20
   :add-column 30
   :drop-column 40
   :create-view 40
   :insert-into-as 60
   :over 55
   :partition-by 165
   :window 195
   :upsert 225
   :on-conflict 230
   :on-conflict-constraint 230
   :do-update-set 235
   :do-update-set! 235
   :do-nothing 235
   :returning 240})

(def ^:private postgres-clause-priorities
  "Determines the order that clauses will be placed within generated SQL"
  (merge {:with 30
          :with-recursive 40
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
          :query-values 250}
         custom-additions))

(defn override-default-clause-priority
  "Override the default cluse priority set by honeysql"
  []
  (doseq [[k v] postgres-clause-priorities]
    (register-clause! k v)))

(defmethod fn-handler "not" [_ value]
  (str "NOT " (to-sql value)))

(defmethod fn-handler "primary-key" [_ & args]
  (str "PRIMARY KEY" (comma-join-args args)))

(defmethod fn-handler "unique" [_ & args]
  (str "UNIQUE" (comma-join-args args)))

(defmethod fn-handler "foreign-key" [_ & args]
  (str "FOREIGN KEY" (comma-join-args args)))

(defmethod fn-handler "references" [_ reftable refcolumn]
  (str "REFERENCES " (to-sql reftable) (paren-wrap (to-sql refcolumn))))

(defmethod fn-handler "constraint" [_ name]
  (str "CONSTRAINT " (to-sql name)))

(defmethod fn-handler "default" [_ value]
  (str "DEFAULT " (to-sql value)))

(defmethod fn-handler "primary-key" [_ & args]
  (str "PRIMARY KEY" (util/comma-join-args args)))

(defmethod fn-handler "nextval" [_ value]
  (str "nextval('" (to-sql value) "')"))

(defmethod fn-handler "check" [_ & args]
  (let [preds (format-predicate* (prep-check args))
        pred-string (if (= 1 (count args))
                      (paren-wrap preds)
                      preds)]
    (str "CHECK" pred-string)))

;; format-clause multimethods used to format various sql clauses as defined

(defmethod format-clause :on-conflict-constraint [[_ k] _]
  (str "ON CONFLICT ON CONSTRAINT " (-> k
                                        get-first
                                        to-sql)))

(defmethod format-clause :on-conflict [[_ ids] _]
  (str "ON CONFLICT " (comma-join-args ids)))

(defmethod format-clause :do-nothing [_ _]
  "DO NOTHING")

(defmethod format-clause :do-update-set! [[_ values] _]
  (str "DO UPDATE SET " (comma-join (for [[k v] values]
                                      (str (to-sql k) " = " (to-sql v))))))

(defmethod format-clause :do-update-set [[_ values] _]
  (let [fields (or (:fields values) values)
        where  (:where values)]
    (str "DO UPDATE SET "
      (comma-join (map #(str (to-sql %) " = EXCLUDED." (to-sql %)) fields))
      (when where
        (str " WHERE " (format-predicate* where))))))

(defn- format-upsert-clause [upsert]
  (let [ks (keys upsert)]
    (map #(format-clause % (find upsert %)) upsert)))

(defmethod format-clause :upsert [[_ upsert] _]
  (space-join (format-upsert-clause upsert)))

(defmethod format-clause :returning [[_ fields] _]
  (str "RETURNING " (comma-join (map to-sql fields))))

(defmethod format-clause :create-view [[_ viewname] _]
  (str "CREATE VIEW " (-> viewname
                          get-first
                          to-sql) " AS"))

(defmethod format-clause :create-table [[_ tablename] _]
  (str "CREATE TABLE " (-> tablename
                           get-first
                           to-sql)))

(defmethod format-clause :with-columns [[_ columns] _]
  (paren-wrap (->> columns
                   get-first
                   (map #(space-join (map to-sql %)))
                   comma-join)))

(defmethod format-clause :drop-table [[_ tables] _]
  (str "DROP TABLE " (->> tables
                          (map to-sql)
                          comma-join)))

(defn- format-over-clause [exp]
  (str
   (-> exp first to-sql)
   " OVER "
   (-> exp second to-sql)
   (when-let [alias (-> exp rest second)]
     (str " AS " (to-sql alias)))))

(defmethod format-clause :over [[_ fields] complete-sql-map]
  (str
   ;; if the select clause has any columns in it then add a comma before the
   ;; window functions
   (if (seq (:select complete-sql-map)) ", ")
   (->> fields
        (map format-over-clause)
        comma-join)))

(defmethod format-clause :window [[_ [window-name fields]] _]
  (str "WINDOW " (to-sql window-name) " AS " (to-sql fields)))

(defmethod format-clause :partition-by [[_ fields] _]
  (str "PARTITION BY " (->> fields
                            (map to-sql)
                            comma-join)))

(defmethod format-clause :alter-table [[_ tablename] _]
  (str "ALTER TABLE " (-> tablename
                          get-first
                          to-sql)))

(defmethod format-clause :add-column [[_ fields] _]
  (str "ADD COLUMN " (->> fields
                         (map to-sql)
                         space-join)))

(defmethod format-clause :drop-column [[_ fields] _]
  (str "DROP COLUMN " (->> fields
                           get-first
                           to-sql)))

(defmethod format-clause :rename-column [[_ [oldname newname]] _]
  (str "RENAME COLUMN " (to-sql oldname) " TO " (to-sql newname)))

(defmethod format-clause :rename-table [[_ newname] _]
  (str "RENAME TO " (-> newname
                        get-first
                        to-sql)))

(defmethod format-clause :insert-into-as [[_ [table-name table-alias]] _]
  (str  "INSERT INTO " (to-sql table-name) " AS " (to-sql table-alias)))

(override-default-clause-priority)
