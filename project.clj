(defproject nilenso/honeysql-postgres "0.2.3"
  :description "PostgreSQL extension for honeysql"
  :url "https://github.com/nilenso/honeysql-postgres"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [honeysql "0.9.2"]]
  :tach {:test-runner-ns 'honeysql-postgres.postgres-test
         :source-paths ["src" "test"]}
  :profiles {:dev {:plugins [[lein-tach "0.4.0"]]}})
