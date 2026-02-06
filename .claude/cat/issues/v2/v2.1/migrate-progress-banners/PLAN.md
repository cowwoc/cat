# Plan: migrate-progress-banners

## Goal
Migrate the progress banner system to use silent `!`command`` preprocessing. This is the most error-prone area (M246,
M256, M257, M288, M298).

## Satisfies
- Parent: migrate-to-silent-preprocessing

## Files to Modify
- .claude/commands/cat-banner/cat-banner.md - Already created, may need refinement
- plugin/hooks/skill_handlers/work_handler.py - Remove banner construction from OUTPUT TEMPLATE
- plugin/commands/work.md - Update to instruct using /cat-banner skill
- plugin/skills/work/SKILL.md - Update phase display instructions

## Acceptance Criteria
- [ ] /cat-banner skill generates correct banners for all phases
- [ ] work_handler.py no longer provides banner templates
- [ ] work.md instructs to use /cat-banner instead of manual construction
- [ ] No M298-type alignment errors possible
