# Plan: track-deferred-stakeholder-concerns

## Goal
When stakeholder review concerns are deferred due to PATIENCE threshold (benefit < cost × threshold), automatically
create new CAT issues to track those deferred concerns instead of silently skipping them.

## Satisfies
None - process improvement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Over-creating issues for trivial concerns; cluttering the issue tracker
- **Mitigation:** Only create issues for concerns rated HIGH or CRITICAL that are deferred; LOW/MEDIUM deferred concerns
  can be noted in the review summary without creating issues

## Files to Modify
- `plugin/skills/stakeholder-review/SKILL.md` - Add step after concern evaluation: for each deferred HIGH/CRITICAL
  concern, invoke `/cat:add` to create a tracking issue
- `plugin/skills/work-with-issue/first-use.md` - Document the deferred-concern tracking behavior in the review phase

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Add deferred concern tracking to stakeholder-review skill**
   - Files: `plugin/skills/stakeholder-review/SKILL.md`
   - After the concern evaluation step (where PATIENCE threshold is applied), add instructions:
     - For each concern rated HIGH or CRITICAL that is deferred (benefit < cost × threshold):
       create a new issue via `/cat:add` with the concern details
     - For MEDIUM/LOW deferred concerns: include in the review summary text only (no issue created)
   - Issue naming convention: `{version}-fix-{stakeholder}-{short-description}`

2. **Update work-with-issue to document deferred concern behavior**
   - Files: `plugin/skills/work-with-issue/first-use.md`
   - In the review phase documentation, note that deferred concerns above HIGH severity
     are automatically tracked as new issues

## Post-conditions
- [ ] When a HIGH or CRITICAL concern is deferred due to PATIENCE, a new CAT issue is created to track it
- [ ] When a MEDIUM or LOW concern is deferred, it appears in the review summary text but no issue is created
- [ ] The created issue includes: stakeholder name, severity, file location, concern description, and recommended fix
