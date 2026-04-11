# Plan

## Goal

Move temporary file creation in the `learn` skill's phase-output pipeline into Java binary internals. Currently, `learn/first-use.md` and `learn/phase-record.md` create a temporary JSON file with `mktemp -p .cat/work/tmp --suffix=.json`, write the phase 3 output to it, and then pass the path to the `record-learning` binary. Moving file management into the Java binary eliminates the need for bash-visible temporary files.

## Pre-conditions

- `learn/first-use.md` and `learn/phase-record.md` create `PHASE3_TMP=$(mktemp -p .cat/work/tmp --suffix=.json)` and write phase output JSON to this file
- The `record-learning` binary reads the file and processes the learning record
- The temp file is deleted after the binary returns

## Post-conditions

- [ ] The `record-learning` Java binary is updated to accept phase output JSON via stdin instead of reading from a file path
- [ ] Alternatively, `record-learning` is updated to accept `--json-content` argument containing the phase output as a string
- [ ] `learn/first-use.md` and `learn/phase-record.md` are updated to pipe or pass the JSON directly to `record-learning` instead of creating temp files
- [ ] The `mktemp` call and temp file cleanup in both files are removed
- [ ] Baseline tests pass (no regressions)

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/RecordLearningMain.java` — extend to accept JSON via stdin or `--json-content` argument
- `plugin/skills/learn/first-use.md` — remove mktemp call; pipe JSON directly to `record-learning`
- `plugin/skills/learn/phase-record.md` — remove mktemp call; pipe JSON directly to `record-learning`

## Risk Assessment

**Low-medium risk**: The `record-learning` binary currently reads from stdin (file redirects are already piped), so modifying it to accept stdin natively is a straightforward enhancement. The change is isolated to the learn skill's final step.

## Notes

This follows the LLM-to-Java rule: the skill orchestrates the learning workflow, while the binary handles JSON parsing and persistence. Moving I/O to the binary reduces coupling and makes the skill easier to read and maintain.
