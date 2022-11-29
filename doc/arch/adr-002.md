# Flexibilization of User and Resource id types

## Status - Accepted

## Context
Until now, it was required to use `UUID` as the type for the columns highlighted in magenta color in the schema:

![Database schema](./database_schema_adr-002.svg "Database schema")

However, the users table and the different resource entities tables are application domain-specific, and not part of the tables dictated by the library. Imposing that restriction could be a problem if those tables used other data types for their primary keys (e.g., `SERIAL`, `INTEGER`, `VARCHAR`, etc).

## Decision

1. The `user_id` column (in both the `rbac_role_assignment` and `rbac_super_admin` tables) and the `resource_id` column (in `rbac_context` table) can now be either a `UUID`, an `Integer` (covering the `SERIAL` and `INTEGER` use cases) or a non-blank string (covering the `CHAR(N)`, `VARCHAR(N)`, `NCHAR(N)`, `NVARCHAR(N)` and `TEXT` cases).

## Consequences

* If a library user needs to use an alternative type for the `user_id` or `resource_id` columns, because its application users and resource entities tables don't use the `UUID` type for their primary keys, the library user will need to make the corresponding change in the [proposed DB schema for the library](../../resources/dev.gethop.rbac/rbac-tables.pg.up.sql).
* As some code has been refactored to not depend on the `user_id` and `resource_id` columns having the `UUID` data type, the code no longer depends on PostgreSQL-specific features. So any other SQL-compliant database can be used.

## Notes
