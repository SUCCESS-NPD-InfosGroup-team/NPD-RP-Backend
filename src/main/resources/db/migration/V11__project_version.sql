-- HU-14 (SCRUM-228): editing, deactivating and reactivating a project. Two
-- administrators can open "Editar proyecto" at once, so the project gets an
-- optimistic-locking version like employee (V2). project_summary_v appends
-- the version and the last update so "Ver proyecto" can return them.
-- V10 is the NPD lead flag (SCRUM-241).

ALTER TABLE project ADD COLUMN version integer NOT NULL DEFAULT 0;
COMMENT ON COLUMN project.version IS 'Optimistic locking for concurrent edits';

-- Same columns as V4, in the same order, plus version and updated_at at the
-- end: CREATE OR REPLACE VIEW only allows appending columns.
CREATE OR REPLACE VIEW project_summary_v AS
SELECT p.id                    AS project_id,
       c.id                    AS client_id,
       c.name                  AS client,
       c.is_internal,
       p.name                  AS project,
       p.active,
       p.created_at::date      AS start_date,
       p.contract_end_date,
       count(e.id) FILTER (WHERE rs.code <> 'BAJA')                                           AS team_size,
       count(e.id) FILTER (WHERE rs.code <> 'BAJA' AND e.project_end_date < current_date)     AS pending_confirmation,
       min(e.project_end_date) FILTER (WHERE rs.code <> 'BAJA' AND e.project_end_date >= current_date) AS next_assignment_end,
       count(e.id) FILTER (WHERE rs.code <> 'BAJA' AND e.project_end_date IS NULL)             AS indefinite_count,
       p.version,
       p.updated_at
FROM project p
JOIN client c                     ON c.id = p.client_id
LEFT JOIN employee e              ON e.project_id = p.id
LEFT JOIN resource_status rs      ON rs.id = e.status_id
GROUP BY p.id, c.id;

COMMENT ON COLUMN project_summary_v.version IS 'project.version, sent back by the edit, deactivation and reactivation';
