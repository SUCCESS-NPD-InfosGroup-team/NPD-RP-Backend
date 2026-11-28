-- HU-09 (SCRUM-231), changed on 2026-11-27: a user is deleted at once instead of being deactivated and
-- deleted one day later, so the deactivated state goes away (docs/specs/SCRUM-231/design.md §3).

-- Users still deactivated are deleted, keeping their name, email and role in the audit log as a deletion
-- through the API does. Their Keycloak accounts stay disabled and are deleted by hand.
INSERT INTO audit_log (entity_type, entity_id, action, field, old_value, new_value, actor)
SELECT 'app_user', u.id, 'DELETE', v.field, v.old_value, NULL, 'system'
FROM app_user u
CROSS JOIN LATERAL (VALUES (1, 'fullName', u.full_name), (2, 'email', u.email), (3, 'role', u.role))
    AS v (position, field, old_value)
WHERE u.state = 'DISABLED'
ORDER BY u.id, v.position;

DELETE FROM app_user WHERE state = 'DISABLED';

ALTER TABLE app_user DROP CONSTRAINT app_user_disabled_at_ck;
ALTER TABLE app_user DROP COLUMN disabled_at;

ALTER TABLE app_user DROP CONSTRAINT app_user_state_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_state_check CHECK (state IN ('PENDING', 'ACTIVE'));

COMMENT ON COLUMN app_user.state IS 'PENDING until the first API call with the user''s token, then ACTIVE';
