# Data Base Schema

## Status - Accepted

## Context
The definitive schema that it will be used is represented in the next link:

![Database schema](./database_schema_adr-001.svg "Database schema")

In the definition of the schema, some decisions were made to adapt it to the proper context of this library.

## Decision

1. The `user` table won't exist as part of the database schema of the library. The `user_id` column in `rbac_role_assignment` and `rbac_super_admin` tables will store the values of the primary key of the existing application database "user table". As the name of the table is unknown to the library, the `user_id` column won't be defined as a foreign key.
2. If a user id exists in the `rbac_super_admin` table, further checks won't be performed and the permission will be considered as `granted`.
3. The `rbac_role_assignment` table will relate a user, with a role in a context.
4. The `rbac_context_type` table describes the different types of resources that will exist in an application. The values in that table are totally attached to the application domain and will be pre-defined by the application itself, not the library.
5. The `rbac_context` table will store each and every instance of the resources present in the application, for each type of `rbac_context_type`s. Hierarchical relationships between resources will be defined by the `parent_id` parameter.
6. The `rbac_role_permission` table will store the list of permissions granted or denied to a role.
7. When checking if a permission is granted or denied in a given context, the full hierarchy of the context will be used for the check. This means that once the permission is denied in a higher context in the hierarchy, it will be considered denied in the lower contexts automatically. That is, permission denial completely overrides permission grants.
8. The `rbac_permission` table relates permissions with context types. A permission will only make sense for a certain `rabc_context_type` and this relation will be defined in this table.
9. For now Optional/Advanced functionality won't be developed.

## Consequences
* The `user` table will have to exist in the application.
* The application domain will define the values of the context types, and this information will be stored into `rbac_context_type` table.
* Each time a new resource is created, updated or deleted in the application database, the corresponding `rbac_context` entry will need to be inserted, updated or deleted.

## Notes
