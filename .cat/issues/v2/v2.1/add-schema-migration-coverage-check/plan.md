# Plan: add-schema-migration-coverage-check

## Goal

Add a schema migration coverage check step to `cat:work-implement-agent` that detects when schema-relevant
files (files named `index.json`, `plan.md`, `STATE.md`, or other data-format-defining files) were changed
in the current commit but other files in the repo still reference the old format names, warning the agent
so it can verify whether those referencing files need updating.

## Parent Requirements

None

## Approaches

### A: Inline Bash check in `cat:work-implement-agent` skill (chosen)

- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Add a new step directly in `plugin/skills/work-implement-agent/SKILL.md` that uses
  `git diff` + `grep` to identify changed schema files and then grep the repo for references to the old
  names, printing a WARNING if any unreferenced files are found. No new Java code needed.

**Chosen:** Approach A — inline Bash in the skill file.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** False-positive warnings if a filename like `STATE.md` appears as a legitimate string in
  unrelated contexts (e.g., documentation or comments).
- **Mitigation:** The step WARNS rather than BLOCKS, so false positives do not halt work.

## Schema File Detection Criteria

A file is "schema-relevant" if its basename matches any of the following (case-insensitive):

- `index.json` — CAT issue state file (formerly `STATE.md`)
- `plan.md` — CAT issue plan file (formerly `PLAN.md`)
- `state.md` — legacy CAT state file
- `PLAN.md` — legacy CAT plan file (uppercase variant)

## Files to Modify

- `plugin/skills/work-implement-agent/SKILL.md` — add new "Schema Migration Coverage Check" section
  inserted after the Commit-Before-Spawn block and before the Single-Subagent Execution section.

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add the schema migration coverage check step to `plugin/skills/work-implement-agent/SKILL.md`
  - Insertion anchor: insert the new `### Schema Migration Coverage Check` section AFTER the Commit-Before-
    Spawn Requirement content and BEFORE `### Single-Subagent Execution`
  - The Bash block should:
    1. Use `git diff --name-status ${TARGET_BRANCH}..HEAD` to find changed schema files
    2. For each changed schema file, grep the repo for references to the old name
    3. Filter out already-updated files (those in the current commit's diff)
    4. Filter out sibling worktrees (`/.cat/work/worktrees/`)
    5. If unreferenced files remain: print a WARNING block listing them with line context
    6. Wrap the entire check in `{ ... } || true` so errors do not abort the skill
  - Use only runtime-available tools: `git`, `grep`, `sed`, `basename`, `echo`, `tr`
  - Files: `plugin/skills/work-implement-agent/SKILL.md`

- Run tests: `mvn -f client/pom.xml test` (validation only)

- Commit: `feature: add schema migration coverage check to work-implement-agent`

## Post-conditions

- [ ] `plugin/skills/work-implement-agent/SKILL.md` contains a new "Schema Migration Coverage Check"
  section that runs before subagent delegation
- [ ] The check detects files named `index.json`, `plan.md`, `STATE.md`, or `PLAN.md` in the git diff
- [ ] The check prints a structured WARNING when unreferenced files are found
- [ ] The check is skipped silently when no schema-relevant files appear in the commit diff
- [ ] The check WARNS but does not BLOCK — execution continues after the warning
- [ ] Tests pass: `mvn -f client/pom.xml test` exits 0
- [ ] E2E: new step appears in `plugin/skills/work-implement-agent/SKILL.md`
