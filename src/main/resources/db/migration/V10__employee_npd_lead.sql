-- BUG-39 (SCRUM-241): mark which employees are NPD leads. Until now a lead was
-- whoever led at least one other employee (leader_id), so a lead without
-- collaborators could not be chosen. The import and HU-10 (SCRUM-239) set it.

ALTER TABLE employee ADD COLUMN is_npd_lead boolean NOT NULL DEFAULT false;

-- Rows already imported: the same rule as the import, so nobody has to import again
UPDATE employee
SET is_npd_lead = true
WHERE id IN (SELECT leader_id FROM employee WHERE leader_id IS NOT NULL);

COMMENT ON COLUMN employee.is_npd_lead IS
    'NPD lead: can be chosen as the lead of other employees. Set by the import and by HU-10';
