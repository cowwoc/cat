# Plan: rename-config-plugin-docs

## Objective
Update all plugin skill files, concepts, rules, templates, gitignore files, and documentation to reference the new config filenames. This is Wave 2 of the parent issue decomposition.

## Parent Issue
2.1-rename-cat-config-files (Wave 2 of 3)

## Files to Modify

### Plugin Skills & Concepts
- `plugin/skills/init/first-use.md` - Update config filename references
- `plugin/skills/help/first-use.md` - Update config filename references
- `plugin/skills/add/first-use.md` - Update config filename references
- `plugin/skills/config/first-use.md` - Update config filename references
- `plugin/skills/work/first-use.md` - Update config filename references
- `plugin/skills/work-prepare-agent/first-use.md` - Update config filename references
- `plugin/skills/work-implement-agent/first-use.md` - Update config filename references
- `plugin/skills/work-merge-agent/first-use.md` - Update config filename references
- `plugin/skills/work-review-agent/first-use.md` - Update config filename references
- `plugin/skills/skill-builder-agent/first-use.md` - Update config filename references
- `plugin/skills/tdd-implementation-agent/first-use.md` - Update config filename references
- `plugin/skills/stakeholder-review-agent/first-use.md` - Update config filename references
- `plugin/skills/learn/RELATED-FILES-CHECK.md` - Update config filename references
- `plugin/rules/work-request-handling.md` - Update config filename references
- `plugin/concepts/build-verification.md` - Update config filename references
- `plugin/concepts/worktree-isolation.md` - Update config filename references
- `plugin/concepts/hierarchy.md` - Update config filename references
- `plugin/templates/project.md` - Update project template reference
- `plugin/templates/gitignore` - Update template gitignore entry

### Configuration & Gitignore
- `.gitignore` - Update ignored filename pattern
- `.cat/.gitignore` - Update ignored filename pattern

### Documentation
- `.claude/rules/common.md` - Update config filename references
- `README.md` - Update config filename references
- `docs/severity.md` - Update config filename references
- `docs/patience.md` - Update config filename references

## Pre-conditions
- [ ] Parent issue 2.1-rename-cat-config-files is open

## Post-conditions
- [ ] No remaining references to `cat-config.json` or `cat-config.local.json` in plugin files, rules, concepts, templates, gitignore, or documentation
