(ns magnet.rbac-test
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [magnet.rbac :as rbac]
            [magnet.sql-utils :as sql-utils])
  (:import [java.util UUID]))

(def ^:const db "jdbc:postgresql://postgres:5432/rbac?user=postgres")

(defn enable-instrumentation []
  (-> (stest/enumerate-namespace 'magnet.rbac) stest/instrument))

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

(def ^:const context-types
  {:application {:context-type :application
                 :description "Application context"}
   :organization {:context-type :organization
                  :description "Organization context description"}
   :plant {:context-type :plant
           :description "Plant context"}
   :asset {:context-type :asset
           :description "Assets context"}})

(def ^:const application-context
  {:context-type (get-in context-types [:application :context-type])
   :resource-id (get-in app-resources [:application :id])})

(def ^:const organization-1-context
  {:context-type (get-in context-types [:organization :context-type])
   :resource-id (get-in app-resources [:organization-1 :id])})

(def ^:const plant-1-context
  {:context-type (get-in context-types [:plant :context-type])
   :resource-id (get-in app-resources [:plant-1 :id])})

(def ^:const asset-1-context
  {:context-type (get-in context-types [:asset :context-type])
   :resource-id (get-in app-resources [:asset-1 :id])})

(def ^:const organization-2-context
  {:context-type (get-in context-types [:organization :context-type])
   :resource-id (get-in app-resources [:organization-2 :id])})

(def ^:const plant-2-context
  {:context-type (get-in context-types [:plant :context-type])
   :resource-id (get-in app-resources [:plant-2 :id])})

(def ^:const asset-2-context
  {:context-type (get-in context-types [:asset :context-type])
   :resource-id (get-in app-resources [:asset-2 :id])})

(def ^:const roles
  [{:name "application/manager"
    :description "Application manager"}
   {:name "organization/manager"
    :description "Organization manager"}
   {:name "plant/manager"
    :description "Plant manager"}
   {:name "asset/manager"
    :description "Asset manager"}
   {:name "asset-1/manager"
    :description "Asset 1 manager"}])

(def ^:const permissions
  [{:name :application/manage
    :description "Manage Application"
    :context-type :application}
   {:name :organization/manage
    :description "Manage Organization"
    :context-type :organization}
   {:name :plant/manage
    :description "Manage Plant"
    :context-type :plant}
   {:name :asset/manage
    :description "Manage Asset"
    :context-type :asset}])

(def ^:const rbac-tables-up-sql
  "rbac/rbac-tables.pg.up.sql")

(def ^:const rbac-tables-down-sql
  "rbac/rbac-tables.pg.down.sql")

(def ^:const app-tables-up-sql
  "_files/app-tables.up.sql")

(def ^:const app-tables-down-sql
  "_files/app-tables.down.sql")

(defn- setup-app-objects []
  (sql-utils/sql-execute! db nil (slurp (io/resource app-tables-up-sql)))
  (dorun (map (fn [[k v]]
                (sql-utils/sql-insert! db nil :appuser (keys v) (vals v)))
              app-users))
  (dorun (map (fn [[k v]]
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
  (let [_ (rbac/create-context-types! db nil (vals context-types))
        ;;
        application-ctx (:context (rbac/create-context! db nil application-context nil))
        organization-1-ctx (:context (rbac/create-context! db nil organization-1-context application-ctx))
        plant-1-ctx (:context (rbac/create-context! db nil plant-1-context organization-1-ctx))
        asset-1-ctx (:context (rbac/create-context! db nil asset-1-context plant-1-ctx))
        _ (rbac/create-roles! db nil roles)
        _ (rbac/create-permissions! db nil permissions)
        ;;
        _ (rbac/grant-role-permissions db nil
                                       (:role (rbac/get-role-by-name db nil "application/manager"))
                                       [(-> (rbac/get-permission-by-name db nil :application/manage)
                                            :permission)])
        _ (rbac/grant-role-permissions db nil
                                       (:role (rbac/get-role-by-name db nil "organization/manager"))
                                       [(-> (rbac/get-permission-by-name db nil :organization/manage)
                                            :permission)])
        _ (rbac/deny-role-permissions db nil
                                      (:role (rbac/get-role-by-name db nil "organization/manager"))
                                      [(-> (rbac/get-permission-by-name db nil :application/manage)
                                           :permission)])
        _ (rbac/grant-role-permissions db nil
                                       (:role (rbac/get-role-by-name db nil "plant/manager"))
                                       [(-> (rbac/get-permission-by-name db nil :plant/manage)
                                            :permission)
                                        (-> (rbac/get-permission-by-name db nil :application/manage)
                                            :permission)])
        _ (rbac/grant-role-permissions db nil
                                       (:role (rbac/get-role-by-name db nil "asset/manager"))
                                       [(-> (rbac/get-permission-by-name db nil :asset/manage)
                                            :permission)])
        _ (rbac/deny-role-permissions db nil
                                      (:role (rbac/get-role-by-name db nil "asset-1/manager"))
                                      [(-> (rbac/get-permission-by-name db nil :asset/manage)
                                           :permission)])
        ;;
        _ (rbac/add-super-admin db nil (get-in app-users [:app-user-2 :id]))
        _ (rbac/assign-roles! db nil
                              [{:role (:role (rbac/get-role-by-name db nil "application/manager"))
                                :context
                                (:context (rbac/get-context db nil
                                                            :application
                                                            (get-in app-resources [:application :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db nil "organization/manager"))
                                :context
                                (:context (rbac/get-context db nil
                                                            :organization
                                                            (get-in app-resources [:organization-1 :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db nil "plant/manager"))
                                :context
                                (:context (rbac/get-context db nil
                                                            :plant
                                                            (get-in app-resources [:plant-1 :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db nil "asset/manager"))
                                :context
                                (:context (rbac/get-context db nil
                                                            :plant
                                                            (get-in app-resources [:plant-1 :id])))
                                :user (:app-user-1 app-users)}
                               {:role (:role (rbac/get-role-by-name db nil "asset-1/manager"))
                                :context
                                (:context (rbac/get-context db nil
                                                            :asset
                                                            (get-in app-resources [:asset-1 :id])))
                                :user (:app-user-1 app-users)}])]
    (testing "app-user-1 has :application/manage permission on :application resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :application :id)
            context-type :application
            permission-name :application/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 has :organization/manage permission on :organization-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :organization-1 :id)
            context-type :organization
            permission-name :organization/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 doesn't have :application/manage permission on :organization-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :organization-1 :id)
            context-type :organization
            permission-name :application/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission false))))

    (testing "app-user-1 has :plant/manage permission on :plant-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :plant-1 :id)
            context-type :plant
            permission-name :plant/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 doesn't have :application/manage permission on :plant-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :plant-1 :id)
            context-type :plant
            permission-name :application/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission false))))

    (testing "app-user-1 has :asset/manage permission on :plant-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :plant-1 :id)
            context-type :plant
            permission-name :asset/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission true))))

    (testing "app-user-1 doesn't have :asset/manage permission on :asset-1 resource"
      (let [user-id (-> app-users :app-user-1 :id)
            resource-id (-> app-resources :asset-1 :id)
            context-type :asset
            permission-name :asset/manage
            has-permission (rbac/has-permission db nil user-id resource-id context-type permission-name)]
        (is (= has-permission false))))))

(comment
  ;; TODO: Create all the individual unit tests by leveraging the example code below.

  ;; -----------------------------------------------------
  (rbac/create-roles! db nil roles)
  (rbac/create-role! db nil (first roles))
  (rbac/get-roles db nil)
  (rbac/get-role-by-id db nil
                       (-> (rbac/get-role-by-name db nil "application/manager")
                           :role
                           :id))
  (rbac/get-role-by-name db nil "application/manager")
  (rbac/update-role! db nil
                     (-> (rbac/get-role-by-name db nil "application/manager")
                         :role
                         (assoc :name "application/manager")))
  (rbac/update-roles! db nil
                      [(-> (rbac/get-role-by-name db nil "application/manager")
                           :role
                           (assoc :name "application/manager"))
                       (-> (rbac/get-role-by-name db nil "application/manager")
                           :role
                           (assoc :name "application/manager"))])
  (rbac/delete-role! db nil
                     (-> (rbac/get-role-by-name db nil "application/manager")
                         :role))
  (rbac/delete-role-by-id! db nil
                           (-> (rbac/get-role-by-name db nil "organization/manager")
                               :role
                               :id))
  (rbac/delete-role-by-name! db nil "asset/manager")
  (rbac/delete-roles! db nil
                      [(-> (rbac/get-role-by-name db nil "plant/manager")
                           :role)
                       (-> (rbac/get-role-by-name db nil "application/manager")
                           :role)])
  (rbac/delete-roles-by-id! db nil
                            [(-> (rbac/get-role-by-name db nil "plant/manager")
                                 :role
                                 :id)
                             (-> (rbac/get-role-by-name db nil "application/manager")
                                 :role
                                 :id)])
  (rbac/delete-roles-by-name! db nil ["organization/manager" "asset/manager"])

  ;; -----------------------------------------------------
  (rbac/create-context-types! db nil (vals context-types))
  (rbac/create-context-type! db nil (:application context-types))
  (rbac/get-context-types db nil)
  (rbac/get-context-type db nil :application)
  (rbac/get-context-type db nil :asset)
  (rbac/update-context-type! db nil (-> (rbac/get-context-type db nil :application)
                                        :context-type
                                        (assoc :description "Some updated description")))
  (rbac/update-context-types! db nil
                              [(-> (rbac/get-context-type db nil :application)
                                   :context-type
                                   (assoc :description "Some updated description for application"))
                               (-> (rbac/get-context-type db nil :organization)
                                   :context-type
                                   (assoc :description "Some updated description for organization"))])
  (rbac/delete-context-type! db nil
                             (-> (rbac/get-context-type db nil :organization)
                                 :context-type))
  (rbac/delete-context-types! db nil
                              [(-> (rbac/get-context-type db nil :application)
                                   :context-type)
                               (-> (rbac/get-context-type db nil :organization)
                                   :context-type)])

  ;; -----------------------------------------------------
  (let [application-ctx (:context (rbac/create-context! db nil application-context nil))
        organization-1-ctx (:context (rbac/create-context! db nil organization-1-context application-ctx))
        organization-2-ctx (:context (rbac/create-context! db nil organization-2-context application-ctx))
        plant-1-ctx (:context (rbac/create-context! db nil plant-1-context organization-1-ctx))
        plant-2-ctx (:context (rbac/create-context! db nil plant-2-context organization-2-ctx))
        asset-1-ctx (:context (rbac/create-context! db nil asset-1-context plant-1-ctx))
        asset-2-ctx (:context (rbac/create-context! db nil asset-2-context plant-2-ctx))]
    [application-ctx
     organization-1-ctx
     organization-2-ctx
     plant-1-ctx
     plant-2-ctx
     asset-1-ctx
     asset-2-ctx])
  (rbac/get-contexts db nil)
  (rbac/get-context db nil :application (get-in app-resources [:application :id]))
  (rbac/get-context db nil :asset (get-in app-resources [:asset-1 :id]))
  (rbac/update-context! db nil (-> (rbac/get-context db nil :application (get-in app-resources [:asset-1 :id]))
                                   :context
                                   (assoc :context-type :asset)))
  (rbac/update-contexts! db nil
                         [(-> (rbac/get-context db nil :asset (get-in app-resources [:application :id]))
                              :context
                              (assoc :context-type :application))
                          (-> (rbac/get-context db nil :asset (get-in app-resources [:asset-1 :id]))
                              :context
                              (assoc :context-type :application))])
  (rbac/delete-context! db nil
                        (-> (rbac/get-context db nil :application (get-in app-resources [:application :id]))
                            :context))
  (rbac/delete-contexts! db nil
                         [(-> (rbac/get-context db nil :application (get-in app-resources [:application :id]))
                              :context)
                          (-> (rbac/get-context db nil :asset (get-in app-resources [:asset-1 :id]))
                              :context)])

  ;; -----------------------------------------------------
  (rbac/create-permissions! db nil permissions)
  (rbac/create-permission! db nil (first permissions))
  (rbac/get-permissions db nil)
  (rbac/get-permission-by-id db nil
                             (-> (rbac/get-permission-by-name db nil :application/manage-settings)
                                 :permission
                                 :id))
  (rbac/get-permission-by-name db nil :application/manage-settings)
  (rbac/update-permission! db nil
                           (-> (rbac/get-permission-by-name db nil :application/manage-settings)
                               :permission
                               (assoc :name :application/manage-settingsXX)))
  (rbac/update-permissions! db nil
                            [(-> (rbac/get-permission-by-name db nil :application/manage-settingsXX)
                                 :permission
                                 (assoc :name :application/manage-settingsYY))
                             (-> (rbac/get-permission-by-name db nil :application/manage-settingsXX)
                                 :permission
                                 (assoc :name :application/manage-settings))])
  (rbac/delete-permission! db nil
                           (-> (rbac/get-permission-by-name db nil :application/manage-settings)
                               :permission))
  (rbac/delete-permissions! db nil
                            [(-> (rbac/get-permission-by-name db nil :application/manage-settings)
                                 :permission)
                             (-> (rbac/get-permission-by-name db nil :organization/manage-settings)
                                 :permission)])

  ;; -----------------------------------------------------
  (rbac/add-super-admin db nil (get-in app-users [:app-user-1 :id]))
  (rbac/super-admin? db nil (get-in app-users [:app-user-1 :id]))
  (rbac/super-admin? db nil (get-in app-users [:app-user-2 :id]))
  (rbac/remove-super-admin db nil (get-in app-users [:app-user-1 :id]))
  (rbac/remove-super-admin db nil (get-in app-users [:app-user-2 :id]))

  ;; -----------------------------------------------------
  (rbac/grant-role-permission db nil
                              (:role (rbac/get-role-by-name db nil "organization/manager"))
                              (-> (rbac/get-permission-by-name db nil :organization/manage-settings)
                                  :permission))
  (rbac/remove-role-permission db nil
                               (:role (rbac/get-role-by-name db nil "organization/manager"))
                               (-> (rbac/get-permission-by-name db nil :organization/manage-settings)
                                   :permission))
  (rbac/deny-role-permission db nil
                             (:role (rbac/get-role-by-name db nil "organization/manager"))
                             (-> (rbac/get-permission-by-name db nil :organization/manage-settings)
                                 :permission))
  (rbac/remove-role-permission db nil
                               (:role (rbac/get-role-by-name db nil "organization/manager"))
                               (-> (rbac/get-permission-by-name db nil :organization/manage-settings)
                                   :permission))

  ;; -----------------------------------------------------
  (rbac/assign-roles! db nil
                      [{:role (:role (rbac/get-role-by-name db nil "application/manager"))
                        :context
                        (:context (rbac/get-context db nil
                                                    :application
                                                    (get-in app-resources [:application :id])))
                        :user (:app-user-1 app-users)}
                       {:role (:role (rbac/get-role-by-name db nil "organization/manager"))
                        :context (:context (rbac/get-context db nil
                                                             :organization
                                                             (get-in app-resources [:organization-2 :id])))
                        :user (:app-user-2 app-users)}])
  (rbac/assign-role! db nil
                     {:role (:role (rbac/get-role-by-name db nil "application/manager"))
                      :context (:context (rbac/get-context db nil
                                                           :application
                                                           (get-in app-resources [:application :id])))
                      :user (:app-user-1 app-users)})
  (rbac/unassign-role! db nil
                       {:role (:role (rbac/get-role-by-name db nil "application/manager"))
                        :context (:context (rbac/get-context db nil
                                                             :application
                                                             (get-in app-resources [:application :id])))
                        :user (:app-user-1 app-users)})
  (rbac/unassign-roles! db nil
                        [{:role (:role (rbac/get-role-by-name db nil "application/manager"))
                          :context
                          (:context (rbac/get-context db nil
                                                      :application
                                                      (get-in app-resources [:application :id])))
                          :user (:app-user-1 app-users)}
                         {:role (:role (rbac/get-role-by-name db nil "organization/manager"))
                          :context (:context (rbac/get-context db nil
                                                               :organization
                                                               (get-in app-resources [:organization-2 :id])))
                          :user (:app-user-2 app-users)}]))

