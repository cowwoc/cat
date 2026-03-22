# Plan

## Goal

Move add-agent description-argument guidance from CLAUDE.md to plugin/rules/work-request-handling.md. The M594
learn cycle placed this end-user behavioral rule in the developer conventions file instead of the end-user rules
layer. Remove the "Invoking add-agent with description" sub-section from CLAUDE.md and add equivalent guidance to
plugin/rules/work-request-handling.md so it is injected into every CAT session.

## Pre-conditions

(none)

## Post-conditions

- [ ] The "Invoking add-agent with description" sub-section is removed from CLAUDE.md (not the entire "Issue
  Workflow vs Direct Implementation" section — only the specific sub-section added by M594)
- [ ] Equivalent guidance is added to plugin/rules/work-request-handling.md in the end-user rules layer
- [ ] Content added to work-request-handling.md is semantically equivalent to what was removed from CLAUDE.md
  (same guidance, adapted for end-user context rather than developer conventions context)
- [ ] No new issues introduced
- [ ] E2E verification: Confirm the rule now appears in plugin/rules/ (confirming it will be injected into every
  CAT session rather than being developer-only)
