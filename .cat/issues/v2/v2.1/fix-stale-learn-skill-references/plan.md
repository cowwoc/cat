# Plan

## Goal

Fix stale /cat:learn references in hooks and plugin files that should be /cat:learn-agent. Several files instruct the agent to invoke `/cat:learn` but the correct skill name is `/cat:learn-agent`.

## Pre-conditions

(none)

## Post-conditions

- [ ] `AutoLearnMistakes.java` instructs agent to invoke `/cat:learn-agent` instead of `/cat:learn`
- [ ] `plugin/rules/approval-gate-protocol.md` references `/cat:learn-agent` instead of `/cat:learn`
- [ ] `plugin/concepts/token-warning.md` references `/cat:learn-agent` instead of `/cat:learn`
- [ ] `plugin/concepts/agent-architecture.md` references `/cat:learn-agent` instead of `/cat:learn` (both occurrences)
- [ ] `plugin/skills/learn/first-use.md` references `/cat:learn-agent` instead of `/cat:learn`
- [ ] `mvn -f client/pom.xml verify -e` exits 0
