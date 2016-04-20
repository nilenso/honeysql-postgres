# honeysql-postgres

PostgreSQL extenstions for [honeysql](https://github.com/jkk/honeysql). This library extends the features of honeysql to support postgres specific SQL clauses. Currently honeysql-postgres supports the following postgres specific clauses
- UPSERT
  - ON CONFLICT
  - ON CONFLICT ON CONSTRAINT
  - DO UPDATE SET
  - DO NOTHING
- RETURNING

## Usage

```clj
(require '[honeysql.core :as sql]
         '[honeysql.helpers :refer :all]
	 '[honeysql-postgres.format :refer :all]
	 '[honeysql-postgres.helpers :refer :all])
```

The query creation and usage is exactly the same as honeysql.

Upsert would be written like
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


## License

Copyright © 2016 Nilenso

Distributed under the Eclipse Public License, the same as Clojure.
