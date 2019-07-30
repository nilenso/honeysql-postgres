## 0.2.6
- Add ILIKE, NOT ILIKE
- ADD EXCEPT and EXCEPT ALL
- Fix query-values priority

## 0.2.5
- Adds if-exist in drop table

## 0.2.4
- Add if-not-exist option to create-table
- Self-host ClojureScript port
- Add package.json for npm publishing

## 0.2.3
- Allows where clause to be used as an index predicate and update condition during upsert

## 0.2.2-SNAPSHOT
- Breaking change (Revised implementation)
  - over
- Added the following
  - window

## 0.2.1-SNAPSHOT
- Added the following
  - alter-table
  - add-column
  - drop-column

## 0.2.0
- Added the following helpers and corresponding clauses
  - partition by
  - over (window function)
  - create view
  - create table
  - drop table
- Added the following SQL function calls
  - not
  - primary-key
  - foreign-key
  - unique
  - references
  - constraint
  - default
  - nextval
  - check

## 0.1.0
- Added upsert & returning clauses
