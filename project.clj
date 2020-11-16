(defproject nilenso/honeysql-postgres "0.2.6"
  :description "PostgreSQL extension for honeysql"
  :url "https://github.com/nilenso/honeysql-postgres"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [honeysql "1.0.444"]]
  :deploy-repositories [["clojars" {:url      "https://clojars.org/nilenso/honeysql-postgres"
                                    :username :env/CLOJARS_USERNANE
                                    :password :env/CLOJARS_DEPLOY_TOKEN}]]
  :plugins [[lein-cljfmt "0.7.0"]]
  :tach {:test-runner-ns 'honeysql-postgres.postgres-test
         :source-paths   ["src" "test"]}
  :profiles {:dev {:plugins [[lein-tach "0.4.0"]]}
             :ci  {:dependencies [[seancorfield/readme "1.0.16"]]
                   :aliases      {"verify-readme" ["run" "-m" "seancorfield.readme"]}}})
