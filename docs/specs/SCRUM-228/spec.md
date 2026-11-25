# SCRUM-228 — Edit and deactivate a project, backend (HU-14)

Jira: [SCRUM-228](https://infosgroup-sucess.atlassian.net/browse/SCRUM-228) · Story: SCRUM-215
HE-01-HU-14 "Edit or deactivate a project" · Frontend: SCRUM-229 · Client questions to confirm: P-14 and
the two new questions of the sheet "Open questions" (people's end dates when the contract end changes;
reactivating a project).

Mockups: Figma "NPD-InfosGroup", page "04 · Proyectos": "Proyecto · Editar proyecto".

## User story

As an **Operations administrator**, I want to **edit or deactivate a project**, so that **its name and
client contract end stay correct and finished projects stop appearing as active**.

Today a project can only be created (SCRUM-121) and read. Nothing changes its name, its client contract
end or whether it is active.

## Acceptance criteria

Only the part the API serves. The form, its confirmation and the hidden controls are frontend
(SCRUM-229). CA-04 and CA-05 are placeholders in the HU Excel (TODO), so this spec does **not** implement
them; CA-03 and CA-06 are proposals in the HU Excel, implemented as written below and marked
**to confirm**.

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Edit and deactivate | The name and the client contract end can be changed; the client cannot. A name already used by another project of the same client is rejected (409). The project can be deactivated only when it has no team (`teamSize = 0`); otherwise 409 with the team size. |
| CA-02 | Only Operations administrators | Editing and deactivating answer 403 to every other role. |
| CA-03 | Project changes are audited (**to confirm**) | Each change writes the per-field audit rows (who, when, field, previous and new value), like "Editar recurso": `name`, `contractEndDate`, `active`. |
| CA-04 | Contract end changed with people on the same date | **Not implemented (TODO).** Changing the contract end does **not** change any person's end date. When the client answers, a follow-up task adds the rule. |
| CA-05 | Reactivate a project | **Not implemented (TODO).** There is no reactivation request. An inactive project cannot be edited (409) until this is decided. |
| CA-06 | Invalid values and save failure on edit (**to confirm**) | The contract end follows the rule of creation: it cannot be in the past (today is accepted). The check applies only when the date **changes**, so a project whose contract already ended can still be renamed. Every invalid field is reported at once (400) and nothing is saved; a failure saves nothing and the request can be retried. Two people editing at once: the second gets 409 (`version`). |

## Agreed details

- **Same rules as "Crear proyecto".** Name required, at most 150 characters, stripped, unique within the
  client without case (`project_client_name_uk`).
- **Contract end can be removed.** Sending `contractEndDate: null` leaves the project without contract
  end (no Sales notice, HE-04-HU-01 CA-05).
- **Team** means the same as `teamSize`: people on the project, Baja excluded. A project whose only
  people are on Baja can be deactivated.
- **Deactivating an inactive project** answers 200 and changes nothing (no audit row).
- **The Sales notice.** The future notice job (SCRUM-194) reads `project.contract_end_date` each day, so a
  new date is picked up with no extra work here (HE-04-HU-01 CA-03).

## Scope

**Included**

- Editing the name and the client contract end of an active project.
- Deactivating a project without team.
- Optimistic locking of the project (`version`), its audit rows, and the OpenAPI description.

**Out of scope**

- Changing the client of a project, or the client's name or "Cliente interno" flag (open question).
- Reactivating a project (CA-05) and moving people's end dates (CA-04), until the client answers.
- Removing the team to be able to deactivate: that is "Quitar del proyecto" (SCRUM-187 / HU-15).

## Decisions taken while writing this spec

- **Deactivation is its own request**, not a field of the edit (design, Decision 1).
- **The project gets a `version` column** (design, Decision 2). It needs a migration; SCRUM-241 takes
  V10, so this one is **V11** (renumber if another migration is merged first).

## Open questions (for the Product Owner / client)

1. CA-03: confirm that project changes are audited like resource changes.
2. CA-06: confirm the same date rule as on creation and the 409 on concurrent edits.
3. CA-04: when the contract end changes, do the people whose end date was the old contract end move with
   it?
4. CA-05: can an inactive project be reactivated?
