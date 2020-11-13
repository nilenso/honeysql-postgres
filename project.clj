(defproject nilenso/honeysql-postgres "0.2.6"
  :description "PostgreSQL extension for honeysql"
  :url "https://github.com/nilenso/honeysql-postgres"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [honeysql "1.0.444"]]
  :plugins [[lein-cljfmt "0.7.0"]]
  :tach {:test-runner-ns 'honeysql-postgres.postgres-test
         :source-paths   ["src" "test"]}
  :profiles {:dev {:plugins [[lein-tach "0.4.0"]]}})
