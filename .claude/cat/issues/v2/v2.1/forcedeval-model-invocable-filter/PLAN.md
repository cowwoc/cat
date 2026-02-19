# Plan: ForcedEvalSkills should filter on model-invocable, not user-invocable

## Goal
Two changes:
1. Change ForcedEvalSkills to use a `model-invocable` frontmatter field instead of `user-invocable`
2. Strip descriptions from ForcedEvalSkills output (list skill names only) and re-inject the full skill listing
   (with descriptions) after compaction via a SessionStart `compact` hook

## Satisfies
None (bug fix / correctness improvement + token optimization)

## Background

**Problem 1:** `ForcedEvalSkills` uses `user-invocable: false` as a proxy for "don't include in forced eval." This
conflates two separate concerns. The `user-invocable` field controls slash-command access; `model-invocable` should
control forced evaluation.

**Problem 2:** ForcedEvalSkills repeats skill descriptions on every user prompt. Claude Code's built-in skill listing
(injected at SessionStart) is NOT re-sent after compaction — verified from source code: the `YmH` Set (tracking sent
skills) is only cleared by `/clear`, not by compaction. After compaction, the skill listing gets compressed away.

**Solution:** Register a SessionStart hook with `"matcher": "compact"` to re-inject skill descriptions after
compaction. Then ForcedEvalSkills can list just skill names (saving tokens on every prompt).

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Agent may miss skill activation if names alone are insufficient
- **Mitigation:** Full descriptions remain available via SessionStart listing + compact re-injection

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/ForcedEvalSkills.java` — change filter + strip descriptions
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ForcedEvalSkillsTest.java` — update tests
- `client/src/main/java/io/github/cowwoc/cat/hooks/SessionStartHook.java` — add skill listing to compact mode
- `plugin/hooks/hooks.json` — register compact matcher for SessionStart

## Acceptance Criteria
- [ ] `ForcedEvalSkills` filters on `model-invocable: false` instead of `user-invocable: false`
- [ ] `ForcedEvalSkills` outputs skill names only (no descriptions)
- [ ] SessionStart hook with `compact` matcher re-injects full skill listing with descriptions
- [ ] Javadoc accurately describes the filtering logic
- [ ] All tests pass

## Execution Steps
1. **Update ForcedEvalSkills.java** — change filter from `user-invocable` to `model-invocable` and strip descriptions
   from output (list names only)
2. **Add compact re-injection** — update SessionStartHook to include skill listing in compact mode output, and register
   the compact matcher in hooks.json
3. **Update tests** to reflect new behavior
4. **Run tests** to verify
