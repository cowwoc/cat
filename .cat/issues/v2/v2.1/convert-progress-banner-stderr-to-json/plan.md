# Plan

## Goal

Eliminate the use of temporary stderr capture files in `work-implement-agent` by modifying the `progress-banner` binary to report any errors via structured JSON output to stdout instead of stderr. Currently, the skill creates a temporary file to capture stderr, reads it on error, and then deletes it. Moving error reporting to stdout JSON removes the need for temporary file I/O.

## Pre-conditions

- `progress-banner` Java binary currently accepts `--phase` argument and writes banner output to stdout
- `work-implement-agent` skill captures the output and reads stderr from a temp file on non-zero exit

## Post-conditions

- [ ] `progress-banner` binary accepts a new `--output-format json` flag
- [ ] When invoked with `--output-format json`, the binary returns a JSON object containing `{"status": "success|error", "banner_text": "...", "error": "..."}` (error field omitted on success)
- [ ] `work-implement-agent` skill is updated to use the `--output-format json` flag and parse the JSON response instead of using stderr temp files
- [ ] All stderr capture logic and temp file cleanup in `work-implement-agent` for the progress-banner calls is removed (two instances: preparing phase and implementing phase)
- [ ] Baseline tests pass (no regressions)

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/ProgressBannerMain.java` — add `--output-format json` flag and JSON output support
- `plugin/skills/work-implement-agent/first-use.md` — remove `BANNER_STDERR_FILE=$(mktemp ...)` and stderr redirect logic; update to parse JSON response

## Risk Assessment

**Low risk**: progress-banner is a utility binary with a single invocation point (work-implement-agent). Output format change is isolated to this skill. The JSON output can include the same banner text as the current stdout, so the JSON wrapping is purely additive.

## Notes

The goal is to eliminate temporary file creation patterns that can't be statically verified by the worktree isolation hook.
