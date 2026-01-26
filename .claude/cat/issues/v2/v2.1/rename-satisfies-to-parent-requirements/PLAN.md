# Plan: rename-satisfies-to-parent-requirements

## Current State
Issue PLAN.md files and plugin templates use `## Satisfies` as the section heading that links issues to
parent version requirements. The name is opaque — it does not indicate that values are requirement IDs
defined in the parent version's PLAN.md.

## Target State
All `## Satisfies` headings and prose references (field names, labels, backtick references) are renamed
to `## Parent Requirements`, making it explicit that the section contains requirement IDs from the parent.

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None (heading label change only, no semantic change)
- **Mitigation:** Grep verification that no `## Satisfies` or prose `Satisfies` references remain in
  plugin files after execution; existing closed issues are NOT modified (historical records)

## Files to Modify

### Plugin Templates
- `plugin/templates/issue-plan.md` — rename `## Satisfies` heading
- `plugin/templates/plan.md` — rename `## Satisfies` heading and prose references

### Plugin Concepts
- `plugin/concepts/hierarchy.md` — rename `## Satisfies` in examples, diagrams, and prose
- `plugin/concepts/version-completion.md` — rename prose reference to `## Satisfies`

### Plugin Agents
- `plugin/agents/stakeholder-requirements.md` — rename all `Satisfies` references

### Plugin Skills
- `plugin/skills/add/first-use.md` — rename `## Satisfies` in PLAN.md template, AskUserQuestion
  `header: "Satisfies"`, and all prose references (`Satisfies = None`, `Satisfies field`, etc.)

### Migration Script
- `plugin/migrations/` — add or update the current version's migration script to rename
  `## Satisfies` → `## Parent Requirements` in all issue PLAN.md files (idempotent)

### Existing Issue PLAN.md Files
- `.claude/cat/issues/v2/v2.1/*/PLAN.md` — rename `## Satisfies` heading (~pending issues only;
  closed issues are NOT modified)
- All other open/pending issue PLAN.md files across all versions

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Update Plugin Files
- Rename `## Satisfies` → `## Parent Requirements` heading and all prose references in plugin template,
  concept, agent, and skill files listed above
  - Files: plugin/templates/issue-plan.md, plugin/templates/plan.md, plugin/concepts/hierarchy.md,
    plugin/concepts/version-completion.md, plugin/agents/stakeholder-requirements.md,
    plugin/skills/add/first-use.md

### Wave 2: Migration Script + Existing Issue PLAN.md Files
- Add or update the current version's migration script in `plugin/migrations/` to rename
  `## Satisfies` → `## Parent Requirements` in all issue PLAN.md files (must be idempotent)
- Run the migration to update all existing PLAN.md files
  - Files: plugin/migrations/, all PLAN.md files under .claude/cat/issues/

### Wave 3: Verification
- `grep -r "^## Satisfies" plugin/` returns zero matches
- `grep -r "^## Satisfies" .claude/cat/issues/` returns zero matches
- `grep -rn "Satisfies" plugin/templates/ plugin/concepts/ plugin/agents/ plugin/skills/add/` returns
  zero matches for section-name references

## Post-conditions
- [ ] All plugin template files updated: `## Satisfies` → `## Parent Requirements`
- [ ] All prose references to `Satisfies` as a section name, field name, or label in `plugin/templates/`,
  `plugin/concepts/`, `plugin/agents/`, and `plugin/skills/` are updated to use "Parent Requirements"
- [ ] AskUserQuestion `header: "Satisfies"` in add-agent skill renamed to `"Parent Requirements"`
- [ ] No occurrences of `## Satisfies` remain in `plugin/`
- [ ] No occurrences of `## Satisfies` remain in `plugin/` or open issue PLAN.md files
- [ ] Migration script in `plugin/migrations/` is idempotent and renames `## Satisfies` in PLAN.md files
- [ ] E2E: Running `/cat:add` produces a PLAN.md with `## Parent Requirements` (not `## Satisfies`)
