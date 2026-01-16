# Plan: flexible-version-schema

## Goal
Change the version format from MAJOR.MINOR to MAJOR[.MINOR[.PATCH]], allowing versions like v1, v1.0, or v1.0.1. Update all MD files to support this flexible versioning scheme.

## Directory Structure Mapping

The version schema determines where tasks are stored:

| Schema | Example | Task Directory |
|--------|---------|----------------|
| MAJOR | v1 | `.claude/cat/v1/` |
| MAJOR.MINOR | v1.0 | `.claude/cat/v1/v1.0/` |
| MAJOR.MINOR.PATCH | v1.0.0 | `.claude/cat/v1/v1.0/v1.0.0/` |

- **MAJOR only**: Tasks live directly under the major version directory
- **MAJOR.MINOR**: Tasks live under the minor version subdirectory
- **MAJOR.MINOR.PATCH**: Tasks live under the patch version subdirectory

## Satisfies
- None (infrastructure task)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Breaking existing version parsing logic, inconsistent format handling across files
- **Mitigation:** Update all regex patterns consistently, test with all three format variants

## Files to Modify
- Templates: task-state.md, task-plan.md, minor-state.md, minor-plan.md, major-state.md, major-plan.md, changelog.md
- Commands: add.md, remove.md, status.md, work.md, init.md
- Skills: spawn-subagent, collect-results, merge-subagent, decompose-task
- References: hierarchy.md, task-resolution.md
- Scripts: status.sh, task-lock.sh, and other version-parsing scripts

## Acceptance Criteria
- [ ] Version format supports MAJOR alone (e.g., v1)
- [ ] Version format supports MAJOR.MINOR (e.g., v1.0)
- [ ] Version format supports MAJOR.MINOR.PATCH (e.g., v1.0.1)
- [ ] All MD files updated to reflect new format
- [ ] Existing commands/scripts handle all three formats

## Execution Steps
1. **Step 1:** Audit all files for version format references
   - Files: All .md and .sh files in plugin directory
   - Verify: grep -r "v[0-9]" to find all version references

2. **Step 2:** Update regex patterns to handle flexible format
   - Pattern: `v[0-9]+(\.[0-9]+)?(\.[0-9]+)?`
   - Verify: Test patterns match v1, v1.0, and v1.0.1

3. **Step 3:** Update template files
   - Files: .claude/cat/templates/*.md
   - Verify: Templates use flexible version placeholders

4. **Step 4:** Update command documentation
   - Files: commands/*.md
   - Verify: Examples show all three format variants

5. **Step 5:** Update scripts
   - Files: scripts/*.sh
   - Verify: Scripts parse all three formats correctly
