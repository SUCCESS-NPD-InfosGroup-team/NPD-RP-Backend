# SCRUM-228 — Edit and deactivate a project, backend (HU-14)

Jira: [SCRUM-228](https://infosgroup-sucess.atlassian.net/browse/SCRUM-228) · Story: SCRUM-215
HE-01-HU-14 "Edit or deactivate a project" · Frontend: SCRUM-229 · Client questions to confirm: P-14, CA-04 (decided by
the team, to confirm with the client) and the reactivation question of the sheet "Open questions".

Mockups: Figma "NPD-InfosGroup", page "04 · Proyectos": "Proyecto · Editar proyecto".

## User story

As an **Operations administrator**, I want to **edit or deactivate a project**, so that **its name and
client contract end stay correct and finished projects stop appearing as active**.

Today a project can only be created (SCRUM-121) and read. Nothing changes its name, its client contract
end or whether it is active.

## Acceptance criteria

Only the part the API serves. The form, its confirmation and the hidden controls are frontend
(SCRUM-229). CA-03, CA-04 and CA-06 were decided by the team on 2026-11-25 (still to be written in the
HU Excel and, for CA-04, confirmed with the client). CA-05 is still a placeholder (TODO), so this spec
does **not** implement it.

| ID | Criterion | What the backend guarantees |
|----|-----------|-----------------------------|
| CA-01 | Edit and deactivate | The name and the client contract end can be changed; the client cannot. A name already used by another project of the same client is rejected (409). The project can be deactivated only when it has no team (`teamSize = 0`); otherwise 409 with the team size. |
| CA-02 | Only Operations administrators | Editing and deactivating answer 403 to every other role. |
| CA-03 | Project changes are audited (decided 2026-11-25) | Each change writes the per-field audit rows (who, when, field, previous and new value), like "Editar recurso": `name`, `contractEndDate`, `active`. |
| CA-04 | Contract end changed with people on the same date (decided 2026-11-25) | The people of the team (Baja excluded) whose end date **is the old contract end** move with the project: their end date becomes the new contract end, in the same transaction. Everyone else keeps their date. Each person moved gets their own audit row (`assignment.endDate`) and a new `version`. The answer says how many moved. |
| CA-05 | Reactivate a project | **Not implemented (TODO).** There is no reactivation request. An inactive project cannot be edited (409) until this is decided. |
| CA-06 | Invalid values and save failure on edit (decided 2026-11-25) | The contract end follows the rule of creation: it cannot be in the past (today is accepted). The check applies only when the date **changes**, so a project whose contract already ended can still be renamed. Every invalid field is reported at once (400) and nothing is saved; a failure saves nothing and the request can be retried. Two people editing at once: the second gets 409 (`version`). |

## Agreed details

- **Same rules as "Crear proyecto".** Name required, at most 150 characters, stripped, unique within the
  client without case (`project_client_name_uk`).
- **Contract end can be removed.** Sending `contractEndDate: null` leaves the project without contract
  end (no Sales notice, HE-04-HU-01 CA-05).
- **Team** means the same as `teamSize`: people on the project, Baja excluded. A project whose only
  people are on Baja can be deactivated.
- **Deactivating an inactive project** answers 200 and changes nothing (no audit row).
- **Who moves with the contract end (CA-04).**
  - Only a person whose end date is exactly the old contract end. Indefinite people stay indefinite.
  - If the project had no contract end, nobody moves (there is no "old date" to match).
  - If the contract end is **removed** (`null`), nobody moves: people keep their dates and do not become
    indefinite without someone choosing it.
  - A person whose date had already passed (Pendiente de confirmación) and matched the old contract end
    moves too, and becomes Asignado again: the new contract end cannot be in the past (CA-06).
- **The Sales notice.** The future notice job (SCRUM-194) reads `project.contract_end_date` each day, so a
  new date is picked up with no extra work here (HE-04-HU-01 CA-03).

## Scope

**Included**

- Editing the name and the client contract end of an active project.
- Deactivating a project without team.
- Optimistic locking of the project (`version`), its audit rows, and the OpenAPI description.

**Out of scope**

- Changing the client of a project, or the client's name or "Cliente interno" flag (open question).
- Reactivating a project (CA-05), until it is decided.
- Removing the team to be able to deactivate: that is "Quitar del proyecto" (SCRUM-187 / HU-15).

## Decisions taken while writing this spec

- **Deactivation is its own request**, not a field of the edit (design, Decision 1).
- **The project gets a `version` column** (design, Decision 2). It needs a migration; SCRUM-241 takes
  V10, so this one is **V11** (renumber if another migration is merged first).

## Decided on 2026-11-25

- CA-03: project changes are audited like resource changes. **Yes.**
- CA-04: the people whose end date was the old contract end move with the project. **Yes**, with the
  rules of "Who moves with the contract end" above.
- CA-06: same date rule as on creation, and 409 on concurrent edits. **Yes.**

To do outside the code: write these answers in the HU Excel (sheet HE-01 and "Open questions") and
confirm CA-04 with the client at the next meeting.

## Open questions (for the Product Owner / client)

1. CA-05: can an inactive project be reactivated?
