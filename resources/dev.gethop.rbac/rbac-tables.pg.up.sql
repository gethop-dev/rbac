CREATE TABLE IF NOT EXISTS rbac_context_type (
    name VARCHAR(127) PRIMARY KEY,
    description TEXT);
-- ;;
CREATE TABLE IF NOT EXISTS rbac_context (
    id uuid PRIMARY KEY,
    context_type_name VARCHAR(127) NOT NULL REFERENCES rbac_context_type(name) ON UPDATE CASCADE,
    resource_id uuid NOT NULL,
    parent uuid REFERENCES rbac_context(id) ON UPDATE CASCADE,
    UNIQUE(context_type_name, resource_id));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_role (
    id uuid PRIMARY KEY,
    name VARCHAR(127) NOT NULL,
    description TEXT,
    UNIQUE (name));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_role_assignment (
    role_id uuid NOT NULL REFERENCES rbac_role(id) ON UPDATE CASCADE,
    context_id uuid NOT NULL REFERENCES rbac_context(id) ON UPDATE CASCADE,
    user_id uuid NOT NULL,
    PRIMARY KEY (role_id, context_id, user_id));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_super_admin (
    user_id uuid PRIMARY KEY);
-- ;;
CREATE TABLE IF NOT EXISTS rbac_permission (
    id uuid PRIMARY KEY,
    name VARCHAR(127) NOT NULL,
    description TEXT,
    context_type_name VARCHAR(127) NOT NULL REFERENCES rbac_context_type(name) ON UPDATE CASCADE,
    UNIQUE (name));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_role_permission (
    role_id uuid REFERENCES rbac_role(id) ON UPDATE CASCADE,
    permission_id uuid NOT NULL REFERENCES rbac_permission(id) ON UPDATE CASCADE,
    permission_value SMALLINT NOT NULL,
    PRIMARY KEY (role_id, permission_id));
-- ;;
