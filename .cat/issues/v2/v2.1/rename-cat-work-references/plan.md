# Plan

## Goal

Rename stale /cat:work references to /cat:work-agent across the codebase. The command was renamed but many files still reference the old name in plugin skills, client Java source, tests, docs, and config files.

## Pre-conditions

(none)

## Post-conditions

- [ ] All `/cat:work` references updated to `/cat:work-agent` (except test strings that intentionally use the old command name as a detection example)
- [ ] Tests pass (`mvn -f client/pom.xml verify -e`)
- [ ] No behavioral regressions
