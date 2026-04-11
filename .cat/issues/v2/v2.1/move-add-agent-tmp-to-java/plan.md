# Plan

## Goal

Move temporary file creation in the `add-agent` skill into Java binary internals. Currently, `add-agent/first-use.md` creates two temporary markdown and JSON files (`plan_temp_file` and `index_temp_file`) to stage plan.md and index.json content before passing paths to the `create-issue` CLI binary. Moving file management into the Java binary eliminates the need for bash-visible temporary files and simplifies the skill's orchestration logic.

## Pre-conditions

- `add-agent/first-use.md` creates temp files with `mktemp -p .cat/work/tmp --suffix=.md` and `mktemp -p .cat/work/tmp --suffix=.json`
- The skill writes content to these temp files, then invokes `create-issue --index-file <path> --plan-file <path>`
- The `create-issue` binary reads these files and moves them into the final issue directory

## Post-conditions

- [ ] The `create-issue` Java binary is updated to accept `--plan-content` and `--index-content` arguments containing the file content as strings (or pass content via stdin)
- [ ] Alternatively, `create-issue` accepts a single `--temp-dir` argument and internally creates and manages temporary files within that directory
- [ ] `add-agent/first-use.md` is updated to pass content directly (via arguments or stdin) instead of creating temp files
- [ ] The two `mktemp` calls in the add-agent skill are removed
- [ ] Baseline tests pass (no regressions)

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/CreateIssueMain.java` — extend to accept content arguments or manage temp files internally
- `plugin/skills/add-agent/first-use.md` — remove mktemp calls and temp file creation; pass content directly to `create-issue`

## Risk Assessment

**Medium risk**: The add-agent skill is complex with multiple content-generation steps. The refactor must preserve the exact semantics of plan.md and index.json file creation. Careful testing of various issue types (dependencies, decomposed, etc.) is required.

## Notes

This follows the LLM-to-Java rule: the skill should orchestrate high-level issue creation, while file management and path handling belong in the Java binary. The skill's job is to generate content; the binary's job is to store it safely.
