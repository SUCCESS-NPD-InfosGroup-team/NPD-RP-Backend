-- Audit log: who changed what and when. One row per field, so the creation of
-- a record stores its initial values and an edit only the fields that changed
-- (previous and new value). Rows are only inserted, never updated or deleted.

CREATE TABLE audit_log (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type varchar(40)  NOT NULL,
    entity_id   bigint       NOT NULL,
    action      varchar(20)  NOT NULL CHECK (action IN ('CREATE', 'UPDATE')),
    field       varchar(100) NOT NULL,
    old_value   text,
    new_value   text,
    actor       varchar(150) NOT NULL,
    occurred_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, occurred_at);

COMMENT ON TABLE audit_log IS 'Change history of the records edited through the API, one row per field';
COMMENT ON COLUMN audit_log.entity_type IS 'Audited table, for example employee';
COMMENT ON COLUMN audit_log.field IS 'Field as the API names it, for example assignment.project';
COMMENT ON COLUMN audit_log.old_value IS 'Value before the change, as the user sees it; NULL on CREATE';
COMMENT ON COLUMN audit_log.new_value IS 'Value after the change, as the user sees it';
COMMENT ON COLUMN audit_log.actor IS 'preferred_username of the Keycloak token';
