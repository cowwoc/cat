# Plan: extract-learn-record-to-script

## Goal

Extract Phase 4 (Record) of the learn skill into a Java CLI tool, eliminating the LLM invocation for
purely mechanical file I/O work (append JSON entry, increment counter, commit). This reduces learn
skill total execution time by ~24% (~3.5 minutes).

## Satisfies

- None (optimization)

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Phase 4 has non-trivial logic: JSON append with ID generation, counter validation,
  retrospective threshold check, commit to correct location (worktree vs main workspace)
- **Mitigation:** TDD approach — write tests for each Phase 4 responsibility before implementing

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java` — new CLI tool that
  accepts Phase 3 JSON output and performs all Phase 4 actions
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/RecordLearningTest.java` — tests for the
  new tool
- `client/src/main/java/module-info.java` — export new class if needed
- `plugin/skills/learn/phase-record.md` — replace bash instructions with single CLI invocation
- `plugin/skills/learn/SKILL.md` — update Phase 4 description to reference the new tool

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1: Implement CLI tool

- Create `RecordLearning.java` with `main()` that accepts Phase 3 JSON via stdin
  - Responsibilities: generate next mistake ID, append to `mistakes-YYYY-MM.json`, validate counter,
    increment counter in `index.json`, determine commit location (worktree vs main), stage and commit
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/RecordLearning.java`
- Write tests covering: ID generation, JSON append, counter validation/increment, retrospective
  threshold detection, commit location determination
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/RecordLearningTest.java`

### Wave 2: Update learn skill

- Replace Phase 4 bash instructions with single CLI invocation:
  `"$CLIENT_BIN/record-learning" < phase3-output.json`
  - Files: `plugin/skills/learn/phase-record.md`
- Update learn orchestrator to pass Phase 3 output directly to the tool instead of spawning a
  subagent for Phase 4
  - Files: `plugin/skills/learn/SKILL.md`

## Post-conditions

- [ ] `RecordLearning.java` exists with `main()` method accepting Phase 3 JSON via stdin
- [ ] Tool outputs JSON with learning_id, counter status, retrospective trigger, and commit hash
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
- [ ] `phase-record.md` invokes the CLI tool instead of containing inline bash instructions
- [ ] Learn skill SKILL.md references the new tool for Phase 4

### Wave 3: Register record-learning launcher in build-jlink.sh

- Add `"record-learning:util.RecordLearning"` to the `HANDLERS` array in `client/build-jlink.sh` so
  the `record-learning` binary launcher is generated during jlink builds. Without this entry the
  `$CLIENT_BIN/record-learning` path referenced by `phase-record.md` does not exist in the jlink image.
  - Files: `client/build-jlink.sh`
