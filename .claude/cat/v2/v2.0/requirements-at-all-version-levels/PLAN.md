# Plan: requirements-at-all-version-levels

## Problem
Currently, only minor versions have a `## Requirements` section. With flexible versioning:
- Projects using only MAJOR versions (v1, v2) have no place for requirements
- Projects using PATCH versions (v1.0.1) might want patch-specific requirements
- Requirements are artificially tied to the minor level

## Satisfies
- Supports flexible-version-schema by making requirements version-agnostic

## Target State
Requirements can exist at ANY version level that contains tasks:
- Major (if tasks live directly under major)
- Minor (current behavior)
- Patch (if tasks live under patches)

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** 
  - Must update multiple workflow files
  - Requirements validation logic needs to be version-agnostic
- **Mitigation:** Follow existing patterns, update one file at a time

## Files to Modify

### Templates
- plugin/.claude/cat/templates/major-plan.md
  - Add optional `## Requirements` section
- plugin/.claude/cat/templates/patch-plan.md (NEW)
  - Create patch PLAN template with optional `## Requirements`

### Commands
- plugin/commands/add.md
  - Update task_select_requirements to look at parent version (any level)
  - Add requirements step to major_discuss
  - Add requirements step to patch workflow (when created)
- plugin/commands/work.md
  - Update validate_requirements_coverage to work with any version level
- plugin/commands/status.md
  - Display requirements for any version that has them

### Workflows
- plugin/.claude/cat/workflows/version-completion.md
  - Update requirements satisfaction check to work at any level

## Acceptance Criteria
- [ ] Major PLAN.md template has optional Requirements section
- [ ] Patch PLAN.md template exists with optional Requirements
- [ ] /cat:add offers requirements during major version creation
- [ ] /cat:work validates requirements at whatever level defines them
- [ ] /cat:status shows requirements for any version that has them

## Execution Steps
1. **Update major-plan.md template**
   - Add optional `## Requirements` section (same format as minor)
   - Verify: Template includes requirements table

2. **Create patch-plan.md template**
   - Copy minor-plan.md structure
   - Adjust for patch context
   - Verify: File exists with requirements section

3. **Update add.md - major workflow**
   - Add major_derive_requirements step (similar to minor)
   - Verify: Creating major version asks about requirements

4. **Update add.md - task workflow**
   - Make task_select_requirements version-agnostic
   - Look at parent version's PLAN.md regardless of level
   - Verify: Task creation finds requirements at any level

5. **Update work.md**
   - Make validate_requirements_coverage version-agnostic
   - Verify: Works with major/minor/patch requirements

6. **Update version-completion.md**
   - Make requirements check work at any level
   - Verify: Version completion validates requirements properly

7. **Test workflows**
   - Create major with requirements
   - Create task satisfying major requirements
   - Verify satisfaction check works
