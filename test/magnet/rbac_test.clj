(ns magnet.rbac-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [magnet.rbac :refer :all]))

(def db-spec "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")

(defn enable-instrumentation []
  (-> (stest/enumerate-namespace 'magnet.rbac) stest/instrument))

(use-fixtures
  :once (fn reset-db [f]
          (enable-instrumentation)
          (f)
          (jdbc/execute! db-spec "DROP ALL OBJECTS")))