# Plan: fix-neutral-empirical-javadoc-and-agent-file-references

## Goal
Move the neutral runtime wording and renamed file-reference fixes into an isolated issue worktree.

## Scope
- Keep EmpiricalTestRunner documentation runtime-neutral
- Ensure renamed subagent->agent file references point to existing files
- Preserve behavior; no functional runtime changes intended

## Validation
- mvn -f client/pom.xml verify -e
