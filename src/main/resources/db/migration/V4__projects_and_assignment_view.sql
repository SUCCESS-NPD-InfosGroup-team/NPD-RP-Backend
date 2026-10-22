-- One-project-per-person redesign (oct-2026): projects get a contract end, the
-- roster view exposes ids to group and link by, and a project summary view
-- feeds the projects list and detail.

-- BD-01: contract end with the client, distinct from each employee's
-- project_end_date
ALTER TABLE project ADD COLUMN contract_end_date date;
COMMENT ON COLUMN project.contract_end_date IS 'Contract end with the client; NULL = no date. Triggers the Sales alert at 30 and 15 days';
CREATE INDEX project_contract_end_idx ON project (contract_end_date) WHERE active;

-- BD-04: an employee may have no project (new hire, or removed from a project)
COMMENT ON COLUMN employee.project_id IS 'PROYECTO ASIGNADO (its client is CLIENTE); NULL = without project, allowed on register and when removed from a project';

-- BD-05: same columns as V2, in the same order, plus ids and project dates.
-- CREATE OR REPLACE VIEW only allows appending columns at the end.
CREATE OR REPLACE VIEW resource_v AS
SELECT e.id                    AS employee_id,
       c.name                  AS client,
       p.name                  AS project,
       l.full_name             AS npd_leader,
       l.code                  AS leader_code,
       e.code                  AS employee_code,
       e.full_name,
       jt.name                 AS job_title,
       jp.name                 AS job_position,
       cr.name                 AS client_role,
       e.level,
       e.hire_date,
       bu.code                 AS business_unit,
       g.name                  AS gender,
       ep.name                 AS equipment_provider,
       e.probation_end_date,
       e.vacation_month,
       e.project_end_date,
       date_trunc('month', e.project_end_date::timestamp)::date AS project_end_month,
       rs.code                 AS employment_status_code,
       rs.name                 AS employment_status,
       CASE
           WHEN e.project_id IS NULL             THEN 'SIN_PROYECTO'
           WHEN e.project_end_date < current_date THEN 'PENDIENTE_CONFIRMACION'
           ELSE 'ASIGNADO'
       END                     AS assignment_situation,
       tr.name                 AS termination_reason,
       e.termination_date,
       tv.name                 AS talent_review,
       e.email,
       e.client_contact_email,
       wm.name                 AS work_modality,
       e.project_id,
       p.client_id,
       p.active                AS project_active,
       p.created_at::date      AS project_start_date,
       p.contract_end_date,
       e.leader_id
FROM employee e
JOIN resource_status rs           ON rs.id = e.status_id
JOIN gender g                     ON g.id = e.gender_id
JOIN job_position jp              ON jp.id = e.job_position_id
LEFT JOIN project p               ON p.id = e.project_id
LEFT JOIN client c                ON c.id = p.client_id
LEFT JOIN employee l              ON l.id = e.leader_id
LEFT JOIN job_title jt            ON jt.id = e.job_title_id
LEFT JOIN client_role cr          ON cr.id = e.client_role_id
LEFT JOIN business_unit bu        ON bu.id = e.business_unit_id
LEFT JOIN equipment_provider ep   ON ep.id = e.equipment_provider_id
LEFT JOIN termination_reason tr   ON tr.id = e.termination_reason_id
LEFT JOIN talent_review tv        ON tv.id = e.talent_review_id
LEFT JOIN work_modality wm        ON wm.id = e.work_modality_id;

COMMENT ON COLUMN resource_v.project_id IS 'Group the roster by project and link to its detail (project names are only unique per client)';
COMMENT ON COLUMN resource_v.project_active IS 'false for the inactive "No especificado" project, which must be corrected';
COMMENT ON COLUMN resource_v.project_start_date IS 'Day the project was created in the platform; start of the roster bar';
COMMENT ON COLUMN resource_v.leader_id IS 'Filter an NPD lead''s collaborators by id instead of by name';

-- BD-06: one row per project for the projects list and detail. Employees on
-- Baja are not counted as team.
CREATE VIEW project_summary_v AS
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
       count(e.id) FILTER (WHERE rs.code <> 'BAJA' AND e.project_end_date IS NULL)             AS indefinite_count
FROM project p
JOIN client c                     ON c.id = p.client_id
LEFT JOIN employee e              ON e.project_id = p.id
LEFT JOIN resource_status rs      ON rs.id = e.status_id
GROUP BY p.id, c.id;

COMMENT ON VIEW project_summary_v IS 'Projects with team size and assignment dates, for the projects list and detail';
COMMENT ON COLUMN project_summary_v.team_size IS 'Employees on the project, Baja excluded; 0 = "Sin equipo"';
COMMENT ON COLUMN project_summary_v.pending_confirmation IS 'Team members whose project_end_date has passed ("N por confirmar")';
COMMENT ON COLUMN project_summary_v.next_assignment_end IS 'Earliest project_end_date still to come among the team';
COMMENT ON COLUMN project_summary_v.indefinite_count IS 'Team members without project_end_date (INDEFINIDO)';
