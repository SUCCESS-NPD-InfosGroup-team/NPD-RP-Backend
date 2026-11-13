-- HU-06 (SCRUM-179): one row per dated event of the "Arribos proximos" list.
-- The view has no time window: the API filters by date with the application
-- clock (America/Bogota), and it also decides which assignment ends have
-- already passed ("Pendiente de confirmacion"). Employees on Baja give no
-- events and are not counted as team.
CREATE VIEW upcoming_event_v AS
-- Assignment ends: each person's end date on their project. The ones already
-- passed are the "Pendiente de confirmacion" of the list (RQ-10).
SELECT 'ASSIGNMENT_END-' || e.id          AS event_id,
       'ASSIGNMENT_END'                   AS kind,
       e.project_end_date                 AS event_date,
       e.id                               AS employee_id,
       e.code                             AS employee_code,
       e.full_name,
       cr.name                            AS client_role,
       1::bigint                          AS resource_count,
       c.id                               AS client_id,
       c.name                             AS client,
       p.id                               AS project_id,
       p.name                             AS project,
       l.id                               AS leader_id,
       l.full_name                        AS leader
FROM employee e
JOIN resource_status rs      ON rs.id = e.status_id
JOIN project p               ON p.id = e.project_id
JOIN client c                ON c.id = p.client_id
LEFT JOIN client_role cr     ON cr.id = e.client_role_id
LEFT JOIN employee l         ON l.id = e.leader_id
WHERE rs.code <> 'BAJA'
  AND e.project_end_date IS NOT NULL
UNION ALL
-- Probation ends (day 90), only for people still on probation
SELECT 'PROBATION_END-' || e.id,
       'PROBATION_END',
       e.probation_end_date,
       e.id, e.code, e.full_name, cr.name, 1::bigint,
       c.id, c.name, p.id, p.name, l.id, l.full_name
FROM employee e
JOIN resource_status rs      ON rs.id = e.status_id
LEFT JOIN project p          ON p.id = e.project_id
LEFT JOIN client c           ON c.id = p.client_id
LEFT JOIN client_role cr     ON cr.id = e.client_role_id
LEFT JOIN employee l         ON l.id = e.leader_id
WHERE rs.code <> 'BAJA'
  AND e.on_probation
UNION ALL
-- Vacation starts: the planner only keeps the vacation month, so the event is
-- the first day of that month, this year and next year (a 90-day window can
-- cross into January). The first vacation comes 11 months after hire.
SELECT 'VACATION_START-' || e.id || '-' || y.year,
       'VACATION_START',
       make_date(y.year, e.vacation_month, 1),
       e.id, e.code, e.full_name, cr.name, 1::bigint,
       c.id, c.name, p.id, p.name, l.id, l.full_name
FROM employee e
CROSS JOIN LATERAL (SELECT extract(year FROM current_date)::int + n AS year
                    FROM generate_series(0, 1) AS n) y
JOIN resource_status rs      ON rs.id = e.status_id
LEFT JOIN project p          ON p.id = e.project_id
LEFT JOIN client c           ON c.id = p.client_id
LEFT JOIN client_role cr     ON cr.id = e.client_role_id
LEFT JOIN employee l         ON l.id = e.leader_id
WHERE rs.code <> 'BAJA'
  AND make_date(y.year, e.vacation_month, 1) >= date_trunc('month', e.hire_date + interval '11 months')::date
UNION ALL
-- Client contract ends: one row per active project, for its whole team. The
-- lead is given only when the whole team has the same one.
SELECT 'CONTRACT_END-' || p.id,
       'CONTRACT_END',
       p.contract_end_date,
       NULL::bigint, NULL::varchar, NULL::varchar, NULL::varchar, ps.team_size,
       c.id, c.name, p.id, p.name, lead.leader_id, lead.leader
FROM project p
JOIN client c                ON c.id = p.client_id
JOIN project_summary_v ps    ON ps.project_id = p.id
LEFT JOIN LATERAL (SELECT min(l.id)        AS leader_id,
                          min(l.full_name) AS leader
                   FROM employee m
                   JOIN resource_status mrs ON mrs.id = m.status_id
                   JOIN employee l          ON l.id = m.leader_id
                   WHERE m.project_id = p.id
                     AND mrs.code <> 'BAJA'
                   HAVING count(DISTINCT l.id) = 1) lead ON true
WHERE p.active
  AND p.contract_end_date IS NOT NULL;

COMMENT ON VIEW upcoming_event_v IS 'Dated events of the upcoming events list (HU-06), without time window';
COMMENT ON COLUMN upcoming_event_v.event_id IS 'Stable id: <kind>-<employee or project id>[-<year> for vacations]';
COMMENT ON COLUMN upcoming_event_v.kind IS 'ASSIGNMENT_END, PROBATION_END, VACATION_START or CONTRACT_END; a past ASSIGNMENT_END is shown as PENDING_CONFIRMATION';
COMMENT ON COLUMN upcoming_event_v.employee_id IS 'NULL on CONTRACT_END, which affects the whole team';
COMMENT ON COLUMN upcoming_event_v.resource_count IS '1 on employee events; team size (Baja excluded) on CONTRACT_END';
COMMENT ON COLUMN upcoming_event_v.leader_id IS 'NPD lead; on CONTRACT_END only when the whole team has the same one';
