# honeysql-postgres
[![Clojars Project](https://img.shields.io/clojars/v/nilenso/honeysql-postgres.svg)](https://clojars.org/nilenso/honeysql-postgres) [![NPM Version](https://img.shields.io/npm/v/@honeysql/honeysql-postgres.svg)](https://www.npmjs.org/package/@honeysql/honeysql-postgres)

PostgreSQL extensions for widely used [honeysql](https://github.com/jkk/honeysql). This library extends the features of honeysql to support postgres specific SQL clauses and some basic SQL DDL in addition to the ones supported by the parent library. This library exists because it felt having a separate vendor specific namespace rather over having everything within honeysql.

Currently honeysql-postgres supports the following postgres specific clauses:

- upsert
  - on conflict
  - on conflict on constraint
  - do update set
  - do nothing
- returning
- partition by
- over
- window
- create view
- create table
- drop table
- alter table
  - add column
  - drop column
  - rename column
- insert-into-as
- pattern matching (ILIKE and NOT ILIKE)
- except (and except-all)

## Index

- [Usage](#usage)
  - [Leiningen](#leiningen)
  - [Maven](#maven)
  - [repl](#repl)
  - [Breaking Change](#breaking-change)
  - [upsert](#upsert)
  - [insert into with alias](#insert-into-with-alias)
  - [over](#over)
  - [create view](#create-view)
  - [create table](#create-table)
  - [drop table](#drop-table)
  - [alter table](#alter-table)
  - [pattern matching](#pattern-matching)
  - [except](#except)
  - [SQL functions](#sql-functions)
- [License](#license)

## Usage

### Leiningen
```clj
[nilenso/honeysql-postgres "0.2.6"]
```
### Maven
```xml
<dependency>
  <groupId>nilenso</groupId>
  <artifactId>honeysql-postgres</artifactId>
  <version>0.2.6</version>
</dependency>
```
### repl
```clj
; Note that `honeysql-postgres.format` and `honeysql-postgres.helpers`
; must be required into the project for the extended features to work.
(require '[honeysql.core :as sql]
         '[honeysql.helpers :refer :all]
         '[honeysql-postgres.format :refer :all]
         '[honeysql-postgres.helpers :as psqlh])
```

### Breaking Change
Implementation of `over` has been changed (from 0.2.2) to accept alias as an option and define the aggregator-function within the over clause and not in the select clause, this allows the inclusion of multiple window-function which was not possible in the previous implementation.

The query creation and usage is exactly the same as honeysql.

### upsert
`upsert` can be written either way. You can make use of `do-update-set!` over `do-update-set`, if you want to modify the some column values in case of conflicts.
```clj
(-> (insert-into :distributors)
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (psqlh/upsert (-> (on-conflict :did)
                      (do-update-set :dname)))
    (psqlh/returning :*)
    sql/format)
=> ["INSERT INTO distributors (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING *" "Gizmo Transglobal" "Associated Computing, Inc"]

(-> (insert-into :distributors)
    (values [{:did 23 :dname "Foo Distributors"}])
    (psqlh/on-conflict :did)
    (psqlh/do-update-set! [:dname "EXCLUDED.dname || ' (formerly ' || distributors.dname || ')'"] [:downer "EXCLUDED.downer"])
    sql/format)
=> ["INSERT INTO distributors (did, dname) VALUES (23, ?) ON CONFLICT (did) DO UPDATE SET dname = ?, downer = ?" "Foo Distributors" "EXCLUDED.dname || ' (formerly ' || d.dname || ')'" "EXCLUDED.downer"]
```

### insert into with alias
`insert-into-as` can be used to write insert statements with table name aliased.
```clj
(-> (psqlh/insert-into-as :distributors :d)
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (psqlh/upsert (-> (psqlh/on-conflict :did)
                      (psqlh/do-update-set :dname)
                      (where [:<> :d.zipcode "21201"])))
    (psqlh/returning :d.*)
    sql/format)
=> ["INSERT INTO distributors AS d (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname WHERE d.zipcode <> ? RETURNING d.*" "Gizmo Transglobal" "Associated Computing, Inc" "21201"]
```

### over
You can make use of `over` to write window functions where it takes in vectors with aggregator functions and window functions along with optional alias like `(over [aggregator-function window-function alias])`, the can be coupled with the `window` clause to write window-function functions with alias that is later defines the window-function, like `(-> (over [aggregator-function :w]) (window :w window-function))`.
```clj
(-> (select :id)
    (psqlh/over
      [(sql/call :avg :salary) (-> (partition-by :department) (order-by [:designation])) :Average]
      [(sql/call :max :salary) :w :MaxSalary])
    (from :employee)
    (psqlh/window :w (psqlh/partition-by :department))
    sql/format)
=> ["SELECT id , avg(salary) OVER (PARTITION BY department ORDER BY designation) AS Average, max(salary) OVER w AS MaxSalary FROM employee WINDOW w AS (PARTITION BY department)"]
```

### create view
`create-view` can be used to create views
```clj
(-> (psqlh/create-view :metro)
    (select :*)
    (from :cities)
    (where [:= :metroflag "Y"])
    sql/format)
=> ["CREATE VIEW metro AS SELECT * FROM cities WHERE metroflag = ?" "Y"]
```

### create table
`create-table` and `with-columns` can be used to create tables along with the SQL functions, where `create-table` takes a table name as argument and `with-columns` takes a vector of vectors as argument, where the vectors describe the column properties as `[:column-name :datatype :constraints ... ]`.
```clj
(-> (psqlh/create-table :films)
    (psqlh/with-columns [[:code (sql/call :char 5) (sql/call :constraint :firstkey) (sql/call :primary-key)]
                         [:title (sql/call :varchar 40) (sql/call :not nil)]
                         [:did :integer (sql/call :not nil)]
                         [:date_prod :date]
                         [:kind (sql/call :varchar 10)]])
    sql/format)
=> ["CREATE TABLE films (code char(5) CONSTRAINT firstkey PRIMARY KEY, title varchar(40) NOT NULL, did integer NOT NULL, date_prod date, kind varchar(10))"]
```

### drop table
`drop-table` is used to drop tables
```clj
(sql/format (psqlh/drop-table :cities :towns :vilages))
=> ["DROP TABLE cities, towns, vilages"]
```

### alter table
use `alter-table` along with `add-column` & `drop-column` to modify table level details
```clj
(-> (psqlh/alter-table :employees)
    (psqlh/add-column :address :text)
    sql/format)
=> ["ALTER TABLE employees ADD COLUMN address text"]

(-> (psqlh/alter-table :employees)
    (psqlh/drop-column :address)
    sql/format)
=> ["ALTER TABLE employees DROP COLUMN address"]
```

### pattern matching
The `ilike` and `not-ilike` operators can be used to query data using a pattern matching technique.
- like
```clj
(-> (select :name)
    (from :products)
    (where [:ilike :name "%name%"])
    sql/format)
=> ["SELECT * FROM products WHERE name ILIKE ?" "%name%"]
```
- not-ilike
```clj
(-> (select :name)
    (from :products)
    (where [:not-ilike :name "%name%"])
    sql/format)
=> ["SELECT * FROM products WHERE name NOT ILIKE ?" "%name%"]
```
### except

```clj

(sql/format
  {:except
    [{:select [:ip]}
     {:select [:ip] :from [:ip_location]}]})
=> ["SELECT ip EXCEPT SELECT ip FROM ip_location"]
```
`except-all` works the same way as `except`.

### SQL functions
The following are the SQL functions added in `honeysql-postgres`
- not
```clj
(sql/format (sql/call :not nil))
=> ["NOT NULL"]
```
- primary-key
```clj
(sql/format (sql/call :primary-key))
=> ["PRIMARY KEY"]

(sql/format (sql/call :primary-key :arg1 :arg2 ... ))
=> ["PRIMARY KEY (arg1, arg2, ... )"]
```
- unique
```clj
(sql/format (sql/call :unique))
=> ["UNIQUE"]

(sql/format (sql/call :unique :arg1 :arg2 ... ))
=> ["UNIQUE (arg1, arg2, ... )"]
```
- foreign-key
```clj
(sql/format (sql/call :foreign-key))
=> ["FOREIGN KEY"]

(sql/format (sql/call :foreign-key :arg1 :arg2 ... ))
=> ["FOREIGN KEY (arg1, arg2, ... )"]
```
- references
```clj
(sql/format (sql/call :references :reftable :refcolumn))
=> ["REFERENCES reftable(refcolumn)"]
```
- constraint
```clj
(sql/format (sql/call :constraint :name))
=> ["CONSTRAINT name"]
```
- default
```clj
(sql/format (sql/call :default value))
=> ["DEFAULT value"]
```
- nextval
```clj
(sql/format (sql/call :nextval value))
=> ["nextval('value')"]
```
- check
```clj
(sql/format (sql/call :check [:= :a :b]))
=> ["CHECK(a = b)"]

(sql/format (sql/call :check [:= :a :b] [:= :c :d]))
["CHECK(a = b AND c = d)"]
```
## License

Copyright © 2016 Nilenso

Distributed under the Eclipse Public License, the same as Clojure.
