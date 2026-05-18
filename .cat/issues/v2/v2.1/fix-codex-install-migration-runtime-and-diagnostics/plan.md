# Plan: fix-codex-install-migration-runtime-and-diagnostics

## Goal
Fix Codex SessionStart install-migration failures by making migration scripts runtime-compatible, restoring shared migration utilities, and improving failure diagnostics.

## Parent Requirements
None

## Pre-conditions
- [ ] All dependent issues are closed

## Post-conditions
- [ ] `client/plugin/migrations/*.sh` uses CAT runtime-compatible plugin root (no fallback logic).
- [ ] `client/plugin/migrations/lib/utils.sh` exists and provides required migration helpers used by migration scripts.
- [ ] Plugin cache/install packaging includes `client/plugin/migrations/lib/utils.sh` so installed migration scripts can source it.
- [ ] `CheckDataMigration` failure output for install migration includes migration stdout/stderr details to speed diagnosis.
- [ ] Session-start/install migration coverage includes regression tests for the above behaviors.
- [ ] E2E: A Codex SessionStart run with missing install marker can complete install migration without failing on missing env var or missing migration utils.
