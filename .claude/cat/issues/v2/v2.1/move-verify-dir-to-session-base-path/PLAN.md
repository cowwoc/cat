<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan: move-verify-dir-to-session-base-path

## Problem

`VERIFY_DIR` is currently defined as `${PROJECT_CAT_DIR}/${CLAUDE_SESSION_ID}/cat/verify`, which places verification
artifacts inside the project's `.claude/cat/{sessionId}/cat/verify/` directory. This is inconsistent with where Claude
stores other session-specific data — under `~/.config/claude/projects/{encodedProjectDir}/{sessionId}/`.

Verification artifacts (like `criteria-analysis.json`) are session-scoped data and should live alongside other
session files in Claude's session base path, not in the project directory.

## Parent Requirements

None — infrastructure cleanup

## Root Cause

The `VERIFY_DIR` variable was defined using `PROJECT_CAT_DIR` (which resolves to `.claude/cat` in the project root)
instead of using `getSessionBasePath()` (which resolves to `~/.config/claude/projects/{encodedProjectDir}/`).

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Skills and agents that reference `VERIFY_DIR` must all be updated consistently
- **Mitigation:** Only two files reference the variable; grep confirms no other references

## Files to Modify

- `plugin/agents/work-verify.md` — change `VERIFY_DIR` definition to use session base path
- `plugin/skills/work-with-issue-agent/first-use.md` — change `VERIFY_DIR` definition to use session base path

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- In `plugin/agents/work-verify.md`, change the `VERIFY_DIR` definition from:
  ```
  VERIFY_DIR="${PROJECT_CAT_DIR}/${CLAUDE_SESSION_ID}/cat/verify"
  ```
  to use the session base path (`~/.config/claude/projects/{encodedProjectDir}/{sessionId}/cat/verify`).
  The exact variable/mechanism for resolving `getSessionBasePath()` in the agent context needs to be determined
  during implementation.
  - Files: `plugin/agents/work-verify.md`

- In `plugin/skills/work-with-issue-agent/first-use.md`, apply the same `VERIFY_DIR` path change.
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions

- [ ] `VERIFY_DIR` resolves to `{sessionBasePath}/{sessionId}/cat/verify` (under `~/.config/claude/projects/`)
- [ ] `criteria-analysis.json` is written to the new location during verification
- [ ] No references to the old `${PROJECT_CAT_DIR}/${CLAUDE_SESSION_ID}/cat/verify` path remain
- [ ] E2E: Running the verify phase of `/cat:work` writes `criteria-analysis.json` under the session base path
