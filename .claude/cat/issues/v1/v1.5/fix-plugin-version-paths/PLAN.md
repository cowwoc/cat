# Plan: fix-plugin-version-paths

## Objective
replace hardcoded plugin version paths with CLAUDE_PLUGIN_ROOT

## Details
- Replaced 3 hardcoded paths to version 1.2 scripts
- Uses ${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh instead
- Prevents version mismatch when plugin is updated

Fixes M064 - hardcoded version paths become stale

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
