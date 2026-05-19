# Plan: Codex Latest-Only Release Alignment

## Goal
Align CAT's Codex documentation and install behavior to support only the latest Codex release model, with no
fallback or backward-compatibility paths, and execute the full release backlog end-to-end.

## Parent Requirements
- None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Full backlog scope increases chance of doc drift and inconsistent parity wording across files.
- **Mitigation:** Track workstream-complete checklists and enforce behavior/contract validation before closure.

## Files to Modify
- `README.md` - latest-only positioning, troubleshooting, permissions/workspace diagnostics.
- `docs/prompts/codex-install.md` - latest-only install/uninstall flow and hook verification guidance.
- `docs/development/codex-parity.md` - hook/session semantics, permissions/profile, remote-control behavior.
- `docs/development/plugin-distribution.md` - marketplace/share/discoverability/version guidance.

## Implementation Evidence
- `README.md` updated with Codex latest-only install flow, `$cat:help` verification, and `/cat:uninstall` removal path.
- `docs/prompts/codex-install.md` updated with latest-only installer contract and explicit no-fallback behavior.
- `docs/development/codex-parity.md` updated with latest-only support statement, current diagnostics flow, and parity-gap framing.
- `docs/development/plugin-distribution.md` updated with latest-only Codex support tier and engine-managed marketplace metadata notes.

## Pre-conditions
- [x] All dependent issues are closed

## Jobs

### Job 1
- Workstream A - Codex install/update UX alignment (latest-only).
  - Files: `README.md`, `docs/prompts/codex-install.md`, `docs/development/plugin-distribution.md`

### Job 2
- Workstream B - Hook model and session semantics alignment.
  - Files: `docs/development/codex-parity.md`, `docs/prompts/codex-install.md`

### Job 3
- Workstream C - Permissions/sandbox/profile contract updates.
  - Files: `docs/development/codex-parity.md`, `README.md`

### Job 4
- Workstream D - Remote-control and diagnostics alignment.
  - Files: `docs/development/codex-parity.md`, `README.md`

### Job 5
- Workstream E - Config/feature-flag deprecation cleanup and behavior/contract checks.
  - Files: `README.md`, `docs/development/codex-parity.md`, `docs/development/plugin-distribution.md`

### Job 6
- Keep issue plan internally synchronized end-to-end.
  - Files: `.cat/issues/v2/v2.1/codex-latest-only-release-alignment/plan.md`

## TaskList
- [x] Workstream A: Reword install/quick-start text to marketplace-native flow as canonical latest path.
- [x] Workstream A: Remove manual cache-copy/fallback install behavior from prompts and docs.
- [x] Workstream A: Add explicit engine-managed sharing/discoverability/version metadata note.
- [x] Workstream A: Remove hardcoded legacy share-bucket naming assumptions.
- [x] Workstream B: Document session-aware hook semantics and no thread-id coupling assumptions.
- [x] Workstream B: Verify/install guidance checks plugin hook enabled state (not feature-flag opt-in).
- [x] Workstream B: Clarify linked-worktree and trust-enforcement hook assumptions.
- [x] Workstream B: Add "CAT does not assume" notes for volatile hook internals.
- [x] Workstream B: Explicitly call out SessionStart `clear` compatibility.
- [x] Workstream C: Update permissions/profile wording to engine-resolved identities.
- [x] Workstream C: Remove stale legacy permission helper/instruction assumptions.
- [x] Workstream C: Add troubleshooting path for permission/workspace-root mismatches.
- [x] Workstream C: Ensure parity docs reference engine-derived workspace roots.
- [x] Workstream D: Reframe remote-control docs as daemon/engine API behavior.
- [x] Workstream D: Add standard `codex doctor` triage flow (human and JSON variants).
- [x] Workstream D: State CAT does not require remote-control but avoids stale control-path instructions.
- [x] Workstream E: Remove stale references to removed/experimental legacy feature toggles.
- [x] Workstream E: Remove or correct stale/invalid Codex config key guidance.
- [x] Workstream E: Validate behavior/contract checks for install/parity flows instead of source-text scanning.
- [x] Sync issue plan backlog items A-E end-to-end.
- [x] Validate no fallback installation logic remains in Codex install instructions.
- [x] Validate Codex-facing docs state latest-version-only support expectations clearly.
- [x] Validate parity/distribution docs contain no removed legacy feature-flag guidance.
- [x] Proposal/plan is internally consistent with latest-only policy and full release backlog scope.

## Post-conditions
- [x] Full release backlog workstreams (A-E) are represented in this issue plan.
- [x] Codex install instructions do not include fallback installation logic (`docs/prompts/codex-install.md`).
- [x] Codex-facing docs state latest-version support expectations clearly (`README.md`, `docs/development/codex-parity.md`, `docs/development/plugin-distribution.md`).
- [x] Parity/distribution docs do not prescribe removed legacy feature flags/config keys (`docs/development/codex-parity.md`, `docs/development/plugin-distribution.md`).
