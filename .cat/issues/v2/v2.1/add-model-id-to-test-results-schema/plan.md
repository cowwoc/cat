# Plan

## Goal

Add fully-qualified model ID to test-results schema so cached SPRT results are invalidated when the
model changes.

## Pre-conditions

(none)

## Post-conditions

- [ ] Both skills results.json and rules test-results.json schemas include a fully-qualified model ID field (e.g., claude-haiku-4-5-20251001)
- [ ] Tests passing for schema validation
- [ ] Mismatch between stored model ID and current model ID is detectable and triggers re-validation
- [ ] No regressions in existing test infrastructure
- [ ] E2E verification: store a test result with model ID, change the model, verify the cached result is flagged as stale
