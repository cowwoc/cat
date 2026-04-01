# Plan

## Goal

Refactor instruction-builder-agent's first-use.md by applying its own design methodology to itself. Centralize the
curiosity gate (from 4 scattered config reads to 1), flatten step numbering (eliminate decimal sub-steps 4.1-4.5 and
7.1-7.4), reduce the verification checklist (from ~80 items to ~30 outcome-oriented checks), extract the Subagent
Command Allowlist into a shared section, add an explicit Prerequisites section, and condense subagent prohibition
prompts by ~40%.

## Pre-conditions

(none)

## Post-conditions

- [ ] User-visible behavior unchanged: the instruction-builder produces identical outputs for all existing workflows
- [ ] All tests passing: `mvn -f client/pom.xml test` exits 0
- [ ] Code quality improved: step numbering is sequential (no decimal sub-steps), curiosity gate is read once, verification checklist is grouped by phase
- [ ] E2E verification: invoke instruction-builder on a simple test skill and confirm it completes the design subagent phase successfully
