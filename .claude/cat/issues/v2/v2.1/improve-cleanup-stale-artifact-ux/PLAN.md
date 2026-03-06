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
- How old each lock is (so the user can distinguish truly stale from recently-created)
- Which specific items are in scope for the default deletion action

This forces users to reject the prompt and investigate manually before re-running, or blindly approve removal of
potentially active locks.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Changing the AskUserQuestion format requires updating cleanup skill prompt generation
- **Mitigation:** The survey data already contains session IDs and ages — display them rather than hiding them

## Files to Modify

- `plugin/skills/cleanup/SKILL.md` — Update AskUserQuestion presentation to show per-item session IDs and ages;
  change default action to only target artifacts with age ≥ 4 hours; add secondary option for newer artifacts

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

1. **Update cleanup skill AskUserQuestion to include session ID and age per artifact**
   - Files: `plugin/skills/cleanup/SKILL.md`
   - For each locked issue shown to the user, display: lock age and session ID (e.g., "2.1-fix-catid-path-resolution
     — 326s, session eb68bb02")
   - For each worktree shown, display its age (derived from branch creation or last commit time)

2. **Change default deletion scope to artifacts idle ≥ 4 hours**
   - Files: `plugin/skills/cleanup/SKILL.md`
   - Classify artifacts as "stale" (age ≥ 4 hours) vs "recent" (age < 4 hours)
   - Default AskUserQuestion option removes only stale artifacts
   - Add a secondary non-default option to also remove recent artifacts

3. **Update AskUserQuestion options**
   - Primary option (default): "Remove stale artifacts only (≥4 hours)" — only targets artifacts with age ≥ 4 hours
   - Secondary option: "Remove all artifacts (including recent)" — targets all abandoned artifacts regardless of age
   - Abort option: "Abort" — stop without removing anything

## Post-conditions

- [ ] The cleanup AskUserQuestion displays session ID and age for each locked issue
- [ ] The default deletion option targets only artifacts idle for ≥ 4 hours
- [ ] A secondary non-default option allows removing artifacts that are < 4 hours old
- [ ] An abort option is always present
- [ ] The survey output (Step 1 box) is unchanged — only the AskUserQuestion changes
