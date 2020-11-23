(ns honeysql-postgres.postgres-test
  (:refer-clojure :exclude [update partition-by])
  (:require [honeysql-postgres.helpers :as sqlph :refer [upsert on-conflict do-nothing on-conflict-constraint
                                                         returning do-update-set do-update-set!
                                                         alter-table rename-column drop-column
                                                         add-column partition-by insert-into-as
                                                         create-table rename-table drop-table
                                                         window create-view over with-columns]]
            [honeysql.helpers :as sqlh :refer [insert-into values where select columns
                                               from order-by update sset query-values
                                               modifiers]]
            [honeysql.core :as sql]
            [clojure.test :as test :refer [deftest is testing]]))

(deftest upsert-test
  (testing "upsert sql generation for postgresql"
    (is (= ["INSERT INTO distributors (did, dname) VALUES (?, ?), (?, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING *" 5 "Gizmo Transglobal" 6 "Associated Computing, Inc"]
           (-> (insert-into :distributors)
               (values [{:did 5 :dname "Gizmo Transglobal"}
                        {:did 6 :dname "Associated Computing, Inc"}])
               (upsert (-> (on-conflict :did)
                           (do-update-set :dname)))
               (returning :*)
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (?, ?) ON CONFLICT (did) DO NOTHING" 7 "Redline GmbH"]
           (-> (insert-into :distributors)
               (values [{:did 7 :dname "Redline GmbH"}])
               (upsert (-> (on-conflict :did)
                           do-nothing))
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (?, ?) ON CONFLICT ON CONSTRAINT distributors_pkey DO NOTHING" 9 "Antwerp Design"]
           (-> (insert-into :distributors)
               (values [{:did 9 :dname "Antwerp Design"}])
               (upsert (-> (on-conflict-constraint :distributors_pkey)
                           do-nothing))
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (?, ?), (?, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname" 10 "Pinp Design" 11 "Foo Bar Works"]
           (sql/format {:insert-into :distributors
                        :values [{:did 10 :dname "Pinp Design"}
                                 {:did 11 :dname "Foo Bar Works"}]
                        :upsert {:on-conflict [:did]
                                 :do-update-set [:dname]}})))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (?, ?) ON CONFLICT (did) DO UPDATE SET dname = ?" 23 "Foo Distributors" "EXCLUDED.dname || ' (formerly ' || d.dname || ')'"]
           (-> (insert-into :distributors)
               (values [{:did 23 :dname "Foo Distributors"}])
               (on-conflict :did)
               (do-update-set! [:dname "EXCLUDED.dname || ' (formerly ' || d.dname || ')'"])
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) (SELECT ?, ?) ON CONFLICT ON CONSTRAINT distributors_pkey DO NOTHING" 1 "whatever"]
           (-> (insert-into :distributors)
               (columns :did :dname)
               (query-values (select 1 "whatever"))
               (upsert (-> (on-conflict-constraint :distributors_pkey)
                           do-nothing))
               sql/format)))))

(deftest upsert-where-test
  (is (= ["INSERT INTO user (phone, name) VALUES (?, ?) ON CONFLICT (phone) WHERE phone IS NOT NULL DO UPDATE SET phone = EXCLUDED.phone, name = EXCLUDED.name WHERE user.active = FALSE" "5555555" "John"]
         (sql/format
          {:insert-into :user
           :values      [{:phone "5555555" :name "John"}]
           :upsert      {:on-conflict   [:phone]
                         :where         [:<> :phone nil]
                         :do-update-set {:fields [:phone :name]
                                         :where  [:= :user.active false]}}}))))

(deftest returning-test
  (testing "returning clause in sql generation for postgresql"
    (is (= ["DELETE FROM distributors WHERE did > 10 RETURNING *"]
           (sql/format {:delete-from :distributors
                        :where [:> :did :10]
                        :returning [:*]})))
    (is (= ["UPDATE distributors SET dname = ? WHERE did = 2 RETURNING did dname" "Foo Bar Designs"]
           (-> (update :distributors)
               (sset {:dname "Foo Bar Designs"})
               (where [:= :did :2])
               (returning [:did :dname])
               sql/format)))))

(deftest create-view-test
  (testing "creating a view from a table"
    (is (= ["CREATE VIEW metro AS SELECT * FROM cities WHERE metroflag = ?" "Y"]
           (-> (create-view :metro)
               (select :*)
               (from :cities)
               (where [:= :metroflag "Y"])
               sql/format)))))

(deftest drop-table-test
  (testing "drop table sql generation for a single table"
    (is (= ["DROP TABLE cities"]
           (sql/format (drop-table :cities)))))
  (testing "drop table sql generation for multiple tables"
    (is (= ["DROP TABLE cities, towns, vilages"]
           (sql/format (drop-table :cities :towns :vilages))))))

(deftest create-table-test
  (testing "create table with two columns"
    (is (= ["CREATE TABLE cities (city varchar(?) PRIMARY KEY, location point)" 80]
           (-> (create-table :cities)
               (with-columns [[:city (sql/call :varchar 80) (sql/call :primary-key)]
                              [:location :point]])
               sql/format))))
  (testing "create table with foreign key reference"
    (is (= ["CREATE TABLE weather (city varchar(80) REFERENCES cities(city), temp_lo int, temp_hi int, prcp real, date date)"]
           (-> (create-table :weather)
               (with-columns [[:city (sql/call :varchar :80) (sql/call :references :cities :city)]
                              [:temp_lo :int]
                              [:temp_hi :int]
                              [:prcp :real]
                              [:date :date]])
               sql/format))))
  (testing "creating table with table level constraint"
    (is (= ["CREATE TABLE films (code char(?), title varchar(?), did integer, date_prod date, kind varchar(?), CONSTRAINT code_title PRIMARY KEY(code, title))" 5 40 10]
           (-> (create-table :films)
               (with-columns [[:code (sql/call :char 5)]
                              [:title (sql/call :varchar 40)]
                              [:did :integer]
                              [:date_prod :date]
                              [:kind (sql/call :varchar 10)]
                              [(sql/call :constraint :code_title) (sql/call :primary-key :code :title)]])
               sql/format))))
  (testing "creating table with column level constraint"
    (is (= ["CREATE TABLE films (code char(?) CONSTRAINT firstkey PRIMARY KEY, title varchar(?) NOT NULL, did integer NOT NULL, date_prod date, kind varchar(?))" 5 40 10]
           (-> (create-table :films)
               (with-columns [[:code (sql/call :char 5) (sql/call :constraint :firstkey) (sql/call :primary-key)]
                              [:title (sql/call :varchar 40) (sql/call :not nil)]
                              [:did :integer (sql/call :not nil)]
                              [:date_prod :date]
                              [:kind (sql/call :varchar 10)]])
               sql/format))))
  (testing "creating table with columns with default values"
    (is (= ["CREATE TABLE distributors (did integer PRIMARY KEY DEFAULT nextval('serial'), name varchar(?) NOT NULL)" 40]
           (-> (create-table :distributors)
               (with-columns [[:did :integer (sql/call :primary-key) (sql/call :default (sql/call :nextval :serial))]
                              [:name (sql/call :varchar 40) (sql/call :not nil)]])
               sql/format))))
  (testing "creating table with column checks"
    (is (= ["CREATE TABLE products (product_no integer, name text, price numeric CHECK(price > ?), discounted_price numeric, CHECK(discounted_price > ? AND price > discounted_price))" 0 0]
           (-> (create-table :products)
               (with-columns [[:product_no :integer]
                              [:name :text]
                              [:price :numeric (sql/call :check [:> :price 0])]
                              [:discounted_price :numeric]
                              [(sql/call :check [:> :discounted_price 0] [:> :price :discounted_price])]])
               sql/format)))))

(deftest over-test
  (testing "window function over on select statemt"
    (is (= ["SELECT id , avg(salary) OVER (PARTITION BY department ORDER BY designation) AS Average, max(salary) OVER w AS MaxSalary FROM employee WINDOW w AS (PARTITION BY department)"]
           (-> (select :id)
               (over
                [(sql/call :avg :salary) (-> (partition-by :department) (order-by [:designation])) :Average]
                [(sql/call :max :salary) :w :MaxSalary])
               (from :employee)
               (window :w (partition-by :department))
               sql/format)))))

(deftest alter-table-test
  (testing "alter table add column generates the required sql"
    (is (= ["ALTER TABLE employees ADD COLUMN address text"]
           (-> (alter-table :employees)
               (add-column :address :text)
               sql/format))))
  (testing "alter table drop column generates the required sql"
    (is (= ["ALTER TABLE employees DROP COLUMN address"]
           (-> (alter-table :employees)
               (drop-column :address)
               sql/format))))
  (testing "alter table rename column generates the requred sql"
    (is (= ["ALTER TABLE employees RENAME COLUMN address TO homeaddress"]
           (-> (alter-table :employees)
               (rename-column :address :homeaddress)
               sql/format))))
  (testing "alter table rename table generates the required sql"
    (is (= ["ALTER TABLE employees RENAME TO managers"]
           (-> (alter-table :employees)
               (rename-table :managers)
               sql/format)))))

(deftest insert-into-with-alias
  (testing "insert into with alias"
    (is (= ["INSERT INTO distributors AS d (did, dname) VALUES (?, ?), (?, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname WHERE d.zipcode <> ? RETURNING d.*" 5 "Gizmo Transglobal" 6 "Associated Computing, Inc" "21201"]
           (-> (insert-into-as :distributors :d)
               (values [{:did 5 :dname "Gizmo Transglobal"}
                        {:did 6 :dname "Associated Computing, Inc"}])
               (upsert (-> (on-conflict :did)
                           (do-update-set :dname)
                           (where [:<> :d.zipcode "21201"])))
               (returning :d.*)
               sql/format)))))

(deftest create-table-if-not-exists
  (testing "create a table if not exists"
    (is (= ["CREATE TABLE IF NOT EXISTS tablename"]
           (-> (create-table :tablename :if-not-exists)
               sql/format)))))

(deftest drop-table-if-exists
  (testing "drop a table if it exists"
    (is (= ["DROP TABLE IF EXISTS t1, t2, t3"]
           (-> (drop-table :if-exists :t1 :t2 :t3)
               sql/format)))))

(deftest select-where-ilike
  (testing "select from table with ILIKE operator"
    (is (= ["SELECT * FROM products WHERE name ILIKE ?" "%name%"]
           (-> (select :*)
               (from :products)
               (where [:ilike :name "%name%"])
               sql/format)))))

(deftest select-where-not-ilike
  (testing "select from table with NOT ILIKE operator"
    (is (= ["SELECT * FROM products WHERE name NOT ILIKE ?" "%name%"]
           (-> (select :*)
               (from :products)
               (where [:not-ilike :name "%name%"])
               sql/format)))))

(deftest values-except-select
  (testing "select which values are not not present in a table"
    (is (= ["VALUES (?), (?), (?) EXCEPT SELECT id FROM images" 4 5 6]
           (sql/format
            {:except
             [{:values [[4] [5] [6]]}
              {:select [:id] :from [:images]}]})))))

(deftest select-except-select
  (testing "select which rows are not present in another table"
    (is (= ["SELECT ip EXCEPT SELECT ip FROM ip_location"]
           (sql/format
            {:except
             [{:select [:ip]}
              {:select [:ip] :from [:ip_location]}]})))))

(deftest values-except-all-select
  (testing "select which values are not not present in a table"
    (is (= ["VALUES (?), (?), (?) EXCEPT ALL SELECT id FROM images" 4 5 6]
           (sql/format
            {:except-all
             [{:values [[4] [5] [6]]}
              {:select [:id] :from [:images]}]})))))

(deftest select-except-all-select
  (testing "select which rows are not present in another table"
    (is (= ["SELECT ip EXCEPT ALL SELECT ip FROM ip_location"]
           (sql/format
            {:except-all
             [{:select [:ip]}
              {:select [:ip] :from [:ip_location]}]})))))

(deftest select-distinct-on
  (testing "select distinct on"
    (is (= ["SELECT DISTINCT ON(\"a\", \"b\") \"c\" FROM \"products\" "]
           (-> (select :c)
               (from :products)
               (modifiers :distinct-on :a :b)
               (sql/format :quoting :ansi))))))
