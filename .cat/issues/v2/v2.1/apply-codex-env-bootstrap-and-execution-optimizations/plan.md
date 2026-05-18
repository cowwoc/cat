# Plan

## Goal

Apply the execution-efficiency workflow fixes from the recent optimization report and align Codex runtime environment handling so skills bootstrap only minimal `CAT_*` variables while Java CLI invocations rely on `CODEX_*` derivation.

## Pre-conditions

(none)

## Post-conditions

- [ ] Codex runtime-specific skill entry points define only the minimal required `CAT_*` variables before including common skill content.
- [ ] Common skill portions no longer assume globally exported `CAT_*` values in Codex shells.
- [ ] Codex skill/command guidance explicitly keeps `CAT_*` unexported for Java CLI calls so scope is derived from `CODEX_*` values.
- [ ] Merge/review workflow improvements from the session optimization report are applied (review freshness on final squashed/rebased HEAD, avoid duplicate reviewer passes, and close completed review agents before refreshed rounds).
- [ ] Verification/analysis workflow uses Codex-native session identity (`CODEX_THREAD_ID`) without synthesizing fallback UUIDs.
- [ ] Automated tests/docs are updated where needed and all relevant tests pass.
