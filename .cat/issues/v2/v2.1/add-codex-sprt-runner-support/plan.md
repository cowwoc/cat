# Plan

## Goal

Add Codex support to `instruction-test-runner` and `cat:sprt-runner` so SPRT tests can be executed and reported as
Codex-runtime validation instead of being limited to the existing non-Codex runner path.

## Pre-conditions

(none)

## Post-conditions

- [ ] `instruction-test-runner` supports a Codex-backed execution path for SPRT test cases.
- [ ] `cat:sprt-runner` invokes the Codex-backed runner when running under Codex and no longer reports Codex SPRT as unsupported.
- [ ] Codex model and effort are explicit in runner invocation and persisted in SPRT result metadata for cache invalidation.
- [ ] Existing non-Codex runner behavior remains compatible.
- [ ] Regression coverage verifies Codex SPRT execution, result parsing, and unsupported-runtime fallback behavior.
- [ ] E2E verification demonstrates a Codex SPRT run reaches ACCEPT/REJECT and reports per-test-case results.
