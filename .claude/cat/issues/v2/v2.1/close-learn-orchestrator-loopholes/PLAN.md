# Plan: close-learn-orchestrator-loopholes

## Problem
`plugin/skills/learn/first-use.md` contains four loopholes that allow the learn orchestrator
subagent to bypass prevention validation:

1. **heredoc-injection** (CRITICAL): Step 4 writes Phase 3 JSON via `cat > "$PHASE3_TMP" << 'PHASE3_EOF'`.
   A single-quoted heredoc is safe from variable expansion, but the delimiter `PHASE3_EOF` appearing on its own
   line inside the JSON would terminate the heredoc prematurely. Subagent-generated JSON cannot be assumed free
   of this string.

2. **subagent-fabrication-passes-validation** (CRITICAL): Step 4 only checks `record-learning` exit code.
   A subagent can copy any real existing commit hash from `git log` — `git cat-file` would pass, but the commit
   has no relation to the prevention work.

3. **phase-summaries-satisfy-validation-without-substance** (HIGH): No validation exists on subagent summary
   content. A single-character `"."` would satisfy a non-empty string requirement.

4. **skip-step-6-when-prevention-implemented-is-true-with-fabricated-commit** (HIGH): Step 6 is only triggered
   when `prevention_implemented: false`. Combined with the weak hash verification, a subagent can set
   `prevention_implemented: true` with any real commit hash and bypass follow-up issue creation entirely.

## Parent Requirements
- None

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** All changes are additive validations. No behavioral path for correct subagent output
  is affected; only fabricated or degenerate output is rejected.
- **Mitigation:** Changes are targeted and minimal to the Step 4 block of first-use.md.

## Files to Modify
- `plugin/skills/learn/first-use.md` - Step 4 block only

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Replace Step 4 in `plugin/skills/learn/first-use.md` with expanded Step 4a/4b/4c structure:
  - Step 4a: Add required-field validation including minimum 20-character summaries
  - Step 4b: Add three-check commit verification (existence, timestamp after spawn, touches prevention_path)
  - Step 4c: Replace heredoc with `printf '%s'` to avoid heredoc injection
  - Update the Step 4 error-handling table to cover all new failure conditions
  - Files: `plugin/skills/learn/first-use.md`

## Post-conditions
- [ ] Step 4 uses `printf '%s'` (not heredoc) to write Phase 3 JSON to the temp file
- [ ] Step 4a validates `phase_summaries.*` fields require at least 20 characters
- [ ] Step 4b verifies `prevention_commit_hash` existence, timestamp after subagent spawn, and that it
  touches the file at `prevention_path`
- [ ] Step 4 error-handling table covers all four new failure modes
- [ ] No other section of first-use.md is altered
