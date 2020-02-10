CREATE TABLE IF NOT EXISTS appuser (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL);
-- ;;
CREATE TABLE IF NOT EXISTS resource (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT);
-- ;;
