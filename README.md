# honeysql-postgres
[![Clojars Project](https://img.shields.io/clojars/v/nilenso/honeysql-postgres.svg)](https://clojars.org/nilenso/honeysql-postgres)

PostgreSQL extensions for [honeysql](https://github.com/jkk/honeysql). This library extends the features of honeysql to support postgres specific SQL clauses. Currently honeysql-postgres supports the following postgres specific clauses

- upsert
  - on conflict
  - on conflict on constraint
  - do update set
  - do nothing
- returning
- partition by
- over (window function)
- create view
- create table
- drop table

## Usage

### Leiningen
```clj
[nilenso/honeysql-postgres "0.1.0-SNAPSHOT"]
```
### Mave
```xml
<dependency>
  <groupId>nilenso</groupId>
  <artifactId>honeysql-postgres</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```clj
(require '[honeysql.core :as sql]
         '[honeysql.helpers :refer :all]
         '[honeysql-postgres.format :refer :all]
         '[honeysql-postgres.helpers :refer :all])
```

The query creation and usage is exactly the same as honeysql.

**Upsert** would be written like
```clj
(-> (insert-into [:distributors :d]
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (upsert (-> (on-conflict :did)
                (do-update-set :dname)))
    (returning :d.*)
    sql/format)
=> ["INSERT INTO distributors d (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING d.*" "Gizmo Transglobal" "Associated Computing, Inc"]
```

Most of the times the above can also be written without the `upsert` helper function, you would need the `upsert` helper function only when you have sub queries or `where` clause within the upsert.
```clj
(-> (insert-into [:distributors :d])
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (on-conflict :did)
    (do-update-set :dname)
    (returning :d.*)
    sql/format)
=> ["INSERT INTO distributors d (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname RETURNING d.*" "Gizmo Transglobal" "Associated Computing, Inc"]

(-> (insert-into [:distributors :d])
    (values [{:did 5 :dname "Gizmo Transglobal"}
             {:did 6 :dname "Associated Computing, Inc"}])
    (upsert (-> (on-conflict :did)
                (do-update-set :dname)
                (where [:<> :d.zipcode "21201"])))
    (returning :d.*)
    sql/format)
=> ["INSERT INTO distributors d (did, dname) VALUES (5, ?), (6, ?) ON CONFLICT (did) DO UPDATE SET dname = EXCLUDED.dname WHERE d.zipcode <> ? RETURNING d.*" "Gizmo Transglobal" "Associated Computing, Inc" "21201"]
```

You can make use of `over` and `partition-by` to write window functions
```clj
(-> (select :last_name :salary :department (sql/call :rank))
    (over (-> (partition-by :department)
              (order-by [:salary :desc])))
    (from :employees)
    sql/format)
=> ["SELECT last_name, salary, department, rank() OVER (PARTITION BY department ORDER BY salary DESC) FROM employees"]
```

`create-view` can be used to create views
```clj
(-> (create-view :metro)
    (select :*)
    (from :cities)
    (where [:= :metroflag "Y"])
    sql/format)
=> ["CREATE VIEW metro AS SELECT * FROM cities WHERE metroflag = ?" "Y"]
```

`create-table` and `with-columns` can be used to create tables along with the SQL functions, where `create-table` takes a table name as argument and `with-columns` takes a vector of vectors as argument, where the vectors describe the column properties as `[:column-name :datatype :constraints ... ]`.
```clj
(-> (create-table :films)
    (with-columns [[:code (sql/call :char 5) (sql/call :constraint :firstkey) (sql/call :primary-key)]
                   [:title (sql/call :varchar 40) (sql/call :not nil)]
                   [:did :integer (sql/call :not nil)]
                   [:date_prod :date]
                   [:kind (sql/call :varchar 10)]])
    sql/format)
=> ["CREATE TABLE films (code char(5) CONSTRAINT firstkey PRIMARY KEY, title varchar(40) NOT NULL, did integer NOT NULL, date_prod date, kind varchar(10))"]
```

`drop-table` is used to drop tables
```clj
(sql/format (drop-table :cities :towns :vilages))
=> ["DROP TABLE cities, towns, vilages"]
```

The following are the SQL functions added in `honeysql-postgres`
- not
```clj
(sql/format (sql/call :not nil))
=> "NOT NULL"
```
- primary-key
```clj
(sql/format (sql/call :primary-key))
=> "PRIMARY KEY"

(sql/format (sql/call :primary-key :arg1 :arg2 ... ))
=> "PRIMARY KEY (arg1, arg2, ... )"
```
- unique
```clj
(sql/format (sql/call :unique))
=> "UNIQUE"

(sql/format (sql/call :unique :arg1 :arg2 ... ))
=> "UNIQUE (arg1, arg2, ... )"
```
- foreign-key
```clj
(sql/format (sql/call :foreign-key))
=> "FOREIGN KEY"

(sql/format (sql/call :foreign-key :arg1 :arg2 ... ))
=> "FOREIGN KEY (arg1, arg2, ... )"
```
- references
```clj
(sql/format (sql/call :references :reftable :refcolumn))
=> "REFERENCES reftable(refcolumn)"
```
- constraint
```clj
(sql/format (sql/call :constraint :name))
=> "CONSTRAINT name"
```
- default
```clj
(sql/format (sql/call :default value))
"DEFAULT value"
```
- nextval
```clj
(sql/format (sql/value :nextval value))
"nextval('value')"
```

## License

Copyright © 2016 Nilenso

Distributed under the Eclipse Public License, the same as Clojure.
