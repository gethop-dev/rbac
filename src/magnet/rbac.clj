(ns magnet.rbac
  (:require [magnet.sql-utils :as sql-utils]
            [clojure.spec.alpha :as s]
            [duct.logger :as logger])
  (:import [java.util UUID]))

(defn- kw->str
  [k]
  (str (symbol k)))

(defn- str->kw
  [s]
  (keyword s))

(defn- get-*
  [db-spec logger table vals-kw]
  (let [query [(format "SELECT *
                        FROM %s" table)]
        {:keys [success? return-values]} (sql-utils/sql-query db-spec logger query)]
    (if success?
      {:success? true vals-kw return-values}
      {:success? false})))

(defn- get-x-by-y
  [db-spec logger table condition & params]
  (let [query (cons (format "SELECT *
                        FROM %s
                        WHERE %s" table condition)
                    params)
        {:keys [success? return-values]} (sql-utils/sql-query db-spec logger query)]
    (if (and success? (> (count return-values) 0))
      {:success? true :values return-values}
      {:success? false})))

(defn- delete-x-where-y!
  [db-spec logger table where-clause]
  (let [{:keys [success? deleted-values]} (sql-utils/sql-delete! db-spec logger
                                                                 table
                                                                 where-clause)]
    (if (and success? (> deleted-values 0))
      {:success? true}
      {:success? false})))

;; -----------------------------------------------------------
(defn- role->db-role
  [role]
  (-> role
      (update :name kw->str)))

(defn- db-role->role
  [db-role]
  (-> db-role
      (update :name str->kw)))

(s/def ::db-spec ::sql-utils/db-spec)
(s/def ::logger ::sql-utils/logger)
(s/def ::id uuid?)
(s/def ::name string?)
(s/def ::description string?)
(s/def ::role (s/keys :req-un [::name]
                      :opt-un [::id ::description]))
(s/def ::success? boolean)
(s/def ::create-role!-args (s/cat :db-spec ::db-spec
                                  :logger ::logger
                                  :role ::role))
(s/def ::create-role!-ret (s/keys :req-un [::success?]
                                  :opt-un [::role]))
(s/fdef create-role!
  :args ::create-role!-args
  :ret  ::create-role!-ret)

(defn create-role!
  [db-spec logger role]
  {:pre [(and (s/valid? ::db-spec db-spec)
              (s/valid? ::logger logger)
              (s/valid? ::role role))]}
  (let [db-role (-> role
                    (assoc :id (UUID/randomUUID))
                    (role->db-role))
        insert-keys (keys db-role)
        insert-values (vals db-role)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                  :rbac-role
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :role (db-role->role db-role)}
      {:success? false})))

(s/def ::roles (s/coll-of ::role))
(s/def ::create-roles!-args (s/cat :db-spec ::db-spec
                                   :logger ::logger
                                   :roles ::roles))
(s/def ::create-roles!-ret (s/coll-of ::create-role!-ret))
(s/fdef create-roles!
  :args ::create-roles!-args
  :ret  ::create-roles!-ret)

(defn create-roles!
  [db-spec logger roles]
  (doall (map #(create-role! db-spec logger %) roles)))

(s/def ::get-roles-args (s/cat :db-spec ::db-spec
                               :logger ::logger))
(s/def ::get-roles-ret (s/keys :req-un [::success?]
                               :opt-un [::roles]))
(s/fdef get-roles
  :args ::get-roles-args
  :ret  ::get-roles-ret)

(defn get-roles
  [db-spec logger]
  (let [return (get-* db-spec logger "rbac_role" :roles)]
    (update return :roles #(map db-role->role %))))

(defn- get-role-by-*
  [db-spec logger column value]
  (let [{:keys [success? values]} (get-x-by-y db-spec logger "rbac_role"
                                              (format "%s = ?" column)
                                              value)]
    {:success? success?
     :role (first (map db-role->role values))}))

(s/def ::get-role-by-id-args (s/cat :db-spec ::db-spec
                                    :logger ::logger
                                    :role-id ::id))
(s/def ::get-role-by-id-ret (s/keys :req-un [::success?]
                                    :opt-un [::role]))
(s/fdef get-role-by-id
  :args ::get-role-by-id-args
  :ret  ::get-role-by-id-ret)

(defn get-role-by-id
  [db-spec logger role-id]
  (get-role-by-* db-spec logger "id" role-id))

(s/def ::get-role-by-name-args (s/cat :db-spec ::db-spec
                                      :logger ::logger
                                      :name ::name))
(s/def ::get-role-by-name-ret (s/keys :req-un [::success?]
                                      :opt-un [::role]))
(s/fdef get-role-by-name
  :args ::get-role-by-name-args
  :ret  ::get-role-by-name-ret)

(defn get-role-by-name
  [db-spec logger name]
  (get-role-by-* db-spec logger "name" (kw->str name)))

(s/def ::update-role!-args (s/cat :db-spec ::db-spec
                                  :logger ::logger
                                  :role ::role))
(s/def ::update-role!-ret (s/keys :req-un [::success?]
                                  :opt-un [::role]))
(s/fdef update-role!
  :args ::update-role!-args
  :ret  ::update-role!-ret)

(defn update-role!
  [db-spec logger role]
  (let [db-role ()
        {:keys [success? processed-values]} (sql-utils/sql-update! db-spec logger
                                                                   :rbac-role
                                                                   (role->db-role role)
                                                                   ["id = ?" (:id role)])]
    (if (and success? (> processed-values 0))
      {:success? true :role role}
      {:success? false})))

(s/def ::update-roles!-args (s/cat :db-spec ::db-spec
                                   :logger ::logger
                                   :roles ::roles))
(s/def ::update-roles!-ret (s/keys :req-un [::success?]
                                   :opt-un [::roles]))
(s/fdef update-roles!
  :args ::update-roles!-args
  :ret  ::update-roles!-ret)

(defn update-roles!
  [db-spec logger roles]
  (doall (map #(update-role! db-spec logger %) roles)))

(s/def ::delete-role!-args (s/cat :db-spec ::db-spec
                                  :logger ::logger
                                  :role ::role))
(s/def ::delete-role!-ret (s/keys :req-un [::success?]))
(s/fdef delete-role!
  :args ::delete-role!-args
  :ret  ::delete-role!-ret)

(defn delete-role!
  [db-spec logger role]
  (delete-x-where-y! db-spec logger :rbac-role ["id = ?" (:id role)]))

(s/def ::delete-role-by-id!-args (s/cat :db-spec ::db-spec
                                        :logger ::logger
                                        :role-id ::id))
(s/def ::delete-role-by-id!-ret (s/keys :req-un [::success?]))
(s/fdef delete-role-by-id!
  :args ::delete-role-by-id!-args
  :ret  ::delete-role-by-id!-ret)

(defn delete-role-by-id!
  [db-spec logger role-id]
  (delete-x-where-y! db-spec logger :rbac-role ["id = ?" role-id]))

(s/def ::delete-role-by-name!-args (s/cat :db-spec ::db-spec
                                          :logger ::logger
                                          :name ::name))
(s/def ::delete-role-by-name!-ret (s/keys :req-un [::success?]))
(s/fdef delete-role-by-name!
  :args ::delete-role-by-name!-args
  :ret  ::delete-role-by-name!-ret)

(defn delete-role-by-name!
  [db-spec logger name]
  (delete-x-where-y! db-spec logger :rbac-role ["name = ?" name]))

(s/def ::delete-roles!-args (s/cat :db-spec ::db-spec
                                   :logger ::logger
                                   :roles ::roles))
(s/def ::delete-roles!-ret (s/keys :req-un [::success?]))
(s/fdef delete-roles!
  :args ::delete-roles!-args
  :ret  ::delete-roles!-ret)

(defn delete-roles!
  [db-spec logger roles]
  (doall (map #(delete-role-by-id! db-spec logger (:id %)) roles)))

(s/def ::delete-roles-by-id!-args (s/cat :db-spec ::db-spec
                                         :logger ::logger
                                         :role-ids (s/coll-of ::id)))
(s/def ::delete-roles-by-id!-ret (s/keys :req-un [::success?]))
(s/fdef delete-roles-by-id!
  :args ::delete-roles-by-id!-args
  :ret  ::delete-roles-by-id!-ret)

(defn delete-roles-by-id!
  [db-spec logger role-ids]
  (doall (map #(delete-role-by-id! db-spec logger %) role-ids)))

(s/def ::delete-roles-by-name!-args (s/cat :db-spec ::db-spec
                                           :logger ::logger
                                           :role-ids (s/coll-of ::id)))
(s/def ::delete-roles-by-name!-ret (s/keys :req-un [::success?]))
(s/fdef delete-roles-by-name!
  :args ::delete-roles-by-name!-args
  :ret  ::delete-roles-by-name!-ret)

(defn delete-roles-by-name!
  [db-spec logger names]
  (doall (map #(delete-role-by-name! db-spec logger %) names)))

;; -----------------------------------------------------------
(defn- context-type->db-context-type
  [context-type]
  (-> context-type
      (update :name kw->str)))

(defn- db-context-type->context-type
  [db-context-type]
  (-> db-context-type
      (update :name str->kw)))

(s/def ::context-type-name keyword?)
(s/def ::name ::context-type-name)
(s/def ::description string?)
(s/def ::context-type (s/keys :req-un [::name]
                              :opt-un [::description]))
(s/def ::create-context-type!-args (s/cat :db-spec ::db-spec
                                          :logger ::logger
                                          :context-type ::context-type))
(s/def ::create-context-type!-ret (s/keys :req-un [::success?]
                                          :opt-up [::context-type]))
(s/fdef create-context-type!
  :args ::create-context-type!-args
  :ret  ::create-context-type!-ret)

(defn create-context-type!
  [db-spec logger context-type]
  (let [insert-keys (keys context-type)
        insert-values (-> context-type
                          context-type->db-context-type
                          vals)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                  :rbac-context-type
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :context-type context-type}
      {:success? false})))

(s/def ::context-types (s/coll-of ::context-type))
(s/def ::create-context-types!-args (s/cat :db-spec ::db-spec
                                           :logger ::logger
                                           :context-types ::context-types))
(s/def ::create-context-types!-ret (s/coll-of ::create-context-type!-ret))
(s/fdef create-context-types!
  :args ::create-context-types!-args
  :ret  ::create-context-types!-ret)

(defn create-context-types!
  [db-spec logger context-types]
  (doall (map #(create-context-type! db-spec logger %) context-types)))

(s/def ::get-context-types-args (s/cat :db-spec ::db-spec
                                       :logger ::logger))
(s/def ::get-context-types-ret (s/keys :req-un [::success?]
                                       :opt-up [::context-types]))
(s/fdef get-context-types
  :args ::get-context-types-args
  :ret  ::get-context-types-ret)

(defn get-context-types
  [db-spec logger]
  (let [return (get-* db-spec logger "rbac_context_type" :context-types)]
    (update return :context-types #(map db-context-type->context-type %))))

(s/def ::get-context-type-args (s/cat :db-spec ::db-spec
                                      :logger ::logger
                                      :context-type-name ::context-type-name))
(s/def ::get-context-type-ret (s/keys :req-un [::success? ::context-type]))
(s/fdef get-context-type
  :args ::get-context-type-args
  :ret  ::get-context-type-ret)

(defn get-context-type
  [db-spec logger context-type-name]
  (let [{:keys [success? values]}
        (get-x-by-y db-spec logger "rbac_context_type"
                    "name = ?"
                    (kw->str context-type-name))]
    {:success? success?
     :context-type (first values)}))

(s/def ::update-context-type!-args (s/cat :db-spec ::db-spec
                                          :logger ::logger
                                          :context-type ::context-type))
(s/def ::update-context-type!-ret (s/keys :req-un [::success?]
                                          :opt-up [::context-type]))
(s/fdef update-context-type!
  :args ::update-context-type!-args
  :ret  ::update-context-type!-ret)

(defn update-context-type!
  [db-spec logger context-type]
  (let [context-type (context-type->db-context-type context-type)
        {:keys [success? processed-values]} (sql-utils/sql-update! db-spec logger
                                                                   :rbac-context-type
                                                                   context-type
                                                                   ["name = ?" (:name context-type)])]
    (if (and success? (> processed-values 0))
      {:success? true :context-type context-type}
      {:success? false})))

(s/def ::update-context-types!-args (s/cat :db-spec ::db-spec
                                           :logger ::logger
                                           :context-types ::context-types))
(s/def ::update-context-types!-ret (s/keys :req-un [::success?]
                                           :opt-up [::context-types]))
(s/fdef update-context-types!
  :args ::update-context-types!-args
  :ret  ::update-context-types!-ret)

(defn update-context-types!
  [db-spec logger context-types]
  (doall (map #(update-context-type! db-spec logger %) context-types)))

(s/def ::delete-context-type!-args (s/cat :db-spec ::db-spec
                                          :logger ::logger
                                          :context-type ::context-type))
(s/def ::delete-context-type!-ret (s/keys :req-un [::success?]))
(s/fdef delete-context-type!
  :args ::delete-context-type!-args
  :ret  ::delete-context-type!-ret)

(defn delete-context-type!
  [db-spec logger context-type]
  (delete-x-where-y! db-spec logger :rbac-context-type ["name = ?" (:name context-type)]))

(s/def ::delete-context-types!-args (s/cat :db-spec ::db-spec
                                           :logger ::logger
                                           :context-types ::context-types))
(s/def ::delete-context-types!-ret (s/keys :req-un [::success?]))
(s/fdef delete-context-types!
  :args ::delete-context-types!-args
  :ret  ::delete-context-types!-ret)

(defn delete-context-types!
  [db-spec logger context-types]
  (doall (map #(delete-context-type! db-spec logger %) context-types)))

;; -----------------------------------------------------------
(defn- context->db-context
  [context]
  (-> context
      (update :context-type-name kw->str)))

(defn- db-context->context
  [db-context]
  (-> db-context
      (update :context-type-name str->kw)))

(s/def ::resource-id uuid?)
(s/def ::parent-id ::id)
(s/def ::context (s/keys :req-un [::context-type-name
                                  ::resource-id]
                         :opt-un [::id
                                  ::parent-id]))
(s/def ::create-context!-args (s/cat :db-spec ::db-spec
                                     :logger ::logger
                                     :context ::context
                                     :parent-context (s/nilable ::context)))
(s/def ::create-context!-ret (s/keys :req-un [::success?]))
(s/fdef create-context!
  :args ::create-context!-args
  :ret  ::create-context!-ret)

(defn create-context!
  ;; To be able to create the top-level context, pass `nil` for PARENT-CONTEXT
  [db-spec logger {:keys [context-type-name resource-id] :as context} parent-context]
  (let [context (-> context
                    (assoc :id (UUID/randomUUID))
                    (assoc :parent (:id parent-context)))
        insert-keys (keys context)
        insert-values (-> context
                          context->db-context
                          vals)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                  :rbac-context
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :context context}
      {:success? false})))

(s/def ::contexts (s/coll-of ::context))
(s/def ::get-contexts-args (s/cat :db-spec ::db-spec
                                  :logger ::logger))
(s/def ::get-contexts-ret (s/keys :req-un [::success?
                                           ::contexts]))
(s/fdef get-contexts
  :args ::get-contexts-args
  :ret  ::get-contexts-ret)

(defn get-contexts
  [db-spec logger]
  (let [return (get-* db-spec logger "rbac_context" :contexts)]
    (update return :contexts #(map db-context->context %))))

(s/def ::get-context-args (s/cat :db-spec ::db-spec
                                 :logger ::logger
                                 :context-type-name ::context-type-name
                                 :resource-id ::resource-id))
(s/def ::get-context-ret (s/keys :req-un [::success?
                                          ::context]))
(s/fdef get-context
  :args ::get-context-args
  :ret  ::get-context-ret)

(defn get-context
  [db-spec logger context-type-name resource-id]
  (let [{:keys [success? values]}
        (get-x-by-y db-spec logger "rbac_context"
                    "context_type_name = ? AND resource_id = ?"
                    (kw->str context-type-name)
                    resource-id)]
    {:success? success?
     :context (first values)}))

(s/def ::update-context!-args (s/cat :db-spec ::db-spec
                                     :logger ::logger
                                     :context ::context))
(s/def ::update-context!-ret (s/keys :req-un [::success?
                                              ::context]))
(s/fdef update-context!
  :args ::update-context!-args
  :ret  ::update-context!-ret)

(defn update-context!
  [db-spec logger context]
  (let [{:keys [success? processed-values]} (sql-utils/sql-update! db-spec logger
                                                                   :rbac-context
                                                                   (context->db-context context)
                                                                   ["id = ?" (:id context)])]
    (if (and success? (> processed-values 0))
      {:success? true :context context}
      {:success? false})))

(s/def ::update-contexts!-args (s/cat :db-spec ::db-spec
                                      :logger ::logger
                                      :contexts ::contexts))
(s/def ::update-contexts!-ret (s/keys :req-un [::success?
                                               ::contexts]))
(s/fdef update-contexts!
  :args ::update-contexts!-args
  :ret  ::update-contexts!-ret)

(defn update-contexts!
  [db-spec logger contexts]
  (doall (map #(update-context! db-spec logger %) contexts)))

(s/def ::delete-context!-args (s/cat :db-spec ::db-spec
                                     :logger ::logger
                                     :context ::context))
(s/def ::delete-context!-ret (s/keys :req-un [::success?]))
(s/fdef delete-context!
  :args ::delete-context!-args
  :ret  ::delete-context!-ret)

(defn delete-context!
  [db-spec logger {:keys [context-type-name resource-id]}]
  (delete-x-where-y! db-spec logger :rbac-context
                     ["context_type_name = ? AND resource_id = ?"
                      context-type-name
                      resource-id]))

(s/def ::delete-contexts!-args (s/cat :db-spec ::db-spec
                                      :logger ::logger
                                      :contexts ::contexts))
(s/def ::delete-contexts!-ret (s/keys :req-un [::success?]))
(s/fdef delete-contexts!
  :args ::delete-contexts!-args
  :ret  ::delete-contexts!-ret)

(defn delete-contexts!
  [db-spec logger contexts]
  (doall (map #(delete-context! db-spec logger %) contexts)))

;; -----------------------------------------------------------
(defn- perm->db-perm
  [perm]
  (-> perm
      (update :name #(kw->str %))
      (update :context-type-name #(kw->str %))))

(defn- db-perm->perm
  [db-perm]
  (-> db-perm
      (update :name #(str->kw %))
      (update :context-type-name #(str->kw %))))

(s/def ::permission (s/keys :req-un [::name
                                     ::context-type-name]
                            :opt-un [::id
                                     ::description]))
(s/def ::create-permission!-args (s/cat :db-spec ::db-spec
                                        :logger ::logger
                                        :permission ::permission))
(s/def ::create-permission!-ret (s/keys :req-un [::success?]
                                        :opt-un [::permission]))
(s/fdef create-permission!
  :args ::create-permission!-args
  :ret  ::create-permission!-ret)

(defn create-permission!
  [db-spec logger permission]
  (let [permission (-> permission
                       (assoc :id (UUID/randomUUID))
                       perm->db-perm)
        insert-keys (keys permission)
        insert-values (vals permission)
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                  :rbac-permission
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {::success? true :permission permission}
      {:success? false})))

(s/def ::permissions (s/coll-of ::permission))
(s/def ::create-permission!-args (s/cat :db-spec ::db-spec
                                        :logger ::logger
                                        :permissions ::permission))
(s/def ::create-permission!-ret (s/keys :req-un [::success?]
                                        :opt-un [::permission]))
(s/fdef create-permission!
  :args ::create-permission!-args
  :ret  ::create-permission!-ret)

(defn create-permissions!
  [db-spec logger permissions]
  (doall (map #(create-permission! db-spec logger %) permissions)))

(defn get-permissions
  [db-spec logger]
  (let [result (get-* db-spec logger "rbac_permission" :permissions)]
    (if-not (:success? result)
      {:success? false}
      (update result :permissions #(map db-perm->perm %)))))

(defn- get-permission-by-*
  [db-spec logger column value]
  (let [{:keys [success? values]} (get-x-by-y db-spec logger "rbac_permission"
                                              (format "%s = ?" column)
                                              value)]
    {:success? success?
     :permission (first values)}))

(defn get-permission-by-id
  [db-spec logger id]
  (get-permission-by-* db-spec logger "id" id))

(defn get-permission-by-name
  [db-spec logger name]
  (get-permission-by-* db-spec logger "name" (kw->str name)))

(defn update-permission!
  [db-spec logger permission]
  (let [{:keys [success? processed-values]} (sql-utils/sql-update! db-spec logger
                                                                   :rbac-permission
                                                                   (perm->db-perm permission)
                                                                   ["id = ?" (:id permission)])]
    (if (and success? (> processed-values 0))
      {:success? true :permission permission}
      {:success? false})))

(defn update-permissions!
  [db-spec logger permissions]
  (doall (map #(update-permission! db-spec logger %) permissions)))

(defn delete-permission!
  [db-spec logger permission]
  (delete-x-where-y! db-spec logger :rbac-permission ["id = ?" (:id permission)]))

(defn delete-permission-by-id!
  [db-spec logger permission-id]
  (delete-x-where-y! db-spec logger :rbac-permission ["id = ?" permission-id]))

(defn delete-permission-by-name!
  [db-spec logger name]
  (delete-x-where-y! db-spec logger :rbac-permission ["name = ?" name]))

(defn delete-permissions!
  [db-spec logger permissions]
  (doall (map #(delete-permission! db-spec logger %) permissions)))

(defn delete-permissions-by-id!
  [db-spec logger permission-ids]
  (doall (map #(delete-permission-by-id! db-spec logger %) permission-ids)))

(defn delete-permissions-by-names!
  [db-spec logger names]
  (doall (map #(delete-permission-by-name! db-spec logger %) names)))

;; -----------------------------------------------------------
(defn add-super-admin!
  [db-spec logger user-id]
  (let [insert-keys [:user-id]
        insert-values [user-id]
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                  :rbac-super-admin
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true :super-admin user-id}
      {:success? false})))

(defn super-admin?
  [db-spec logger user-id]
  (let [{:keys [success? return-values]} (sql-utils/sql-query db-spec logger ["SELECT *
                                                                          FROM rbac_super_admin
                                                                          WHERE user_id = ?" user-id])]
    (if (and success? (> (count return-values) 0))
      {:success? true :super-admin? true}
      (if success?
        {:success? true :super-admin? false}))))

(defn remove-super-admin!
  [db-spec logger user-id]
  (delete-x-where-y! db-spec logger :rbac-super-admin ["user_id = ?" user-id]))

;; -----------------------------------------------------------
(defn assign-role!
  [db-spec logger {:keys [role context user] :as assignment}]
  (let [insert-keys [:role-id :context-id :user-id]
        insert-values [(:id role) (:id context) (:id user)]
        {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                  :rbac-role-assignment
                                                                  insert-keys
                                                                  insert-values)]
    (if (and success? (> inserted-values 0))
      {:success? true}
      {:success? false})))

(defn assign-roles!
  [db-spec logger assignments]
  (doall (map #(assign-role! db-spec logger %) assignments)))

(defn unassign-role!
  [db-spec logger {:keys [role context user]}]
  (delete-x-where-y! db-spec logger :rbac-role-assignment ["role_id = ? AND context_id = ? AND user_id = ?"
                                                           (:id role)
                                                           (:id context)
                                                           (:id user)]))

(defn unassign-roles!
  [db-spec logger unassignments]
  (doall (map #(unassign-role! db-spec logger %) unassignments)))

;; -----------------------------------------------------------
(defn- set-perm-with-value
  [db-spec logger role permission permission-value]
  (let [perm-val (cond
                   (= :permission-granted permission-value) 1
                   (= :permission-denied permission-value) -1)
        role-permission {:role-id (:id role)
                         :permission-id (:id permission)
                         :permission-value perm-val}
        where-clause ["role_id = ? AND permission_id = ?"
                      (:id role)
                      (:id permission)]
        {:keys [success? processed-values]} (sql-utils/sql-update! db-spec logger
                                                                   :rbac-role-permission
                                                                   role-permission
                                                                   where-clause)]
    (if (and success? (> processed-values 0))
      {:success? true}
      (let [ks (keys role-permission)
            vs (vals role-permission)
            {:keys [success? inserted-values]} (sql-utils/sql-insert! db-spec logger
                                                                      :rbac-role-permission
                                                                      ks
                                                                      vs)]
        (if (and success? (> inserted-values 0))
          {:success? true}
          {:success? false})))))

(defn grant-role-permission!
  [db-spec logger role permission]
  (set-perm-with-value db-spec logger role permission :permission-granted))

(defn grant-role-permissions!
  [db-spec logger role permissions]
  (doall (map #(set-perm-with-value db-spec logger role % :permission-granted) permissions)))

(defn deny-role-permission!
  [db-spec logger role permission]
  (set-perm-with-value db-spec logger role permission :permission-denied))

(defn deny-role-permissions!
  [db-spec logger role permissions]
  (doall (map #(set-perm-with-value db-spec logger role % :permission-denied) permissions)))

(defn remove-role-permission!
  [db-spec logger role permission]
  (delete-x-where-y! db-spec logger :rbac-role-permission ["role_id = ? AND permission_id = ?"
                                                           (:id role)
                                                           (:id permission)]))

(defn remove-role-permissions!
  [db-spec logger role permissions]
  (doall (map #(remove-role-permission! db-spec logger role %) permissions)))

;; -----------------------------------------------------------

(defn has-permission
  [db-spec logger user-id resource-id context-type permission-name]
  (let [;; WITH RECURSE construct Inspired by familiy tree example at
        ;; https://sqlite.org/lang_with.html
        query "WITH super_admin AS (
                       SELECT
                           count(user_id) > 0 as super_admin
                       FROM
                           rbac_super_admin
                       WHERE
                           user_id = ?
                   ), has_permission AS (
                       SELECT
                           true = EVERY (rrp.permission_value > 0) as has_permission
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
                                                     rc.id = ANY (WITH RECURSIVE
                                                                    parent_of(id, resource_id, context_type_name, parent) AS
                                                                      (SELECT
                                                                           id, resource_id, context_type_name, parent
                                                                       FROM
                                                                           rbac_context),
                                                                    ancestor_of_context(id, resource_id) AS
                                                                      (SELECT
                                                                          parent_of.parent, parent_of.resource_id, parent_of.context_type_name
                                                                       FROM
                                                                          parent_of
                                                                       WHERE
                                                                          (resource_id = ? AND context_type_name = ?)
                                                                       UNION ALL
                                                                       SELECT
                                                                          parent_of.parent, parent_of.resource_id, parent_of.context_type_name
                                                                       FROM
                                                                          parent_of
                                                                       JOIN
                                                                          ancestor_of_context USING (id))
                                                                   SELECT
                                                                       rbac_context.id
                                                                   FROM
                                                                       rbac_context, ancestor_of_context
                                                                   WHERE
                                                                       ancestor_of_context.id = rbac_context.id)
                                                     OR
                                                        (rc.resource_id = ? AND rc.context_type_name = ?))
                   )
               SELECT * FROM super_admin CROSS JOIN has_permission;"
        {:keys [success? return-values]} (sql-utils/sql-query db-spec nil
                                                              [query
                                                               user-id
                                                               user-id
                                                               (kw->str permission-name)
                                                               resource-id
                                                               (kw->str context-type)
                                                               resource-id
                                                               (kw->str context-type)])]
    (cond
      (not success?) false
      (empty? return-values) false
      :else (let [{:keys [super-admin has-permission]} (first return-values)]
              (or super-admin has-permission)))))
