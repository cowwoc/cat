# Plan: patience-driven-review-concern-handling

## Goal
Use the `patience` configuration option to determine how out-of-scope stakeholder review concerns are handled in the
work-with-issue workflow, replacing the current behavior where all concerns are treated equally regardless of scope
relevance.

## Satisfies
None (infrastructure improvement)

## Background

The `patience` setting exists in `cat-config.json` and is documented in README.md and
`SUBAGENT-PROMPT-CHECKLIST.md`, but is only referenced as prose guidance — no workflow step actually reads it to make
behavioral decisions. Currently, the stakeholder review auto-fix loop treats all concerns the same way regardless of
whether they are in-scope or out-of-scope for the current issue.

**Current patience semantics (from SUBAGENT-PROMPT-CHECKLIST.md):**

| Value | Action on Discovered Issues |
|-------|----------------------------|
| `low` | Act immediately — fix in current issue |
| `medium` | Create issues in CURRENT version backlog |
| `high` | Create issues in a LATER version backlog |

These semantics should extend to out-of-scope review concerns.

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Must not break the existing auto-fix loop for in-scope concerns; must correctly distinguish in-scope
  vs out-of-scope concerns
- **Mitigation:** Only modify concern handling after the auto-fix loop; in-scope concern auto-fix remains unchanged

## Files to Modify
- `plugin/skills/work-with-issue/first-use.md` — Add patience-based handling of out-of-scope review concerns after
  the auto-fix loop
- `plugin/skills/delegate/SUBAGENT-PROMPT-CHECKLIST.md` — Update the patience table to reflect the corrected "high"
  meaning ("later version" not "future/next version") and add review concern handling semantics
- `README.md` — Update patience documentation to cover review concern handling and correct the "high" description

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Update SUBAGENT-PROMPT-CHECKLIST.md patience table:** Change "high" description from "FUTURE version backlog" to
   "LATER version backlog" and add a section explaining patience also applies to out-of-scope review concerns.
2. **Add out-of-scope concern classification to work-with-issue:** After the auto-fix loop in the "Handle Review
   Result" section, add a step that classifies remaining concerns as "in-scope" (directly related to the issue's goal
   and changed files) or "out-of-scope" (improvements/suggestions beyond the issue's intent).
3. **Add patience-based handling for out-of-scope concerns:** Based on the `patience` config value:
   - `low`: Include out-of-scope concerns in the auto-fix loop (treat them like in-scope concerns)
   - `medium`: Create issues for out-of-scope concerns in the current version backlog via `/cat:add`
   - `high`: Create issues for out-of-scope concerns in a later version backlog via `/cat:add`
4. **Update the approval gate concern display:** Clearly label concerns as "in-scope" vs "out-of-scope (deferred)" so
   users can see what was acted on vs deferred.
5. **Update README.md patience documentation:** Add review concern handling to the patience level descriptions and
   correct "high" from "next version" to "later version".

## Post-conditions
- [ ] The `patience` config value is read from `cat-config.json` in the work-with-issue workflow
- [ ] Out-of-scope review concerns are classified and handled differently from in-scope concerns
- [ ] `patience: low` causes out-of-scope concerns to be fixed immediately
- [ ] `patience: medium` creates issues for out-of-scope concerns in the current version
- [ ] `patience: high` creates issues for out-of-scope concerns in a later version
- [ ] The approval gate clearly labels deferred concerns
- [ ] The "high" patience description says "later version" (not "next version" or "future version") in all
  documentation
- [ ] In-scope concern auto-fix behavior is unchanged
