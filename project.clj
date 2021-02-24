(defproject magnet/rbac "0.1.0-alpha-4-SNAPSHOT"
  :description "A Clojure library designed to provide role-based access control (RBAC)"
  :url "https://github.com/magnetcoop/rbac"
  :license {:name "Mozilla Public Licence 2.0"
            :url "https://www.mozilla.org/en-US/MPL/2.0/"}
  :min-lein-version "2.9.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [magnet/sql-utils "0.4.11"]
                 [honeysql "1.0.461"]]
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit" "Release v%s"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ;; We don't want to deploy manually, we let TravisCI do it
                  ;; after it has run all the tests, etc. So simply push
                  ;; the new version and let TravisCI do its work.
                  ["vcs" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit" "Prepare for next development release"]
                  ["vcs" "push"]]
  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:repl-options {:host "0.0.0.0"
                         :port 4001}}
   :profiles/dev {}
   :project/dev {:dependencies [[duct/logger "0.3.0"]
                                [org.postgresql/postgresql "42.2.14"]]
                 :plugins [[jonase/eastwood "0.3.11"]
                           [lein-cljfmt "0.6.8"]]}})
