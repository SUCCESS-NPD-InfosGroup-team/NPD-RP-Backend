-- HU-01: the Operations administrator says whether a new collaborator is on
-- probation. probation_end_date (hire_date + 90) is still calculated for
-- everybody and only informs when that period ends.

ALTER TABLE employee ADD COLUMN on_probation boolean NOT NULL DEFAULT false;

-- Rows already imported: on probation while their 90 days have not ended
UPDATE employee SET on_probation = probation_end_date >= current_date;

COMMENT ON COLUMN employee.on_probation IS 'En periodo de prueba: chosen on register; the import sets it while probation_end_date has not passed on the import date';
