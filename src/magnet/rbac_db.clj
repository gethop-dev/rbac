(ns magnet.rbac-db
  (:require [magnet.sql-utils :as sql-utils]))

(defn create-role
  [spec logger role-name role-descripcion]
  (let [insert-keys '(:name :desc)
        insert-values '(role-name role-descripcion)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! spec logger
                                                                  :rbac-role
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :role role-name}
      {:success? false})))

(defn get-roles
  [spec logger]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT * FROM rbac_role"])]
    (if (and success? (> (count return-values) 0))
      {:success? true :roles return-values}
      {:success? false})))

(defn get-role-by-name
  [spec logger role-name]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT * FROM rbac_role
                                                                            WHERE name = ?" role-name])]
    (if (and success? (= (count return-values) 1))
      {:success? true :role (first return-values)}
      {:success? false})))

(defn create-permission
  [spec logger permission]
  (let [insert-keys (keys permission)
        insert-values (vals permission)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! spec logger
                                                                  :rbac-permission
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :permission permission}
      {:success? false})))

(defn get-permissions
  [spec logger]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT * FROM rbac_permission"])]
    (if (and success? (> (count return-values) 0))
      {:success? true :permissions return-values}
      {:success? false})))

(defn get-permission-by-id
  [spec logger permission-id]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT * FROM rbac_permission
                                                                            WHERE id = ?" permission-id])]
    (if (and success? (= (count return-values) 1))
      {:success? true :permissions (first return-values)}
      {:success? false})))

(defn set-super-admin
  [spec logger user]
  (let [insert-keys (keys user)
        insert-values (vals user)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! spec logger
                                                                  :rbac_super_admin
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :super-admin (:id user)}
      {:success? false})))

(defn is-super-admin
  [spec logger user]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT * FROM rbac_super_admin
                                                                            WHERE super_id = ?" (:id user)])]
    (if (and success? (> return-values 0))
      {:success? true :super-admin true}
      (if success?
        {:success? true :super-admin false}))))

(defn set-role-assigment
  [spec logger role-assigment]
  (let [insert-keys (keys role-assigment)
        insert-values (keys role-assigment)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! spec logger
                                                                  :rbac_role_assigment
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true}
      {:success? false})))

(defn set-role-permission
  [spec logger role-permission]
  (let [insert-keys (keys role-permission)
        insert-values (keys role-permission)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! spec logger
                                                                  :rbac_role_permission
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true}
      {:success? false})))

(defn get-context
  [spec logger context-level resource-id]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT id
                                                                            FROM rbac_contect
                                                                            WHERE context_level = ?
                                                                            AND resource_id = ?"])]
    (if (and success? (= (count return-values) 1))
      {:success? true :context-id (first return-values)}
      (if success?
        {:success? true :context-id nil}
        {:success? false}))))

(defn has-parent
  [spec logger context-id]
  (let [{:keys [success? return-values]} (sql-utils/sql-query spec logger ["SELECT parent_id
                                                                            FROM rbac_context
                                                                            WHERE id = ?" context-id])]
    (if (and success? (= (count return-values) 1))
      {:success? true :parent-id (first return-values)}
      (if success?
        {:success? true :parent-id nil}
        {:success? false}))))
