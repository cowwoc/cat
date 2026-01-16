# Plan: version-entry-exit-gates

## Objective
add entry/exit gates for version control

## Details
Formalizes the concept of entry and exit gates for major and minor versions:

Entry gates: conditions that must be met before tasks can start
Exit gates: conditions that determine when a version is complete

Key changes:
- add-minor-version.md: gate wizard after discussion step
- add-major-version.md: gate wizard with inheritance to minor versions
- init.md: gate configuration for imported projects with defaults
- config.md: new "Version Gates" menu for viewing/editing gates
- execute-task.md: entry gate evaluation with --override-gate flag
- status.md: gate status display with 🚧 indicator

Gate conditions supported:
- Previous version complete (default)
- Specific task(s) complete
- Specific version(s) complete
- Manual approval required
- Custom freeform conditions

Gates stored in PLAN.md files under ## Gates section.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
