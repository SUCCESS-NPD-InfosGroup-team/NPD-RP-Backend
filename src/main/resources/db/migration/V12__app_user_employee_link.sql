-- HU-03 (SCRUM-53): edit a user's name and role. An NPD lead user is linked to their employee record,
-- so the role change can put that record on Baja and bring it back (design §4, §5). Brings forward the
-- link proposed by HU-10 (SCRUM-239).

-- Link to the employee record: only NPD leads (and former NPD leads) have one, so it is optional.
-- UNIQUE: an employee record belongs to one user at most
ALTER TABLE app_user ADD COLUMN employee_id bigint;
ALTER TABLE app_user ADD CONSTRAINT app_user_employee_id_fk FOREIGN KEY (employee_id) REFERENCES employee (id);
ALTER TABLE app_user ADD CONSTRAINT app_user_employee_id_uk UNIQUE (employee_id);

-- Optimistic locking for concurrent edits, like employee (V2) and project (V11)
ALTER TABLE app_user ADD COLUMN version integer NOT NULL DEFAULT 0;

-- Termination reason of an NPD lead who changes to another role. Inactive: it is not offered in the
-- Recursos form, only the user edit sets it, but its name is still shown on the records that have it
INSERT INTO termination_reason (code, name, active) VALUES ('CAMBIO_DE_ROL', 'Cambio de rol', false);

COMMENT ON COLUMN app_user.employee_id IS 'Employee record of an NPD lead user; NULL for the other roles';
COMMENT ON COLUMN app_user.version IS 'Optimistic locking for concurrent edits';
