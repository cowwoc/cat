# Plan: Codex Latest-Only Issue Relocation (Retroactive)

## Goal
Relocate this issue record from `v2.7` to `v2.1` retroactively and keep issue metadata internally consistent with the
new canonical path.

## Parent Requirements
- None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Metadata-only relocation can leave stale path references.
- **Mitigation:** Update issue index entry and in-plan self references atomically.

## Files to Modify
- `.cat/issues/v2/v2.1/index.json` - register issue under `v2.1`.
- `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/index.json` - issue metadata at new path.
- `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/plan.md` - canonical path references and scope.

## Implementation Evidence
- Issue directory relocated from `.cat/issues/v2/v2.7/codex-latest-only-release-alignment/` to
  `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/`.
- `.cat/issues/v2/v2.1/index.json` includes `"codex-latest-only-release-alignment"` in `issues`.
- In-plan self-reference updated to `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/plan.md`.

## Pre-conditions
- [x] Issue path migration approved.

## Jobs

### Job 1
- Move issue path from `v2.7` to `v2.1`.
  - Files: `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/{index.json,plan.md}`

### Job 2
- Update parent issue index for `v2.1`.
  - Files: `.cat/issues/v2/v2.1/index.json`

### Job 3
- Keep issue plan internally synchronized with the new canonical path.
  - Files: `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/plan.md`

## TaskList
- [x] Move issue directory to `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/`.
- [x] Update `v2.1` issue index to include `codex-latest-only-release-alignment`.
- [x] Remove stale references to the old `v2.7` issue path from this plan.

## Post-conditions
- [x] Issue exists under `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/`.
- [x] `v2.1` issue index contains `codex-latest-only-release-alignment`.
- [x] Plan references only the canonical `v2.1` issue path.
