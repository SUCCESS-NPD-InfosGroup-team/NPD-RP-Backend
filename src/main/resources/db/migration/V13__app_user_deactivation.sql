-- HU-09 (SCRUM-231): deactivate and reactivate a user account. A deactivated account is deleted one day
-- after its deactivation unless it is reactivated (docs/specs/SCRUM-231/design.md §5).

-- Moment of the deactivation; the deletion time is disabled_at + 1 day, computed by the API
ALTER TABLE app_user ADD COLUMN disabled_at timestamptz;
ALTER TABLE app_user ADD CONSTRAINT app_user_disabled_at_ck CHECK ((state = 'DISABLED') = (disabled_at IS NOT NULL));

-- The deletion of an account is audited too, keeping its name and email (CA-08)
ALTER TABLE audit_log DROP CONSTRAINT audit_log_action_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_action_check CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'));

COMMENT ON COLUMN app_user.state IS 'PENDING until the first API call with the user''s token, then ACTIVE; DISABLED while deactivated';
COMMENT ON COLUMN app_user.disabled_at IS 'Moment of the deactivation; the account is deleted one day later. NULL unless DISABLED';
