# Plan

## Goal

Add the missing supported Codex hook registrations to `client/plugin/hooks/codex/hooks.json` so Codex receives the
CAT protections and context injections that current Codex hook events can provide. Codex support is intentionally
limited to Codex-exposed hook events; Claude-only read/search hooks are documented as unsupported until Codex exposes
compatible events.

## Pre-conditions

(none)

## Post-conditions

- [ ] Codex hook registration includes the supported hook events and native Codex tool matchers for session start and
  executable-command pre-tool validation.
- [ ] Codex hook launchers parse native Codex hook input directly and do not require
  `client/plugin/hooks/codex/run-hook.sh`.
- [ ] Unsupported Claude-only hooks are intentionally omitted or documented rather than silently registered.
- [ ] Regression coverage verifies the Codex hooks JSON contains the expected supported registrations and that native
  Codex command payloads reach the Codex pre-bash guard.
- [ ] `mvn -f client/pom.xml verify -e` passes.
