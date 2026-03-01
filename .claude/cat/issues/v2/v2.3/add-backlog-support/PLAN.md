# Plan: add-backlog-support

## Goal
Add a dedicated `backlog/` directory for unscheduled issues that exist outside the version hierarchy. Issues graduate
from the backlog into a specific version when they are prioritized and scheduled.

## Satisfies
None (infrastructure improvement)

## Design Decisions
- **Option C chosen:** Dedicated `backlog/` directory at the same level as version directories, not a special version
  number or issue tags.
- **Planning-only:** Backlog items cannot be worked (`/cat:work`) until graduated to a version.
- **Graduation via `/cat:graduate`:** A new skill moves backlog items into a chosen version.

## Structure

```
.claude/cat/
├── backlog/
│   └── <issue-name>/
│       ├── STATE.md
│       └── PLAN.md
├── issues/
│   └── v2/v2.1/...
```

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Path detection logic assumes all issues live under versioned directories; backlog items break that
  assumption. Multiple skills (add, work, status, graduate) need updates.
- **Mitigation:** Incremental implementation by wave; test each component before proceeding.

## Files to Modify
- `plugin/concepts/hierarchy.md` - Document backlog concept
- `plugin/concepts/version-paths.md` - Add backlog path resolution
- `plugin/skills/add/SKILL.md` - Add "Backlog" option to add wizard
- `plugin/skills/status/SKILL.md` - Display backlog items in status output
- `client/src/main/java/` - Java handlers for backlog path detection, status display
- New: `plugin/skills/graduate/SKILL.md` - Graduation skill
- New: `plugin/skills/graduate/first-use.md` - First-use guidance

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1: Data model and path detection
- Add `backlog/` path support to version-paths.md and hierarchy.md
- Update Java path detection to recognize backlog items
  - Files: `plugin/concepts/hierarchy.md`, `plugin/concepts/version-paths.md`, `client/src/main/java/`

### Wave 2: `/cat:add` backlog support
- Add "Backlog" as a target option in the add wizard alongside version selection
- Create STATE.md and PLAN.md under `backlog/<issue-name>/`
  - Files: `plugin/skills/add/SKILL.md`, Java handler updates

### Wave 3: `/cat:status` backlog display
- Display backlog items in a separate section of status output
- Show count and list of backlog items
  - Files: `plugin/skills/status/SKILL.md`, Java status display

### Wave 4: `/cat:graduate` skill
- New skill to move a backlog item into a chosen version
- Moves directory from `backlog/<name>/` to `issues/v.../v.../<name>/`
- Updates target version's STATE.md with the new issue
- Commits the move
  - Files: New `plugin/skills/graduate/SKILL.md`, `plugin/skills/graduate/first-use.md`

### Wave 5: `/cat:work` guard
- Ensure `/cat:work` rejects backlog items with a clear error message directing the user to graduate first
  - Files: `plugin/skills/work/` (handler or SKILL.md)

## Post-conditions
- [ ] `backlog/` directory is recognized by path detection and does not interfere with version resolution
- [ ] `/cat:add` allows creating issues directly in the backlog
- [ ] `/cat:status` displays backlog items in a dedicated section
- [ ] `/cat:graduate` moves a backlog item into a chosen version and updates STATE.md
- [ ] `/cat:work` rejects backlog items with a clear error message
- [ ] E2E: Create a backlog item via `/cat:add`, verify it appears in `/cat:status`, graduate it to a version via
  `/cat:graduate`, then verify it appears under the version in `/cat:status`
