# Plan: fix-learn-subagent-hash-fabrication

## Problem

When the learn skill delegates to a subagent, the subagent returns fabricated commit hashes
(e.g., `"prevention_commit_hash": "abc1234d"`) without making actual commits.

## Expected vs Actual

- **Expected:** `prevention_commit_hash` reflects an actual git hash from `git rev-parse --short HEAD`,
  or `null` if no commit was made
- **Actual:** Subagent fills in plausible-looking fabricated hashes (e.g., `"abc1234"`, `"def5678"`)

## Root Cause

The orchestrator prompt in `first-use.md` (Step 3) and the output format examples in `phase-record.md`
and `phase-prevent.md` show concrete example values like `"prevention_commit_hash": "abc1234"` directly
in JSON templates. This primes the subagent to fill the template with similar-looking fabricated values
instead of performing actual work.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** None — changes are prompt/documentation only, no code logic affected
- **Mitigation:** Read files after editing to verify changes are present

## Files to Modify

- `plugin/skills/learn/first-use.md` — Add anti-fabrication instruction to Step 3 subagent prompt
- `plugin/skills/learn/phase-record.md` — Replace `"abc1234"` / `"def5678"` with `"<actual-git-hash-or-null>"`
- `plugin/skills/learn/phase-prevent.md` — Verify no concrete hash examples in output format

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Edit `plugin/skills/learn/first-use.md`: add anti-fabrication block before the JSON output template
  - Files: `plugin/skills/learn/first-use.md`
- Edit `plugin/skills/learn/phase-record.md`: replace concrete hash examples with angle-bracket placeholders
  - Files: `plugin/skills/learn/phase-record.md`
- Edit `plugin/skills/learn/phase-prevent.md`: verify output format has no concrete hash examples; align
  if any found
  - Files: `plugin/skills/learn/phase-prevent.md`

## Post-conditions

- [ ] No concrete fabricated hash strings (e.g., `abc1234`, `def5678`) appear in learn skill output format examples
- [ ] `first-use.md` Step 3 subagent prompt contains explicit anti-fabrication instruction covering
  `prevention_commit_hash`, `retrospective_commit_hash`, and `prevention_implemented`
- [ ] All modified files have lines ≤ 120 characters
- [ ] `mvn -f client/pom.xml test` passes with no regressions
- [ ] E2E: Invoke learn skill; confirm subagent returns `null` for commit hashes when no commit is made
  rather than fabricated values
