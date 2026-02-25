# Plan: Add .gitignore for .claude/cat directory

## Goal
Create a standard `.gitignore` file in `.claude/cat` that excludes temporary files (worktrees, locks, verification output) while preserving version control of issue definitions, configurations, and planning documents.

## Satisfies
None - Infrastructure/maintenance issue

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - only adds a new file
- **Scope Impact:** Affects new CAT installations and v2.1 migration
- **Mitigation:** Only creates file if it doesn't exist; idempotent

## Files to Modify
- `plugin/concepts/.gitignore-template` (NEW) - Standard .gitignore template
- `plugin/skills/init/SKILL.md` - Add step to create .gitignore during init
- `plugin/migrations/v2.1-migration.sh` (or equivalent) - Add step to create .gitignore during migration

## Pre-conditions
- [ ] CAT init and migration scripts are established
- [ ] All dependent issues are closed

## Execution Steps

1. **Create .gitignore template**
   - New file: `plugin/concepts/.gitignore-template`
   - Content: patterns for /worktrees/, /locks/, /verify/
   - Files: .gitignore-template

2. **Update /cat:init skill**
   - Add step to copy template to `.claude/cat/.gitignore` if not exists
   - Files: plugin/skills/init/SKILL.md

3. **Update v2.1 migration script**
   - Add step to create `.gitignore` for existing installations
   - Files: plugin/migrations/v2.1-migration.sh

## Post-conditions
- [ ] `.gitignore` created in new CAT installations via `/cat:init`
- [ ] `.gitignore` created in existing installations via v2.1 migration
- [ ] File only created if it doesn't already exist
- [ ] Correct patterns exclude temporary files: /worktrees/, /locks/, /verify/
