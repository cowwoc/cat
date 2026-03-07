# Plan: fix-state-schema-doc-target-branch-field

## Goal
`plugin/concepts/state-schema.md` does not list `Target Branch` in its Optional fields table, even though
`StateSchemaValidator.OPTIONAL_KEYS` accepts it as a valid field. Agents reading the schema doc would not know
`Target Branch` is allowed, leading to unnecessary confusion or avoidance of the field.

## Parent Requirements
None - documentation consistency fix

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — one-line addition to a documentation table
- **Mitigation:** None required

## Files to Modify
- `plugin/concepts/state-schema.md` - add `Target Branch` row to the Optional fields table

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- In `plugin/concepts/state-schema.md`, add a row for `Target Branch` to the Optional fields table with
  a description indicating it records the merge target branch for the issue.
  - Files: `plugin/concepts/state-schema.md`

## Post-conditions
- [ ] `plugin/concepts/state-schema.md` Optional fields table includes a `Target Branch` row
- [ ] The row description matches the field's actual purpose (branch the issue merges into)
- [ ] No other content in state-schema.md is changed
