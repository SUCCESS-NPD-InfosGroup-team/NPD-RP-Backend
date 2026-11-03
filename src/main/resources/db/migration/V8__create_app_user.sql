-- Users created through the platform (SCRUM-93). Keycloak keeps the account, password and role; this
-- table keeps what the user list shows, including the state and last access, tracked by the API.

CREATE TABLE app_user (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keycloak_id    uuid         NOT NULL,
    full_name      varchar(150) NOT NULL,
    email          varchar(254) NOT NULL CHECK (email = lower(email)),
    role           varchar(40)  NOT NULL
        CHECK (role IN ('operations-administrator', 'npd-leader', 'customer-success', 'guest')),
    state          varchar(20)  NOT NULL CHECK (state IN ('PENDING', 'ACTIVE', 'DISABLED')),
    last_access_at timestamptz,
    created_at     timestamptz  NOT NULL,
    created_by     varchar(150) NOT NULL,
    CONSTRAINT app_user_keycloak_id_uk UNIQUE (keycloak_id),
    CONSTRAINT app_user_email_uk UNIQUE (email)
);

COMMENT ON TABLE app_user IS 'Users created through the platform; the account itself lives in Keycloak';
COMMENT ON COLUMN app_user.state IS 'PENDING until the first API call with the user''s token, then ACTIVE';
COMMENT ON COLUMN app_user.last_access_at IS 'Last API call of the user, updated at most every 5 minutes';
