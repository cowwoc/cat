# Plan: add-backlog-support

## Goal
Add a dedicated `backlog/` directory for unscheduled issues that exist outside the version hierarchy. Issues graduate
from the backlog into a specific version when they are prioritized and scheduled.

## Parent Requirements
None (infrastructure improvement)

## Design Decisions
- **Option C chosen:** Dedicated `backlog/` directory at the same level as version directories, not a special version
  number or issue tags.
- **Planning-only:** Backlog items cannot be worked (`/cat:work`) until graduated to a version.
- **Move via `/cat:move`:** A new skill moves issues between versions or from backlog into a version.

## Structure

```
.cat/
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
  assumption. Multiple skills (add, work, status, move) need updates.
- **Mitigation:** Incremental implementation by wave; test each component before proceeding.

## Files to Modify
- `plugin/concepts/hierarchy.md` - Document backlog concept
- `plugin/concepts/version-paths.md` - Add backlog path resolution
- `plugin/skills/add/SKILL.md` - Add "Backlog" option to add wizard
- `plugin/skills/status/SKILL.md` - Display backlog items in status output
- `client/src/main/java/` - Java handlers for backlog path detection, status display
- New: `plugin/skills/move/SKILL.md` - Move skill (move issues between versions or from backlog)
- New: `plugin/skills/move/first-use.md` - First-use guidance

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

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

### Wave 4: `/cat:move` skill
- New skill to move issues between versions or from backlog into a version
- Moves directory from source (backlog or version) to target version
- Updates source and target STATE.md files
- Commits the move
  - Files: New `plugin/skills/move/SKILL.md`, `plugin/skills/move/first-use.md`

### Wave 5: `/cat:work` guard
- Ensure `/cat:work` rejects backlog items with a clear error message directing the user to move first
  - Files: `plugin/skills/work/` (handler or SKILL.md)

## Post-conditions
- [ ] `backlog/` directory is recognized by path detection and does not interfere with version resolution
- [ ] `/cat:add` allows creating issues directly in the backlog
- [ ] `/cat:status` displays backlog items in a dedicated section
- [ ] `/cat:move` moves issues between versions (or from backlog to a version) and updates STATE.md
- [ ] `/cat:work` rejects backlog items with a clear error message
- [ ] E2E: Create a backlog item via `/cat:add`, verify it appears in `/cat:status`, move it to a version via
  `/cat:move`, then verify it appears under the version in `/cat:status`
