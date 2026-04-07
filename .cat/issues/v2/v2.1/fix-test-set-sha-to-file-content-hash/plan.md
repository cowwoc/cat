# Plan

## Goal

Clarify test_set_sha as SHA-256 of skill file content and require SPRT re-run on any skill file update including compression/rewrite passes.

## Pre-conditions

(none)

## Post-conditions

- [ ] `test_set_sha` is documented and stored as SHA-256 (64 hex chars) of the skill file content, not a git commit SHA
- [ ] `detect-changes` redesigned: first argument is SHA-256 content hash; uses equality check (`current_sha != stored_sha` → `skill_changed=true`); prior git content retrieval (`git show`) removed
- [ ] SHA format validation in `detectChanges()` updated from `[0-9a-f]{7,40}` to `[0-9a-f]{64}`
- [ ] `instruction-builder-agent/first-use.md` updated: `INSTRUCTION_DRAFT_SHA` computed as `sha256(skill_file)` not a git commit SHA; compression/rewrite passes explicitly require full SPRT re-run
- [ ] `batch-write-agent/test-results.json` `test_set_sha` updated to actual SHA-256 of `first-use.md`
- [ ] Tests passing, no regressions
