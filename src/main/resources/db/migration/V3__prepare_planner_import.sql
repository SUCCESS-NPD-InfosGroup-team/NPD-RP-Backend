-- Prepares the one-time import of the planner: "No especificado" values for
-- required fields that some planner rows leave empty. Those rows point to these
-- values instead of being rejected, until someone fills in the real data.
--
-- They are inactive so the application never offers them in a dropdown:
-- new records and edits must pick a real value. Existing records keep them
-- until they are corrected.

INSERT INTO work_modality (code, name, active) VALUES
    ('NO_ESPECIFICADO', 'No especificado', false);

INSERT INTO client (name, active) VALUES
    ('No especificado', false);

INSERT INTO project (client_id, name, active)
SELECT id, 'No especificado', false
FROM client
WHERE name = 'No especificado';
