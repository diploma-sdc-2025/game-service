-- Create Roles (once per database)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_read') THEN
CREATE ROLE app_read NOLOGIN;
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_write') THEN
CREATE ROLE app_write NOLOGIN;
END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin') THEN
CREATE ROLE admin NOLOGIN;
END IF;
END$$;

--  Create Application User (from env variables)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${APP_USER}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER', '${APP_USER}', '${APP_PASSWORD}');
END IF;
END$$;

-- Grant app roles to the application user
DO $$
BEGIN
EXECUTE format('GRANT app_read, app_write TO %I', '${APP_USER}');
END$$;

-- Schema-level Privileges
-- Grant access to existing tables
GRANT USAGE ON SCHEMA public TO app_read, app_write, admin;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_read;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_write;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO admin;

-- Automatically grant privileges to future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT ON TABLES TO app_read;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_write;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL PRIVILEGES ON TABLES TO admin;
