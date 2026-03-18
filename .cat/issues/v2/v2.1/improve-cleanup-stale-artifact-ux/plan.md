# Plan: improve-cleanup-stale-artifact-ux

## Goal

Improve the `/cat:cleanup` skill's UX when presenting stale artifacts for deletion by displaying session ID and age
for each lock, defaulting to deleting only artifacts idle for 4+ hours, and offering a secondary option to also remove
newer artifacts.

## Satisfies

None — user-requested UX improvement

## Context: Current State

The cleanup skill's `AskUserQuestion` presents a generic "Remove N abandoned worktrees, N stale locks, and N branches?"
prompt without indicating:
- Which session ID holds each lock
- How old each artifact is (so the user can distinguish truly stale from recently-created)
- Which specific items are in scope for the default deletion action

This forces users to reject the prompt and investigate manually before re-running, or blindly approve removal of
potentially active locks.

## Design Decisions

**Age source:** Artifact age is derived from the branch's last commit time via `git log -1 --format=%ct <branch>`,
not from the lock file's `created_at` field. Branch commit time is available from git (synced remotely) and reflects
actual activity. Lock file timestamps are local-only metadata that may not reflect real work status.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Changing the AskUserQuestion format requires updating cleanup skill prompt generation
- **Mitigation:** Branch age is always derivable — every branch has at least one commit

## Files to Modify

- `plugin/skills/cleanup/first-use.md` — Update AskUserQuestion presentation to show per-item session IDs and ages;
  change default action to only target artifacts with age ≥ 4 hours; add secondary option for newer artifacts
- `client/src/.../GetCleanupOutput.java` — Derive Lock age from branch commit time instead of lock file

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Update cleanup skill AskUserQuestion to include session ID and age per artifact**
   - Files: `plugin/skills/cleanup/first-use.md`
   - For each locked issue shown to the user, display: branch age and session ID (e.g., "2.1-fix-catid — 4h 23m,
     session eb68bb02")
   - Age is derived from the branch's last commit time via git, not from lock file metadata

2. **Change default deletion scope to artifacts idle ≥ 4 hours**
   - Files: `plugin/skills/cleanup/first-use.md`
   - Classify artifacts as "stale" (branch age ≥ 4 hours) vs "recent" (branch age < 4 hours)
   - Default AskUserQuestion option removes only stale artifacts
   - Add a secondary non-default option to also remove recent artifacts

3. **Update AskUserQuestion options**
   - Primary option (default): "Remove stale artifacts only (≥4 hours)" — only targets artifacts with age ≥ 4 hours
   - Secondary option: "Remove all artifacts (including recent)" — targets all abandoned artifacts regardless of age
   - Abort option: "Abort" — stop without removing anything

## Post-conditions

- [ ] The cleanup AskUserQuestion displays session ID and branch age for each locked issue
- [ ] Branch age is derived from `git log -1 --format=%ct <branch>`, not from lock file metadata
- [ ] The default deletion option targets only artifacts with branch age ≥ 4 hours
- [ ] A secondary non-default option allows removing artifacts with branch age < 4 hours
- [ ] An abort option is always present
- [ ] The survey output (Step 1 box) is unchanged — only the AskUserQuestion changes
