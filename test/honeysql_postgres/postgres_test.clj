(ns honeysql-postgres.postgres-test
  (:refer-clojure :exclude [update partition-by])
  (:require [honeysql-postgres.format :refer :all]
            [honeysql-postgres.helpers :refer :all]
            [honeysql.helpers :refer :all]
            [honeysql.core :as sql]
            [clojure.test :refer :all]))

(deftest upsert-test
  (testing "upsert sql generation for postgresql"
    (is (= ["INSERT INTO distributors d (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING d.*" "Gizmo Transglobal" "Associated Computing, Inc"]
           (-> (insert-into [:distributors :d])
               (values [{:did 5 :dname "Gizmo Transglobal"}
                        {:did 6 :dname "Associated Computing, Inc"}])
               (upsert (-> (on-conflict :did)
                           (do-update-set :dname)))
               (returning :d.*)
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (7, ?) ON CONFLICT (did) DO NOTHING" "Redline GmbH"]
           (-> (insert-into :distributors)
               (values [{:did 7 :dname "Redline GmbH"}])
               (upsert (-> (on-conflict :did)
                           do-nothing))
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (9, ?) ON CONFLICT ON CONSTRAINT distributors_pkey DO NOTHING" "Antwerp Design"]
           (-> (insert-into :distributors)
               (values [{:did 9 :dname "Antwerp Design"}])
               (upsert (-> (on-conflict-constraint :distributors_pkey)
                           do-nothing))
               sql/format)))
    (is (= ["INSERT INTO distributors (did, dname) VALUES (10, ?), (11, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname" "Pinp Design" "Foo Bar Works"]
           (sql/format {:insert-into :distributors
                        :values [{:did 10 :dname "Pinp Design"}
                                 {:did 11 :dname "Foo Bar Works"}]
                        :upsert {:on-conflict [:did]
                                 :do-update-set [:dname]}})))))

(deftest returning-test
  (testing "returning clause in sql generation for postgresql"
    (is (= ["DELETE FROM distributors WHERE did > 10 RETURNING *"]
           (sql/format {:delete-from :distributors
                        :where [:> :did :10]
                        :returning [:*] })))
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
    (is (= ["CREATE TABLE cities (city varchar(80) PRIMARY KEY, location point)"]
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
    (is (= ["CREATE TABLE films (code char(5), title varchar(40), did integer, date_prod date, kind varchar(10), CONSTRAINT code_title PRIMARY KEY(code, title))"]
           (-> (create-table :films)
               (with-columns [[:code (sql/call :char 5)]
                              [:title (sql/call :varchar 40)]
                              [:did :integer]
                              [:date_prod :date]
                              [:kind (sql/call :varchar 10)]
                              [(sql/call :constraint :code_title) (sql/call :primary-key :code :title)]])
               sql/format))))
  (testing "creating table with column level constraint"
    (is (= ["CREATE TABLE films (code char(5) CONSTRAINT firstkey PRIMARY KEY, title varchar(40) NOT NULL, did integer NOT NULL, date_prod date, kind varchar(10))"]
           (-> (create-table :films)
               (with-columns [[:code (sql/call :char 5) (sql/call :constraint :firstkey) (sql/call :primary-key)]
                              [:title (sql/call :varchar 40) (sql/call :not nil)]
                              [:did :integer (sql/call :not nil)]
                              [:date_prod :date]
                              [:kind (sql/call :varchar 10)]])
               sql/format))))
  (testing "creating table with columns with default values"
    (is (= ["CREATE TABLE distributors (did integer PRIMARY KEY DEFAULT nextval('serial'), name varchar(40) NOT NULL)"]
           (-> (create-table :distributors)
               (with-columns [[:did :integer (sql/call :primary-key) (sql/call :default (sql/call :nextval :serial))]
                              [:name (sql/call :varchar 40) (sql/call :not nil)]])
               sql/format))))
  (testing "creating table with column checks"
    (is (= ["CREATE TABLE products (product_no integer, name text, price numeric CHECK(price > 0), discounted_price numeric, CHECK(discounted_price > 0 AND price > discounted_price))"]
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
