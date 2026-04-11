# Plan

## Goal

Move the runtime download, SHA256 verification, and extraction logic from `plugin/hooks/session-start.sh` into a dedicated Java binary (or extend an existing client utility). Currently `session-start.sh` creates two temporary files (`*.tar.gz` and `*.sha256`) for the download operation, which need to be managed manually. Moving this to Java eliminates the need for bash-visible temporary files.

## Pre-conditions

- `session-start.sh` contains a `download_runtime()` function that downloads from GitHub releases, verifies SHA256, and extracts to a target directory
- Two temporary files are created: one for the tarball, one for the SHA256 checksum

## Post-conditions

- [ ] A new Java CLI binary `download-runtime` (or similar) exists in `client/` with the capability to download, verify, and extract a runtime tarball
- [ ] The binary accepts arguments: `--version <version>`, `--target-dir <dir>`, and returns exit code 0 on success, non-zero with error message on failure
- [ ] `session-start.sh` is updated to invoke the Java binary instead of performing the download operations inline
- [ ] The two `mktemp` calls in `download_runtime()` are removed; all temporary file management is internal to the Java binary
- [ ] Baseline tests pass (no regressions)

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/claude/hook/DownloadRuntimeMain.java` — new CLI entry point for runtime download
- `plugin/hooks/session-start.sh` — replace `download_runtime()` function implementation with a call to the Java binary

## Risk Assessment

**Low-medium risk**: The download operation is invoked once per session during initial setup. Moving to Java is a straightforward refactor with clear input/output semantics. Error paths are identical to the current bash implementation.

## Notes

This follows the LLM-to-Java rule: operations that bash scripts perform for file I/O side effects (downloading, verifying, extracting) belong in Java where they can be tested, versioned, and maintained independently.
