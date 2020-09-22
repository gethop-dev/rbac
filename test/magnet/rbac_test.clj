(ns magnet.rbac-test
  (:require [clojure.java.io :as io]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [duct.logger :as logger]
            [magnet.rbac :as rbac]
            [magnet.sql-utils :as sql-utils])
  (:import [java.util UUID]))

(def ^:const db (System/getenv "JDBC_DATABASE_URL"))

(defn enable-instrumentation []
  (-> (stest/enumerate-namespace 'magnet.rbac) stest/instrument))

(defrecord AtomLogger [logs]
  logger/Logger
  (-log [logger level ns-str file line id event data]
    (swap! logs conj [level ns-str file line event data])))

(defonce ^:const app-users
  {:app-user-1 {:id (UUID/randomUUID)
                :username "first.user"
                :email "first.user@magnet.coop"}
   :app-user-2 {:id (UUID/randomUUID)
                :username "second.user"
                :email "second.user@magnet.coop"}})

(defonce ^:const app-resources
  {:application {:id (UUID/randomUUID)
                 :name "Application"
                 :description "Application description"}
   :organization-1 {:id (UUID/randomUUID)
                    :name "Organization 1"
                    :description "Organization 1 description"}
   :organization-2 {:id (UUID/randomUUID)
                    :name "Organization 2"
                    :description "Organization 2 description"}
   :plant-1 {:id (UUID/randomUUID)
             :name "Plant 1"
             :description "Plant 1 description"}
   :plant-2 {:id (UUID/randomUUID)
             :name "Plant 2"
             :description "Plant 2 description"}
   :asset-1 {:id (UUID/randomUUID)
             :name "Asset 1"
             :description "Asset 1 description"}
   :asset-2 {:id (UUID/randomUUID)
             :name "Asset 2"
             :description "Asset 2 description"}})

(defonce ^:const context-types
  {:application {:name :application
                 :description "Application context"}
   :organization {:name :organization
                  :description "Organization context description"}
   :plant {:name :plant
           :description "Plant context"}
   :asset {:name :asset
           :description "Assets context"}})

(defonce ^:const application-context
  {:context-type-name (get-in context-types [:application :name])
   :resource-id (get-in app-resources [:application :id])})

(defonce ^:const organization-1-context
  {:context-type-name (get-in context-types [:organization :name])
   :resource-id (get-in app-resources [:organization-1 :id])})

(defonce ^:const plant-1-context
  {:context-type-name (get-in context-types [:plant :name])
   :resource-id (get-in app-resources [:plant-1 :id])})

(defonce ^:const asset-1-context
  {:context-type-name (get-in context-types [:asset :name])
   :resource-id (get-in app-resources [:asset-1 :id])})

(defonce ^:const organization-2-context
  {:context-type-name (get-in context-types [:organization :name])
   :resource-id (get-in app-resources [:organization-2 :id])})

(defonce ^:const plant-2-context
  {:context-type-name (get-in context-types [:plant :name])
   :resource-id (get-in app-resources [:plant-2 :id])})

(defonce ^:const asset-2-context
  {:context-type-name (get-in context-types [:asset :name])
   :resource-id (get-in app-resources [:asset-2 :id])})

(defonce ^:const roles
  [{:name :application/manager
    :description "Application manager"}
   {:name :organization/manager
    :description "Organization manager"}
   {:name :plant/manager
    :description "Plant manager"}
   {:name :asset/manager
    :description "Asset manager"}
   {:name :asset-1/manager
    :description "Asset 1 manager"}])

(defonce ^:const permissions
  [{:name :application/manage
    :description "Manage Application"
    :context-type-name :application}
   {:name :organization/manage
    :description "Manage Organization"
    :context-type-name :organization}
   {:name :plant/manage
    :description "Manage Plant"
    :context-type-name :plant}
   {:name :asset/manage
    :description "Manage Asset"
    :context-type-name :asset}])

(defonce ^:const rbac-tables-up-sql
  "magnet.rbac/rbac-tables.pg.up.sql")

(defonce ^:const rbac-tables-down-sql
  "magnet.rbac/rbac-tables.pg.down.sql")

(defonce ^:const app-tables-up-sql
  "_files/app-tables.up.sql")

(defonce ^:const app-tables-down-sql
  "_files/app-tables.down.sql")

(defn- setup-app-objects []
  (sql-utils/sql-execute! db nil (slurp (io/resource app-tables-up-sql)))
  (dorun (map (fn [[_ v]]
                (sql-utils/sql-insert! db nil :appuser (keys v) (vals v)))
              app-users))
  (dorun (map (fn [[_ v]]
                (sql-utils/sql-insert! db nil :resource (keys v) (vals v)))
              app-resources)))

(defn- setup-rbac-tables []
  (sql-utils/sql-execute! db nil (slurp (io/resource rbac-tables-up-sql))))

(defn- destroy-app-objects []
  (sql-utils/sql-execute! db nil (slurp (io/resource app-tables-down-sql))))

(defn- destroy-rbac-tables []
  (sql-utils/sql-execute! db nil (slurp (io/resource rbac-tables-down-sql))))

(use-fixtures
  :once (fn reset-db [f]
          (enable-instrumentation)
          (setup-app-objects)
          (setup-rbac-tables)
          (f)
          (destroy-rbac-tables)
          (destroy-app-objects)))

(deftest test-1
  (let [logs (atom [])
        logger (->AtomLogger logs)
        _ (rbac/create-context-types! db logger (vals context-types))
        ;;
        application-ctx (:context (rbac/create-context! db logger application-context nil))
        organization-1-ctx (:context (rbac/create-context! db logger organization-1-context application-ctx))
        plant-1-ctx (:context (rbac/create-context! db logger plant-1-context organization-1-ctx))
        _ (:context (rbac/create-context! db logger asset-1-context plant-1-ctx))
        _ (rbac/create-roles! db logger roles)
        _ (rbac/create-permissions! db logger permissions)
        ;;
        _ (rbac/grant-role-permissions! db logger
                                        (:role (rbac/get-role-by-name db logger :application/manager))
                                        [(-> (rbac/get-permission-by-name db logger :application/manage)
                                             :permission)
                                         (-> (rbac/get-permission-by-name db logger :plant/manage)
                                             :permission)
                                         (-> (rbac/get-permission-by-name db logger :asset/manage)
                                             :permission)])
        _ (rbac/deny-role-permissions! db logger
                                       (:role (rbac/get-role-by-name db logger :application/manager))
                                       [(-> (rbac/get-permission-by-name db logger :organization/manage)
                                            :permission)])
        _ (rbac/grant-role-permissions! db logger
                                        (:role (rbac/get-role-by-name db logger :organization/manager))
                                        [(-> (rbac/get-permission-by-name db logger :organization/manage)
                                             :permission)
                                         (-> (rbac/get-permission-by-name db logger :plant/manage)
                                             :permission)
                                         (-> (rbac/get-permission-by-name db logger :asset/manage)
                                             :permission)])
        _ (rbac/grant-role-permissions! db logger
                                        (:role (rbac/get-role-by-name db logger :plant/manager))
                                        [(-> (rbac/get-permission-by-name db logger :plant/manage)
                                             :permission)
                                         (-> (rbac/get-permission-by-name db logger :asset/manage)
                                             :permission)])
        _ (rbac/grant-role-permissions! db logger
                                        (:role (rbac/get-role-by-name db logger :asset/manager))
                                        [(-> (rbac/get-permission-by-name db logger :asset/manage)
                                             :permission)])
        _ (rbac/deny-role-permissions! db logger
                                       (:role (rbac/get-role-by-name db logger :asset-1/manager))
                                       [(-> (rbac/get-permission-by-name db logger :asset/manage)
                                            :permission)])
        ;;
        _ (rbac/add-super-admin! db logger (get-in app-users [:app-user-2 :id]))
        _ (rbac/assign-roles! db logger
                              [{:role (:role (rbac/get-role-by-name db logger :application/manager))
                                :context
                                (:context (rbac/get-context db logger
                                                            :application
                                                            (get-in app-resources [:application :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db logger :organization/manager))
                                :context
                                (:context (rbac/get-context db logger
                                                            :organization
                                                            (get-in app-resources [:organization-1 :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db logger :plant/manager))
                                :context
                                (:context (rbac/get-context db logger
                                                            :plant
                                                            (get-in app-resources [:plant-1 :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db logger :asset/manager))
                                :context
                                (:context (rbac/get-context db logger
                                                            :plant
                                                            (get-in app-resources [:plant-1 :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db logger :asset-1/manager))
                                :context
                                (:context (rbac/get-context db logger
                                                            :asset
                                                            (get-in app-resources [:asset-1 :id])))
                                :user (:app-user-1 app-users)}])]
    (testing "app-user-1 has :application/manage permission on :application resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :application :id)
            context-type-name :application
            permission-name :application/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 has :organization/manage permission on :organization-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :organization-1 :id)
            context-type-name :organization
            permission-name :organization/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission false))))

    (testing "app-user-1 has :application/manage permission on :organization-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :organization-1 :id)
            context-type-name :organization
            permission-name :application/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 has :plant/manage permission on :plant-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :plant-1 :id)
            context-type-name :plant
            permission-name :plant/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 :application/manage permission on :plant-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :plant-1 :id)
            context-type-name :plant
            permission-name :application/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 has :asset/manage permission on :plant-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :plant-1 :id)
            context-type-name :plant
            permission-name :asset/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 doesn't have :asset/manage permission on :asset-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :asset-1 :id)
            context-type-name :asset
            permission-name :asset/manage
            has-permission (rbac/has-permission db logger user-id resource-id context-type-name permission-name)]
        (is (= has-permission false))))))

(comment
  ;; TODO: Create all the individual unit tests by leveraging the example code below.

  ;; -----------------------------------------------------
  (def logs (atom []))
  (def logger (->AtomLogger logs))
  (rbac/create-roles! db logger roles)
  (rbac/create-role! db logger (first roles))
  (rbac/get-roles db logger)
  (rbac/get-role-by-id db logger
                       (-> (rbac/get-role-by-name db logger :application/manager)
                           :role
                           :id))
  (rbac/get-role-by-name db logger :application/manager)
  (rbac/update-role! db logger
                     (-> (rbac/get-role-by-name db logger :application/manager)
                         :role
                         (assoc :name :application/manager)))
  (rbac/update-roles! db logger
                      [(-> (rbac/get-role-by-name db logger :application/manager)
                           :role
                           (assoc :name :application/manager))
                       (-> (rbac/get-role-by-name db logger :application/manager)
                           :role
                           (assoc :name :application/manager))])
  (rbac/delete-role! db logger
                     (-> (rbac/get-role-by-name db logger :application/manager)
                         :role))
  (rbac/delete-role-by-id! db logger
                           (-> (rbac/get-role-by-name db logger :organization/manager)
                               :role
                               :id))
  (rbac/delete-role-by-name! db logger :asset/manager)
  (rbac/delete-roles! db logger
                      [(-> (rbac/get-role-by-name db logger :plant/manager)
                           :role)
                       (-> (rbac/get-role-by-name db logger :application/manager)
                           :role)])
  (rbac/delete-roles-by-ids! db logger
                             [(-> (rbac/get-role-by-name db logger :plant/manager)
                                  :role
                                  :id)
                              (-> (rbac/get-role-by-name db logger :application/manager)
                                  :role
                                  :id)])
  (rbac/delete-roles-by-names! db logger [:organization/manager :asset/manager])

  ;; -----------------------------------------------------
  (rbac/create-context-types! db logger (vals context-types))
  (rbac/create-context-type! db logger (:application context-types))
  (rbac/get-context-types db logger)
  (rbac/get-context-type db logger :application)
  (rbac/get-context-type db logger :asset)
  (rbac/update-context-type! db logger (-> (rbac/get-context-type db logger :application)
                                           :context-type
                                           (assoc :description "Some updated description")))
  (rbac/update-context-types! db logger
                              [(-> (rbac/get-context-type db logger :application)
                                   :context-type
                                   (assoc :description "Some updated description for application"))
                               (-> (rbac/get-context-type db logger :organization)
                                   :context-type
                                   (assoc :description "Some updated description for organization"))])
  (rbac/delete-context-type! db logger
                             (-> (rbac/get-context-type db logger :organization)
                                 :context-type))
  (rbac/delete-context-types! db logger
                              [(-> (rbac/get-context-type db logger :application)
                                   :context-type)
                               (-> (rbac/get-context-type db logger :organization)
                                   :context-type)])
  (rbac/delete-context-types! db logger
                              [(-> (rbac/get-context-type db logger :plant)
                                   :context-type)
                               (-> (rbac/get-context-type db logger :asset)
                                   :context-type)])

  ;; -----------------------------------------------------
  (let [_ (rbac/create-context-types! db logger (vals context-types))
        application-ctx (:context (rbac/create-context! db logger application-context nil))
        organization-1-ctx (:context (rbac/create-context! db logger organization-1-context application-ctx))
        organization-2-ctx (:context (rbac/create-context! db logger organization-2-context application-ctx))
        plant-1-ctx (:context (rbac/create-context! db logger plant-1-context organization-1-ctx))
        plant-2-ctx (:context (rbac/create-context! db logger plant-2-context organization-2-ctx))
        asset-1-ctx (:context (rbac/create-context! db logger asset-1-context plant-1-ctx))
        asset-2-ctx (:context (rbac/create-context! db logger asset-2-context plant-2-ctx))]
    [application-ctx
     organization-1-ctx
     organization-2-ctx
     plant-1-ctx
     plant-2-ctx
     asset-1-ctx
     asset-2-ctx])
  (rbac/get-contexts db logger)
  (rbac/get-context db logger :application (get-in app-resources [:application :id]))
  (rbac/get-context db logger :asset (get-in app-resources [:asset-1 :id]))
  (rbac/update-context! db logger (-> (rbac/get-context db logger :application (get-in app-resources [:application :id]))
                                      :context
                                      (assoc :context-type-name :asset)))
  (rbac/update-contexts! db logger
                         [(-> (rbac/get-context db logger :asset (get-in app-resources [:application :id]))
                              :context
                              (assoc :context-type-name :application))
                          (-> (rbac/get-context db logger :asset (get-in app-resources [:asset-1 :id]))
                              :context
                              (assoc :context-type-name :application))])
  (rbac/update-context! db logger (-> (rbac/get-context db logger :application (get-in app-resources [:asset-1 :id]))
                                      :context
                                      (assoc :context-type-name :asset)))
  (rbac/delete-context! db logger
                        (-> (rbac/get-context db logger :application (get-in app-resources [:application :id]))
                            :context))
  (rbac/delete-contexts! db logger
                         [(-> (rbac/get-context db logger :asset (get-in app-resources [:asset-1 :id]))
                              :context)
                          (-> (rbac/get-context db logger :plant (get-in app-resources [:plant-1 :id]))
                              :context)
                          (-> (rbac/get-context db logger :organization (get-in app-resources [:organization-1 :id]))
                              :context)])

  ;; -----------------------------------------------------
  (rbac/create-permissions! db logger permissions)
  (rbac/create-permission! db logger (first permissions))
  (rbac/get-permissions db logger)
  (rbac/get-permission-by-id db logger
                             (-> (rbac/get-permission-by-name db logger :application/manage)
                                 :permission
                                 :id))
  (rbac/get-permission-by-name db logger
                               (-> (rbac/get-permission-by-name db logger :application/manage)
                                   :permission
                                   :name))
  (rbac/update-permission! db logger
                           (-> (rbac/get-permission-by-name db logger :application/manage)
                               :permission
                               (assoc :name :application/manageXX)))
  (rbac/update-permissions! db logger
                            [(-> (rbac/get-permission-by-name db logger :application/manageXX)
                                 :permission
                                 (assoc :name :application/manageYY))
                             (-> (rbac/get-permission-by-name db logger :application/manageXX)
                                 :permission
                                 (assoc :name :application/manage))])
  (rbac/delete-permission! db logger
                           (-> (rbac/get-permission-by-name db logger :application/manage)
                               :permission))
  (rbac/delete-permission-by-id! db logger
                                 (-> (rbac/get-permission-by-name db logger :application/manage)
                                     :permission
                                     :id))
  (rbac/delete-permission-by-name! db logger
                                   (-> (rbac/get-permission-by-name db logger :application/manage)
                                       :permission
                                       :name))
  (rbac/delete-permissions! db logger
                            [(-> (rbac/get-permission-by-name db logger :application/manage)
                                 :permission)
                             (-> (rbac/get-permission-by-name db logger :organization/manage)
                                 :permission)])
  (rbac/delete-permissions-by-ids! db logger
                                   [(-> (rbac/get-permission-by-name db logger :application/manage)
                                        :permission
                                        :id)
                                    (-> (rbac/get-permission-by-name db logger :organization/manage)
                                        :permission
                                        :id)])
  (rbac/delete-permissions-by-names! db logger
                                     [(-> (rbac/get-permission-by-name db logger :application/manage)
                                          :permission
                                          :name)
                                      (-> (rbac/get-permission-by-name db logger :organization/manage)
                                          :permission
                                          :name)])

  ;; -----------------------------------------------------
  (rbac/add-super-admin! db logger (get-in app-users [:app-user-1 :id]))
  (rbac/super-admin? db logger (get-in app-users [:app-user-1 :id]))
  (rbac/super-admin? db logger (get-in app-users [:app-user-2 :id]))
  (rbac/remove-super-admin! db logger (get-in app-users [:app-user-1 :id]))
  (rbac/remove-super-admin! db logger (get-in app-users [:app-user-2 :id]))

  ;; -----------------------------------------------------
  (rbac/grant-role-permission! db logger
                               (:role (rbac/get-role-by-name db logger :organization/manager))
                               (-> (rbac/get-permission-by-name db logger :organization/manage)
                                   :permission))
  (rbac/remove-role-permission! db logger
                                (:role (rbac/get-role-by-name db logger :organization/manager))
                                (-> (rbac/get-permission-by-name db logger :organization/manage)
                                    :permission))
  (rbac/deny-role-permission! db logger
                              (:role (rbac/get-role-by-name db logger :organization/manager))
                              (-> (rbac/get-permission-by-name db logger :organization/manage)
                                  :permission))
  (rbac/remove-role-permission! db logger
                                (:role (rbac/get-role-by-name db logger :organization/manager))
                                (-> (rbac/get-permission-by-name db logger :organization/manage)
                                    :permission))

  ;; -----------------------------------------------------
  (rbac/assign-role! db logger
                     {:role (:role (rbac/get-role-by-name db logger :application/manager))
                      :context (:context (rbac/get-context db logger
                                                           :application
                                                           (get-in app-resources [:application :id])))
                      :user (:app-user-1 app-users)})
  (rbac/assign-roles! db logger
                      [{:role (:role (rbac/get-role-by-name db logger :application/manager))
                        :context
                        (:context (rbac/get-context db logger
                                                    :application
                                                    (get-in app-resources [:application :id])))
                        :user (:app-user-1 app-users)}
                       {:role (:role (rbac/get-role-by-name db logger :organization/manager))
                        :context (:context (rbac/get-context db logger
                                                             :organization
                                                             (get-in app-resources [:organization-2 :id])))
                        :user (:app-user-2 app-users)}])
  (rbac/unassign-role! db logger
                       {:role (:role (rbac/get-role-by-name db logger :application/manager))
                        :context (:context (rbac/get-context db logger
                                                             :application
                                                             (get-in app-resources [:application :id])))
                        :user (:app-user-1 app-users)})
  (rbac/unassign-roles! db logger
                        [{:role (:role (rbac/get-role-by-name db logger :application/manager))
                          :context
                          (:context (rbac/get-context db logger
                                                      :application
                                                      (get-in app-resources [:application :id])))
                          :user (:app-user-1 app-users)}
                         {:role (:role (rbac/get-role-by-name db logger :organization/manager))
                          :context (:context (rbac/get-context db logger
                                                               :organization
                                                               (get-in app-resources [:organization-2 :id])))
                          :user (:app-user-2 app-users)}]))
