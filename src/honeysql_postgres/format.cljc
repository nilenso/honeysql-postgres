(ns ^{:doc "Extension of the honeysql format functions specifically for postgreSQL"}
    honeysql-postgres.format
  (:require [honeysql.format :as sqlf :refer [fn-handler format-clause]] ;; multi-methods
            [honeysql-postgres.util :as util]
            [clojure.string :as string]))

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
          :except 45
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
          :query-values 221}
         custom-additions))

(defn override-default-clause-priority
  "Override the default cluse priority set by honeysql"
  []
  (doseq [[k v] postgres-clause-priorities]
    (sqlf/register-clause! k v)))

(defmethod fn-handler "not" [_ value]
  (str "NOT " (sqlf/to-sql value)))

(defmethod fn-handler "primary-key" [_ & args]
  (str "PRIMARY KEY" (util/comma-join-args args)))

(defmethod fn-handler "unique" [_ & args]
  (str "UNIQUE" (util/comma-join-args args)))

(defmethod fn-handler "foreign-key" [_ & args]
  (str "FOREIGN KEY" (util/comma-join-args args)))

(defmethod fn-handler "references" [_ reftable refcolumn]
  (str "REFERENCES " (sqlf/to-sql reftable) (sqlf/paren-wrap (sqlf/to-sql refcolumn))))

(defmethod fn-handler "constraint" [_ name]
  (str "CONSTRAINT " (sqlf/to-sql name)))

(defmethod fn-handler "default" [_ value]
  (str "DEFAULT " (sqlf/to-sql value)))

(defmethod fn-handler "nextval" [_ value]
  (str "nextval('" (sqlf/to-sql value) "')"))

(defmethod fn-handler "check" [_ & args]
  (let [preds (sqlf/format-predicate* (util/prep-check args))
        pred-string (if (= 1 (count args))
                      (sqlf/paren-wrap preds)
                      preds)]
    (str "CHECK" pred-string)))

(defmethod fn-handler "ilike" [_ field value]
  (str (sqlf/to-sql field) " ILIKE "
       (sqlf/to-sql value)))

(defmethod fn-handler "not-ilike" [_ field value]
  (str (sqlf/to-sql field) " NOT ILIKE "
       (sqlf/to-sql value)))

;; format-clause multimethods used to format various sql clauses as defined

(defmethod format-clause :on-conflict-constraint [[_ k] _]
  (str "ON CONFLICT ON CONSTRAINT " (-> k
                                       util/get-first
                                       sqlf/to-sql)))

(defmethod format-clause :on-conflict [[_ ids] _]
  (str "ON CONFLICT " (util/comma-join-args ids)))

(defmethod format-clause :do-nothing [_ _]
  "DO NOTHING")

(defmethod format-clause :do-update-set! [[_ values] _]
  (str "DO UPDATE SET " (sqlf/comma-join (for [[k v] values]
                                           (str (sqlf/to-sql k) " = " (sqlf/to-sql v))))))

(defmethod format-clause :do-update-set [[_ values] _]
  (let [fields (or (:fields values) values)
        where  (:where values)]
    (str "DO UPDATE SET "
         (sqlf/comma-join (map #(str (sqlf/to-sql %) " = EXCLUDED." (sqlf/to-sql %)) fields))
         (when where
           (str " WHERE " (sqlf/format-predicate* where))))))

(defn- format-upsert-clause [upsert]
  (let [ks (keys upsert)]
    (map #(format-clause % (find upsert %)) upsert)))

(defmethod format-clause :upsert [[_ upsert] _]
  (sqlf/space-join (format-upsert-clause upsert)))

(defmethod format-clause :returning [[_ fields] _]
  (str "RETURNING " (sqlf/comma-join (map sqlf/to-sql fields))))

(defmethod format-clause :create-view [[_ viewname] _]
  (str "CREATE VIEW " (-> viewname
                          util/get-first
                          sqlf/to-sql) " AS"))

(defmethod format-clause :create-table [[_ [tablename if-not-exists]] _]
  (str "CREATE TABLE "
       (when if-not-exists "IF NOT EXISTS ")
       (-> tablename
           util/get-first
           sqlf/to-sql)))

(defmethod format-clause :with-columns [[_ columns] _]
  (sqlf/paren-wrap (->> columns
                        util/get-first
                        (map #(sqlf/space-join (map sqlf/to-sql %)))
                        sqlf/comma-join)))

(defmethod format-clause :drop-table [[_ params] _]
  (let [[if-exists & others] params
        tables (if-not (= :if-exists if-exists)
                 params
                 others)]
    (str "DROP TABLE "
         (when (= :if-exists if-exists) "IF EXISTS ")
         (->> tables
              (map sqlf/to-sql)
              sqlf/comma-join))))

(defn- format-over-clause [exp]
  (str
   (-> exp first sqlf/to-sql)
   " OVER "
   (-> exp second sqlf/to-sql)
   (when-let [alias (-> exp rest second)]
     (str " AS " (sqlf/to-sql alias)))))

(defmethod format-clause :over [[_ fields] complete-sql-map]
  (str
   ;; if the select clause has any columns in it then add a comma before the
   ;; window functions
   (if (seq (:select complete-sql-map)) ", ")
   (->> fields
        (map format-over-clause)
        sqlf/comma-join)))

(defmethod format-clause :window [[_ [window-name fields]] _]
  (str "WINDOW " (sqlf/to-sql window-name) " AS " (sqlf/to-sql fields)))

(defmethod format-clause :partition-by [[_ fields] _]
  (str "PARTITION BY " (->> fields
                            (map sqlf/to-sql)
                            sqlf/comma-join)))

(defmethod format-clause :alter-table [[_ tablename] _]
  (str "ALTER TABLE " (-> tablename
                          util/get-first
                          sqlf/to-sql)))

(defmethod format-clause :add-column [[_ fields] _]
  (str "ADD COLUMN " (->> fields
                          (map sqlf/to-sql)
                          sqlf/space-join)))

(defmethod format-clause :drop-column [[_ fields] _]
  (str "DROP COLUMN " (->> fields
                           util/get-first
                           sqlf/to-sql)))

(defmethod format-clause :rename-column [[_ [oldname newname]] _]
  (str "RENAME COLUMN " (sqlf/to-sql oldname) " TO " (sqlf/to-sql newname)))

(defmethod format-clause :rename-table [[_ newname] _]
  (str "RENAME TO " (-> newname
                        util/get-first
                        sqlf/to-sql)))

(defmethod format-clause :insert-into-as [[_ [table-name table-alias]] _]
  (str  "INSERT INTO " (sqlf/to-sql table-name) " AS " (sqlf/to-sql table-alias)))

(defmethod format-clause :except [[_ maps] _]
  (binding [sqlf/*subquery?* false]
    (string/join " EXCEPT " (map sqlf/to-sql maps))))

(override-default-clause-priority)
