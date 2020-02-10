CREATE TABLE IF NOT EXISTS rbac_context_type (
    context_type VARCHAR(127) PRIMARY KEY,
    description TEXT);
-- ;;
CREATE TABLE IF NOT EXISTS rbac_context (
    id uuid PRIMARY KEY,
    context_type VARCHAR(127) REFERENCES rbac_context_type(context_type) ON UPDATE CASCADE,
    resource_id uuid NOT NULL,
    parents uuid[] NOT NULL,
    UNIQUE(context_type, resource_id));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_role (
    id uuid PRIMARY KEY,
    name VARCHAR(127) NOT NULL,
    description TEXT,
    UNIQUE (name));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_role_assignment (
    role_id uuid REFERENCES rbac_role(id) ON UPDATE CASCADE,
    context_id uuid REFERENCES rbac_context(id) ON UPDATE CASCADE,
    user_id uuid NOT NULL,
    PRIMARY KEY (context_id, role_id));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_super_admin (
    user_id uuid PRIMARY KEY);
-- ;;
CREATE TABLE IF NOT EXISTS rbac_permission (
    id uuid PRIMARY KEY,
    name VARCHAR(127) NOT NULL,
    description TEXT,
    context_type VARCHAR(127) REFERENCES rbac_context_type(context_type) ON UPDATE CASCADE,
    UNIQUE (name));
-- ;;
CREATE TABLE IF NOT EXISTS rbac_role_permission (
    role_id uuid REFERENCES rbac_role(id) ON UPDATE CASCADE,
    permission_id uuid REFERENCES rbac_permission(id) ON UPDATE CASCADE,
    permission_value SMALLINT,
    PRIMARY KEY (role_id, permission_id));
-- ;;
