# Plan

## Goal

Add the missing Codex hook registrations to `client/plugin/hooks/codex/hooks.json` so Codex receives the same CAT protections and context injections that the adapter and client handlers support, including read-related hooks needed for path-scoped rule loading.

## Pre-conditions

(none)

## Post-conditions

- [ ] Codex hook registration includes the missing supported hook events and tool matchers, especially read/search hooks needed for path-scoped rules such as `.cat/rules/codex/java.md`.
- [ ] `client/plugin/hooks/codex/run-hook.sh` correctly adapts registered Codex tool events to the existing client hook handlers.
- [ ] Unsupported Claude-only hooks are intentionally omitted or documented rather than silently registered.
- [ ] Regression coverage verifies the Codex hooks JSON contains the expected registrations and that Java path-scoped rule loading can be triggered for Java file access.
- [ ] `mvn -f client/pom.xml verify -e` passes.
