-- One row per NPD collaborator. Besides the person's own data it holds their
-- current work on a project: everything in the planner except the client and
-- the project name changes from person to person within the same project.
-- Columns the client marked as required are NOT NULL only where every row of
-- the planner has them; the rest are enforced by the application.

CREATE TABLE employee (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Identity
    -- Text so non-numeric codes (SP11) fit; replacing a code touches no foreign key
    code                  varchar(20)  NOT NULL UNIQUE,
    full_name             varchar(150) NOT NULL,
    email                 varchar(254),
    gender_id             smallint     NOT NULL REFERENCES gender (id),

    -- Position at Infosgroup
    job_title_id          bigint       REFERENCES job_title (id),
    job_position_id       bigint       NOT NULL REFERENCES job_position (id),
    level                 smallint     CHECK (level > 0),
    business_unit_id      smallint     REFERENCES business_unit (id),
    work_modality_id      smallint     REFERENCES work_modality (id),

    -- Hiring
    hire_date             date         NOT NULL,
    probation_end_date    date GENERATED ALWAYS AS (hire_date + 90) STORED,
    vacation_month        smallint GENERATED ALWAYS AS
                              (extract(month FROM hire_date + interval '11 months')::smallint) STORED,

    -- Status
    status_id             smallint     NOT NULL REFERENCES resource_status (id),
    termination_reason_id smallint     REFERENCES termination_reason (id),
    termination_date      date,
    talent_review_id      smallint     REFERENCES talent_review (id),

    -- Current work on a project
    -- NULL = without project
    project_id            bigint       REFERENCES project (id),
    -- The NPD lead is another employee
    leader_id             bigint       REFERENCES employee (id),
    client_role_id        bigint       REFERENCES client_role (id),
    equipment_provider_id smallint     REFERENCES equipment_provider (id),
    client_contact_email  varchar(254),
    -- End of the employee's contract on the project; NULL = INDEFINIDO
    project_end_date      date,

    version               integer      NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT employee_termination_after_hire CHECK (termination_date >= hire_date),
    CONSTRAINT employee_not_own_leader CHECK (leader_id <> id)
);

CREATE UNIQUE INDEX employee_email_uk ON employee (lower(email));
CREATE INDEX employee_project_idx ON employee (project_id);
CREATE INDEX employee_leader_idx ON employee (leader_id);
CREATE INDEX employee_status_idx ON employee (status_id);
CREATE INDEX employee_probation_end_idx ON employee (probation_end_date);
CREATE INDEX employee_project_end_idx ON employee (project_end_date);

CREATE TRIGGER employee_set_updated_at BEFORE UPDATE ON employee
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE employee IS 'NPD collaborator and their current work on a project';
COMMENT ON COLUMN employee.code IS 'COD EMPL / COD LIDER';
COMMENT ON COLUMN employee.full_name IS 'NOMBRE DE EMPLEADO';
COMMENT ON COLUMN employee.email IS 'CORREO RECURSO (optional; unique when present)';
COMMENT ON COLUMN employee.gender_id IS 'GENERO';
COMMENT ON COLUMN employee.job_title_id IS 'PUESTO';
COMMENT ON COLUMN employee.job_position_id IS 'POSICIÓN EN INFOSGROUP';
COMMENT ON COLUMN employee.level IS 'NIVEL';
COMMENT ON COLUMN employee.business_unit_id IS 'UEN';
COMMENT ON COLUMN employee.work_modality_id IS 'MODALIDAD LABORAL DEL RECURSO (required by the application)';
COMMENT ON COLUMN employee.hire_date IS 'FECHA DE INICIO: hire date at Infosgroup';
COMMENT ON COLUMN employee.probation_end_date IS 'MES DE PROBATORIA: day 90 after hire_date, weekends included';
COMMENT ON COLUMN employee.vacation_month IS 'MES DE VACACIONES: month (1-12) when 11 months since hire_date are completed';
COMMENT ON COLUMN employee.status_id IS 'ESTADO: employment status';
COMMENT ON COLUMN employee.termination_reason_id IS 'Desvinculación en Infosgroup';
COMMENT ON COLUMN employee.termination_date IS 'Departure date';
COMMENT ON COLUMN employee.talent_review_id IS 'RESULTADO DE TALENT REVIEW';
COMMENT ON COLUMN employee.project_id IS 'PROYECTO ASIGNADO (its client is CLIENTE); NULL = without project. Required by the application';
COMMENT ON COLUMN employee.leader_id IS 'LIDER TLP + COD LIDER. Required by the application';
COMMENT ON COLUMN employee.client_role_id IS 'ROL ACTUAL EN CLIENTE';
COMMENT ON COLUMN employee.equipment_provider_id IS 'EQUIPO / LAPTOP';
COMMENT ON COLUMN employee.client_contact_email IS 'CONTACTO POR CLIENTE';
COMMENT ON COLUMN employee.project_end_date IS 'FECHA FINAL PROYECTO: end of the employee''s contract; NULL = INDEFINIDO';
COMMENT ON COLUMN employee.version IS 'Optimistic locking for concurrent edits';

-- The planner as the client knows it: one row per employee
CREATE VIEW resource_v AS
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
       wm.name                 AS work_modality
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

COMMENT ON VIEW resource_v IS 'The planner columns rebuilt from employee, project and catalogs, for the roster, the table view and exports';
COMMENT ON COLUMN resource_v.assignment_situation IS 'ASIGNADO, PENDIENTE_CONFIRMACION (end date passed, not yet confirmed) or SIN_PROYECTO';
