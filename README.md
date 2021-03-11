# honeysql-postgres [![Actions Status](https://github.com/nilenso/honeysql-postgres/workflows/CI/badge.svg)](https://github.com/nilenso/honeysql-postgres/actions) [![Clojars Project](https://img.shields.io/clojars/v/nilenso/honeysql-postgres.svg)](https://clojars.org/nilenso/honeysql-postgres) [![cljdoc badge](https://cljdoc.org/badge/nilenso/honeysql-postgres)](https://cljdoc.org/d/nilenso/honeysql-postgres/CURRENT)

PostgreSQL extensions for the widely used [honeysql](https://github.com/jkk/honeysql).

This library aims to extend the features of honeysql to support postgres specific SQL clauses and some basic SQL DDL in addition to the ones supported by the parent library. This keeps honeysql clean and single-purpose, any vendor-specific additions can simply be separate libraries that work on top.

## Breaking Change
Implementation of `over` has been changed (from 0.2.2) to accept alias as an option and define the aggregator-function within the over clause and not in the select clause, this allows the inclusion of multiple window-function which was not possible in the previous implementation.

The query creation and usage is exactly the same as honeysql.

## Index

- [Usage](#usage)
  - [REPL](#REPL)
  - [distinct on](#distinct-on)
  - [upsert](#upsert)
  - [insert into with alias](#insert-into-with-alias)
  - [over](#over)
  - [create view](#create-view)
  - [create table](#create-table)
  - [drop table](#drop-table)
  - [alter table](#alter-table)
  - [pattern matching](#pattern-matching)
  - [except](#except)
  - [filter](#filter)
  - [within group](#within-group)
  - [SQL functions](#sql-functions)
- [License](#license)

## Usage

### REPL

```clojure
(require '[honeysql.core :as sql]
         '[honeysql.helpers :refer :all :as sqlh]
         '[honeysql-postgres.helpers :as psqlh])
```

### distinct-on

`select` can be written with a `distinct on` clause
``` clojure
(-> (select :column-1 :column-2 :column-3)
    (from :table-name)
    (modifiers :distinct-on :column-1 :column-2)
    (sql/format))
=> ["SELECT DISTINCT ON(column_1, column_2) column_1, column_2, column_3 FROM table_name"]
```

### upsert

`upsert` can be written either way. You can make use of `do-update-set!` over `do-update-set`, if you want to modify the some column values in case of conflicts.
```clojure
(-> (insert-into :distributors)
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (psqlh/upsert (-> (psqlh/on-conflict :did)
                      (psqlh/do-update-set :dname)))
    (psqlh/returning :*)
    sql/format)
=> ["INSERT INTO distributors (did, dname) VALUES (?, ?), (?, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING *"
    5 "Gizmo Transglobal" 6 "Associated Computing, Inc"]

(-> (insert-into :distributors)
    (values [{:did 23 :dname "Foo Distributors"}])
    (psqlh/on-conflict :did)
    (psqlh/do-update-set! [:dname "EXCLUDED.dname || ' (formerly ' || distributors.dname || ')'"] [:downer "EXCLUDED.downer"])
    sql/format)
=> ["INSERT INTO distributors (did, dname) VALUES (?, ?) ON CONFLICT (did) DO UPDATE SET dname = ?, downer = ?"
    23 "Foo Distributors" "EXCLUDED.dname || ' (formerly ' || distributors.dname || ')'" "EXCLUDED.downer"]
```

### insert into with alias

`insert-into-as` can be used to write insert statements with table name aliased.
```clojure
(-> (psqlh/insert-into-as :distributors :d)
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (psqlh/upsert (-> (psqlh/on-conflict :did)
                      (psqlh/do-update-set :dname)
                      (where [:<> :d.zipcode "21201"])))
    (psqlh/returning :d.*)
    sql/format)
=> ["INSERT INTO distributors AS d (did, dname) VALUES (?, ?), (?, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname WHERE d.zipcode <> ? RETURNING d.*"
    5 "Gizmo Transglobal" 6 "Associated Computing, Inc" "21201"]
```

### over

You can make use of `over` to write window functions where it takes in vectors with aggregator functions and window functions along with optional alias like `(over [aggregator-function window-function alias])`, the can be coupled with the `window` clause to write window-function functions with alias that is later defines the window-function, like `(-> (over [aggregator-function :w]) (window :w window-function))`.
```clojure
(-> (select :id)
    (psqlh/over
      [(sql/call :avg :salary) (-> (psqlh/partition-by :department) (order-by [:designation])) :Average]
      [(sql/call :max :salary) :w :MaxSalary])
    (from :employee)
    (psqlh/window :w (psqlh/partition-by :department))
    sql/format)
=> ["SELECT id , avg(salary) OVER (PARTITION BY department ORDER BY designation) AS Average, max(salary) OVER w AS MaxSalary FROM employee WINDOW w AS (PARTITION BY department)"]
```

### create view
`create-view` can be used to create views
```clojure
(-> (psqlh/create-view :metro)
    (select :*)
    (from :cities)
    (where [:= :metroflag "Y"])
    sql/format)
=> ["CREATE VIEW metro AS SELECT * FROM cities WHERE metroflag = ?" "Y"]
```

### create table

`create-table` and `with-columns` can be used to create tables along with the SQL functions, where `create-table` takes a table name as argument and `with-columns` takes a vector of vectors as argument, where the vectors describe the column properties as `[:column-name :datatype :constraints ... ]`.
```clojure
(-> (psqlh/create-table :films)
    (psqlh/with-columns [[:code (sql/call :char 5) (sql/call :constraint :firstkey) (sql/call :primary-key)]
                         [:title (sql/call :varchar 40) (sql/call :not nil)]
                         [:did :integer (sql/call :not nil)]
                         [:date_prod :date]
                         [:kind (sql/call :varchar 10)]])
    sql/format)
=> ["CREATE TABLE films (code char(?) CONSTRAINT firstkey PRIMARY KEY, title varchar(?) NOT NULL, did integer NOT NULL, date_prod date, kind varchar(?))"
    5 40 10]
```

### drop table

`drop-table` is used to drop tables
```clojure
(sql/format (psqlh/drop-table :cities :towns :vilages))
=> ["DROP TABLE cities, towns, vilages"]
```

### alter table

use `alter-table` along with `add-column` & `drop-column` to modify table level details
```clojure
(-> (psqlh/alter-table :employees)
    (psqlh/add-column :address :text)
    sql/format)
=> ["ALTER TABLE employees ADD COLUMN address text"]

(-> (psqlh/alter-table :employees)
    (psqlh/drop-column :address)
    sql/format)
=> ["ALTER TABLE employees DROP COLUMN address"]
```

### create-extension

`create-extension` can be used to create extensions with a given keyword.
```clojure
(-> (psqlh/create-extension :uuid-ossp :if-not-exists? true)
    (sql/format :allow-dashed-names? true
                :quoting :ansi))
=> ["CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\""]
```

### drop-extension
`drop-extension` is used to drop extensions.
```clojure
(-> (psqlh/drop-extension :uuid-ossp)
    (sql/format :allow-dashed-names? true
                :quoting :ansi))
=> ["DROP EXTENSION \"uuid-ossp\""]
```

### pattern matching

The `ilike` and `not-ilike` operators can be used to query data using a pattern matching technique.
- like
```clojure
(-> (select :name)
    (from :products)
    (where [:ilike :name "%name%"])
    sql/format)
=> ["SELECT name FROM products WHERE name ILIKE ?" "%name%"]
```
- not-ilike
```clojure
(-> (select :name)
    (from :products)
    (where [:not-ilike :name "%name%"])
    sql/format)
=> ["SELECT name FROM products WHERE name NOT ILIKE ?" "%name%"]
```

### except

```clojure
(sql/format
  {:except
    [{:select [:ip]}
     {:select [:ip] :from [:ip_location]}]})
=> ["SELECT ip EXCEPT SELECT ip FROM ip_location"]
```
`except-all` works the same way as `except`.

### filter

``` clojure
(-> (select (sql/call :count :*))
    (filter [(sql/call :count :*) (where [:< :i 5]) :foo]
            [(sql/call :count :*) (where [:between :i 3 10]) :bar])
    (from (sql/raw "generate_series(1,10) AS s(i)"))
    (sql/format))
=> ["SELECT count(*) , count(*) FILTER (WHERE i < ?) AS foo, count(*) FILTER (WHERE i BETWEEN ? AND ?) AS bar FROM generate_series(1,10) AS s(i)" 5 3 10]
```

### within group

``` clojure
(-> (select (sql/call :count :*))
    (within-group [(sql/call :percentile_disc (hsql-types/array [0.25 0.5 0.75])) (order-by :s.i) :alias])
    (from (sql/raw "generate_series(1,10) AS s(i)"))
    (sql/format))
=> ["SELECT count(*) , percentile_disc(ARRAY[?, ?, ?]) WITHIN GROUP (ORDER BY s.i) AS alias FROM generate_series(1,10) AS s(i)"
    0.25 0.50 0.75]
```

### SQL functions

The following are the SQL functions added in `honeysql-postgres`
- not
```clojure
(sql/format (sql/call :not nil))
=> ["NOT NULL"]
```
- primary-key
```clojure
(sql/format (sql/call :primary-key))
=> ["PRIMARY KEY"]

(sql/format (sql/call :primary-key :arg1 :arg2))
=> ["PRIMARY KEY(arg1, arg2)"]
```
- unique
```clojure
(sql/format (sql/call :unique))
=> ["UNIQUE"]

(sql/format (sql/call :unique :arg1 :arg2))
=> ["UNIQUE(arg1, arg2)"]
```
- foreign-key
```clojure
(sql/format (sql/call :foreign-key))
=> ["FOREIGN KEY"]

(sql/format (sql/call :foreign-key :arg1 :arg2))
=> ["FOREIGN KEY(arg1, arg2)"]
```
- references
```clojure
(sql/format (sql/call :references :reftable :refcolumn))
=> ["REFERENCES reftable(refcolumn)"]
```
- constraint
```clojure
(sql/format (sql/call :constraint :name))
=> ["CONSTRAINT name"]
```
- default
```clojure
(sql/format (sql/call :default :value))
=> ["DEFAULT value"]
```
- nextval
```clojure
(sql/format (sql/call :nextval :value))
=> ["nextval('value')"]
```
- check
```clojure
(sql/format (sql/call :check [:= :a :b]))
=> ["CHECK(a = b)"]

(sql/format (sql/call :check [:= :a :b] [:= :c :d]))
=> ["CHECK(a = b AND c = d)"]
```

## License

Copyright © 2021 Nilenso

Distributed under the Eclipse Public License, the same as Clojure.
