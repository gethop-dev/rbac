(ns magnet.rbac
  (:require [magnet.sql-utils :as sql-utils])
  (:import [java.util UUID]))

(defn- kw->str
  [k]
  (str (symbol k)))

(defn- str->kw
  [s]
  (keyword s))

(defn- get-*
  [db logger table vals-kw]
  (let [query [(format "SELECT *
                        FROM %s" table)]
        {:keys [success? return-values]} (sql-utils/sql-query db logger query)]
    (if success?
      {:success? true vals-kw return-values}
      {:success? false})))

(defn- get-x-by-y!
  [db logger table condition & params]
  (let [query (cons (format "SELECT *
                        FROM %s
                        WHERE %s" table condition)
                    params)
        {:keys [success? return-values]} (sql-utils/sql-query db logger query)]
    (if (and success? (> (count return-values) 0))
      {:success? true :values return-values}
      {:success? false})))

(defn- delete-x-where-y!
  [db logger table where-clause]
  (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db logger
                                                                 table
                                                                 where-clause)]
    (if (and success? (> deleted-values 0))
      {:success? true}
      {:success? false})))

;; -----------------------------------------------------------
(defn create-roles!
  [db logger roles]
  (let [roles (map #(assoc % :id (UUID/randomUUID)) roles)
        insert-keys (keys (first roles))
        insert-values (map vals roles)
        {:keys [success? inserted-values]} (sql-utils/sql-insert-multi! db logger
                                                                        :rbac-role
                                                                        insert-keys
                                                                        insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :roles roles}
      {:success? false})))

(defn create-role!
  [db logger role]
  (create-roles! db logger [role]))

(defn get-roles
  [db logger]
  (get-* db logger "rbac_role" :roles))

(defn- get-role-by-*
  [db logger column value]
  (let [{:keys [success? values]} (get-x-by-y! db logger "rbac_role"
                                               (format "%s = ?" (kw->str column))
                                               value)]
    {:success? success?
     :role (first values)}))

(defn get-role-by-id
  [db logger role-id]
  (get-role-by-* db logger :id role-id))

(defn get-role-by-name
  [db logger name]
  (get-role-by-* db logger :name name))

(defn update-role!
  [db logger role]
  (let [{:keys [success? processed-values]} (sql-utils/sql-update! db logger
                                                                   :rbac-role
                                                                   role
                                                                   ["id = ?" (:id role)])]
    (if (and success? (> processed-values 0))
      {:success? true :role role}
      {:success? false})))

(defn update-roles!
  [db logger roles]
  (doall (map #(update-role! db logger %) roles)))

(defn delete-role!
  [db logger role]
  (delete-x-where-y! db logger :rbac-role ["id = ?" (:id role)]))

(defn delete-role-by-id!
  [db logger role-id]
  (delete-x-where-y! db logger :rbac-role ["id = ?" role-id]))

(defn delete-role-by-name!
  [db logger name]
  (delete-x-where-y! db logger :rbac-role ["name = ?" name]))

(defn delete-roles!
  [db logger roles]
  (doall (map #(delete-role-by-id! db logger (:id %)) roles)))

(defn delete-roles-by-id!
  [db logger role-ids]
  (doall (map #(delete-role-by-id! db logger %) role-ids)))

(defn delete-roles-by-name!
  [db logger names]
  (doall (map #(delete-role-by-name! db logger %) names)))

;; -----------------------------------------------------------
(defn- context-type->db-context-type
  [context-type]
  (-> context-type
      (update :context-type kw->str)))

(defn create-context-types!
  [db logger context-types]
  (let [insert-keys (keys (first context-types))
        insert-values (map (fn [m]
                             (-> m
                                 context-type->db-context-type
                                 vals))
                           context-types)
        {:keys [success? inserted-values]} (sql-utils/sql-insert-multi! db logger
                                                                        :rbac-context-type
                                                                        insert-keys
                                                                        insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :context-types context-types}
      {:success? false})))

(defn create-context-type!
  [db logger context-type]
  (create-context-types! db logger [context-type]))

(defn- db-context-type->context-type
  [db-context-type]
  (-> db-context-type
      (update :context-type-type str->kw)))

(defn get-context-types
  [db logger]
  (let [return (get-* db logger "rbac_context_type" :context-types)]
    (update return :context-types #(map db-context-type->context-type %))))

(defn get-context-type
  [db logger context-type]
  (let [{:keys [success? values]}
        (get-x-by-y! db logger "rbac_context_type"
                     "context_type = ?"
                     (kw->str context-type))]
    {:success? success?
     :context-type (first values)}))

(defn update-context-type!
  [db logger context-type]
  (let [context-type (context-type->db-context-type context-type)
        {:keys [success? processed-values]} (sql-utils/sql-update! db logger
                                                                   :rbac-context-type
                                                                   context-type
                                                                   ["context_type = ?" (:context-type context-type)])]
    (if (and success? (> processed-values 0))
      {:success? true :context-type context-type}
      {:success? false})))

(defn update-context-types!
  [db logger context-types]
  (doall (map #(update-context-type! db logger %) context-types)))

(defn delete-context-type!
  [db logger {:keys [context-type]}]
  (delete-x-where-y! db logger :rbac-context-type
                     ["context_type = ?"
                      context-type]))

(defn delete-context-types!
  [db logger context-types]
  (doall (map #(delete-context-type! db logger %) context-types)))

;; -----------------------------------------------------------
(defn- context->db-context
  [context]
  (-> context
      (update :context-type kw->str)
      (update :parents #(sql-utils/coll->jdbc-array % "uuid"))))

(defn create-context!
  ;; To be able to create the top-level context, pass `nil` for PARENT-CONTEXT
  [db logger {:keys [context-type resource-id] :as context} parent-context]
  (let [parents (cond
                  (nil? parent-context) []
                  :else  (conj (:parents parent-context) (:id parent-context)))
        context (-> context
                    (assoc :id (UUID/randomUUID))
                    (assoc :parents parents))
        insert-keys (keys context)
        insert-values (-> context
                          context->db-context
                          vals)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db logger
                                                                  :rbac-context
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :context context}
      {:success? false})))

(defn- db-context->context
  [db-context]
  (-> db-context
      (update :context-type str->kw)))

(defn get-contexts
  [db logger]
  (let [return (get-* db logger "rbac_context" :contexts)]
    (update return :contexts #(map db-context->context %))))

(defn get-context
  [db logger context-type resource-id]
  (let [{:keys [success? values]}
        (get-x-by-y! db logger "rbac_context"
                     "context_type = ? AND resource_id = ?"
                     (kw->str context-type)
                     resource-id)]
    {:success? success?
     :context (first values)}))

(defn update-context!
  [db logger context]
  (let [{:keys [success? processed-values]} (sql-utils/sql-update! db logger
                                                                   :rbac-context
                                                                   (context->db-context context)
                                                                   ["id = ?" (:id context)])]
    (if (and success? (> processed-values 0))
      {:success? true :context context}
      {:success? false})))

(defn update-contexts!
  [db logger contexts]
  (doall (map #(update-context! db logger %) contexts)))

(defn delete-context!
  [db logger {:keys [context-type resource-id]}]
  (delete-x-where-y! db logger :rbac-context
                     ["context_type = ? AND resource_id = ?"
                      context-type
                      resource-id]))

(defn delete-contexts!
  [db logger contexts]
  (doall (map #(delete-context! db logger %) contexts)))

;; -----------------------------------------------------------
(defn- perm->db-perm
  [perm]
  (-> perm
      (update :name #(kw->str %))
      (update :context-type #(kw->str %))))

(defn create-permissions!
  [db logger permissions]
  (let [permissions (map (fn [perm]
                           (-> (assoc perm :id (UUID/randomUUID))
                               perm->db-perm))
                         permissions)
        insert-keys (keys (first permissions))
        insert-values (map vals permissions)
        {:keys [success? inserted-values]} (sql-utils/sql-insert-multi! db logger
                                                                        :rbac-permission
                                                                        insert-keys
                                                                        insert-values)]
    (if (and success? (> inserted-values 0))
      {::success? true :permission permissions}
      {:success? false})))

(defn create-permission!
  [db logger permission]
  (create-permissions! db logger [permission]))

(defn- db-perm->perm
  [db-perm]
  (-> db-perm
      (update :name #(str->kw %))
      (update :context-type #(str->kw %))))

(defn get-permissions
  [db logger]
  (let [result (get-* db logger "rbac_permission" :permissions)]
    (if-not (:success? result)
      {:success? false}
      (update result :permissions #(map db-perm->perm %)))))

(defn- get-permission-by-*
  [db logger column value]
  (let [{:keys [success? values]} (get-x-by-y! db logger "rbac_permission"
                                               (format "%s = ?" (kw->str column))
                                               value)]
    {:success? success?
     :permission (first values)}))

(defn get-permission-by-id
  [db logger id]
  (get-permission-by-* db logger :id id))

(defn get-permission-by-name
  [db logger name]
  (get-permission-by-* db logger :name (kw->str name)))

(defn update-permission!
  [db logger permission]
  (let [{:keys [success? processed-values]} (sql-utils/sql-update! db logger
                                                                   :rbac-permission
                                                                   (perm->db-perm permission)
                                                                   ["id = ?" (:id permission)])]
    (if (and success? (> processed-values 0))
      {:success? true :permission permission}
      {:success? false})))

(defn update-permissions!
  [db logger permissions]
  (doall (map #(update-permission! db logger %) permissions)))

(defn delete-permission!
  [db logger permission]
  (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db logger
                                                                 :rbac-permission
                                                                 ["id = ?" (:id permission)])]
    (if (and success? (> deleted-values 0))
      {:success? true}
      {:success? false})))

(defn delete-permissions!
  [db logger permissions]
  (doall (map #(delete-permission! db logger %) permissions)))

;; -----------------------------------------------------------
(defn add-super-admin
  [db logger user-id]
  (let [insert-keys [:user-id]
        insert-values [user-id]
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db logger
                                                                  :rbac-super-admin
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :super-admin user-id}
      {:success? false})))

(defn super-admin?
  [db logger user-id]
  (let [{:keys [success? return-values]} (sql-utils/sql-query db logger ["SELECT *
                                                                          FROM rbac_super_admin
                                                                          WHERE user_id = ?" user-id])]
    (if (and success? (> (count return-values) 0))
      {:success? true :super-admin? true}
      (if success?
        {:success? true :super-admin? false}))))

(defn remove-super-admin
  [db logger user-id]
  (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db logger
                                                                 :rbac-super-admin
                                                                 ["user_id = ?" user-id])]
    (if (and success? (> deleted-values 0))
      {:success? true}
      {:success? false})))

;; -----------------------------------------------------------
(defn assign-roles!
  [db logger assignments]
  (let [insert-keys [:role-id :context-id :user-id]
        insert-values (map (fn [{:keys [user role context]}]
                             [(:id role) (:id context) (:id user)])
                           assignments)
        {:keys [success? inserted-values]} (sql-utils/sql-insert-multi! db logger
                                                                        :rbac-role-assignment
                                                                        insert-keys
                                                                        insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true}
      {:success? false})))

(defn assign-role!
  [db logger assignment]
  (assign-roles! db logger [assignment]))

(defn unassign-role!
  [db logger {:keys [role context user] :as unassignment}]
  (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db logger
                                                                 :rbac-role-assignment
                                                                 ["role_id = ? AND context_id = ? AND user_id = ?"
                                                                  (:id role)
                                                                  (:id context)
                                                                  (:id user)])]
    (if (and success? (> deleted-values 0))
      {:success? true}
      {:success? false})))

(defn unassign-roles!
  [db logger unassignments]
  (doall (map #(unassign-role! db logger %) unassignments)))

;; -----------------------------------------------------------
(defn- set-perm-with-value
  [db logger role permission permission-value]
  (let [perm-val (cond
                   (= :permission-granted permission-value) 1
                   (= :permission-denied permission-value) -1)
        role-permission {:role-id (:id role)
                         :permission-id (:id permission)
                         :permission-value perm-val}
        where-clause ["role_id = ? AND permission_id = ?"
                      (:id role)
                      (:id permission)]
        {:keys [success? processed-values]} (sql-utils/sql-update! db logger
                                                                   :rbac-role-permission
                                                                   role-permission
                                                                   where-clause)]
    (if (and success? (> processed-values 0))
      {:success? true}
      (let [ks (keys role-permission)
            vs (vals role-permission)
            {:keys [success? inserted-values]} (sql-utils/sql-insert! db logger
                                                                      :rbac-role-permission
                                                                      ks
                                                                      vs)]
        (if (and success? (> inserted-values 0))
          {:success? true}
          {:success? false})))))

(defn grant-role-permission
  [db logger role permission]
  (set-perm-with-value db logger role permission :permission-granted))

(defn grant-role-permissions
  [db logger role permissions]
  (doall (map #(set-perm-with-value db logger role % :permission-granted) permissions)))

(defn deny-role-permission
  [db logger role permission]
  (set-perm-with-value db logger role permission :permission-denied))

(defn deny-role-permissions
  [db logger role permissions]
  (doall (map #(set-perm-with-value db logger role % :permission-denied) permissions)))

(defn remove-role-permission
  [db logger role permission]
  (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db logger
                                                                 :rbac-role-permission
                                                                 ["role_id = ? AND permission_id = ?"
                                                                  (:id role)
                                                                  (:id permission)])]
    (if (and success? (> deleted-values 0))
      {:success? true}
      {:success? false})))

(defn remove-role-permissions
  [db logger role permissions]
  (doall (map #(remove-role-permission db logger role %) permissions)))

;; -----------------------------------------------------------
(defn has-permission
  [db logger user-id resource-id context-type permission-name]
  (let [{:keys [success? super-admin?]} (super-admin? db nil user-id)]
    (if (and success? super-admin?)
      true
      (let [query "SELECT
                       rp.name as permission_name,
                       res.name,
                       rrp.permission_value,
                       rc.id as context_id,
                       rc.parents as context_parents
                   FROM
                       rbac_context rc
                   INNER JOIN
                       resource res ON rc.resource_id = res.id
                   INNER JOIN
                       rbac_role_assignment rra ON rc.id = rra.context_id
                   INNER JOIN
                       rbac_role rr ON rr.id = rra.role_id
                   INNER JOIN
                       rbac_role_permission rrp ON rrp.role_id = rr.id
                   INNER JOIN
                       rbac_permission rp ON rp.id = rrp.permission_id
                   WHERE
                       rra.user_id = ?
                       AND
                       rp.name = ?
                       AND
                       rra.context_id = ANY (SELECT
                                                 rc.id
                                             FROM
                                                 rbac_context rc
                                             WHERE
                                                 (rc.resource_id = ? AND rc.context_type = ?)
                                                 OR
                                                 rc.id = ANY (ARRAY (SELECT
                                                                         rc.parents
                                                                     FROM
                                                                         rbac_context rc
                                                                     WHERE
                                                                         rc.resource_id = ?
                                                                         AND
                                                                         rc.context_type = ?
                                                                     AND
                                                                         rc.parents <> '{}')))
                   ORDER BY rc.parents ASC;"
            {:keys [success? return-values]} (sql-utils/sql-query db nil
                                                                  [query
                                                                   user-id
                                                                   (kw->str permission-name)
                                                                   resource-id
                                                                   (kw->str context-type)
                                                                   resource-id
                                                                   (kw->str context-type)])]
        (cond
          (not success?) false
          (empty? return-values) false
          :else (not (some #{-1} (map :permission-value return-values))))))))
