-- HU-03: search and filter the roster. resource_v gets what the list needs to filter,
-- order and draw each row: probation, the client role id, where the timeline bar starts,
-- the group of the row and a text to search by name, number, project or client.
-- CREATE OR REPLACE VIEW only allows appending columns at the end, so the V4 columns stay
-- as they were.
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
       e.leader_id,
       e.on_probation,
       e.client_role_id,
       GREATEST(p.created_at::date, e.hire_date) AS assignment_start_date,
       CASE
           WHEN e.project_id IS NULL THEN 1
           WHEN NOT p.active          THEN 0
           ELSE 2
       END                     AS roster_group,
       translate(lower(concat_ws(' ', e.full_name, e.code, p.name, c.name)),
                 'áàäâéèëêíìïîóòöôúùüûñç', 'aaaaeeeeiiiioooouuuunc') AS search_text
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

COMMENT ON COLUMN resource_v.on_probation IS 'En periodo de prueba (employee.on_probation)';
COMMENT ON COLUMN resource_v.client_role_id IS 'Filter the roster by role in the client';
COMMENT ON COLUMN resource_v.assignment_start_date IS 'Start of the roster bar: the later of the project creation and the hire date';
COMMENT ON COLUMN resource_v.roster_group IS 'Order of the roster groups: 0 = inactive project ("No especificado", to fix), 1 = without project, 2 = active project';
COMMENT ON COLUMN resource_v.search_text IS 'Name, employee number, project and client in lower case without accents, for the roster search';
