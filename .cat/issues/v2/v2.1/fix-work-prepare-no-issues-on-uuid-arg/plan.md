# Plan

## Goal

Fix work-prepare parseRawArguments treating the CAT agent ID UUID as a bare issue name, causing NO_ISSUES
when issues are available. When cat:work-agent invokes work-prepare via `--arguments "${ARGUMENTS}"`, the
$ARGUMENTS string includes the agent ID UUID as the first token. parseRawArguments matches this UUID against
the bare name pattern `^[a-zA-Z][a-zA-Z0-9_-]*$` (UUIDs start with a letter, contain only alphanumeric
chars and hyphens), sets Scope.BARE_NAME, and resolveBareNameToIssueId finds no matching directory →
returns NO_ISSUES. The fix strips the leading UUID-format token before processing remaining args as an issue
name or filter.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: parseRawArguments no longer treats UUID-format agent IDs as bare issue names — when the
  first token matches UUID format, it is stripped before processing the remaining arguments
- [ ] Regression test added: unit test with UUID as the leading --arguments token verifies the correct next
  available issue is selected (not NO_ISSUES)
- [ ] When ARGUMENTS contains `<UUID> <issue-name>`, the issue name is correctly resolved after UUID
  stripping — parseRawArguments processes remaining token(s) exactly as if the UUID had never been present
- [ ] UUID stripping is format-specific: only tokens matching
  `[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}` at position 0 are
  stripped; bare issue names containing hyphens are unaffected
- [ ] All existing WorkPrepareTest tests pass with no regressions (mvn -f client/pom.xml test exits 0)
- [ ] No new issues introduced
- [ ] E2E verification: invoking /cat:work with no explicit issue argument (where ARGUMENTS contains only
  the agent UUID) correctly returns the next available issue rather than NO_ISSUES
