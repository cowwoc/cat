# Plan

## Goal

Update `/cat:cleanup` to detect issue directories with empty or non-JSON `index.json` files as corrupt, so they appear
in the "Corrupt Issue Directories" section of the cleanup survey. Currently, `work-prepare` fails with
"index.json does not contain a JSON object" when it encounters such files, but the cleanup survey does not surface
them, leaving users with no automated recovery path.

## Pre-conditions

(none)

## Post-conditions

- [ ] The cleanup survey detects issue directories whose `index.json` is empty (0 bytes) or contains invalid JSON
  as corrupt, and lists them in the "Corrupt Issue Directories" section
- [ ] `work-prepare` no longer silently skips past corrupt directories — cleanup provides the recovery path instead
- [ ] The detection is implemented in the Java `DetectCorruptIssueDirectories` class (or equivalent)
- [ ] Existing corrupt detection (missing plan.md) continues to work correctly — no regression
- [ ] E2E verification: run `/cat:cleanup` and confirm that directories with empty `index.json` appear under
  "⚠ Corrupt Issue Directories" with an appropriate description
