# Plan

## Goal

Rename skill-models.md to model-selection.md, remove explicit `model: sonnet` and `model: opus` frontmatter from all skills and subagents, and add entries to model-selection.md for every skill/subagent that currently lacks a model specification.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/rules/skill-models.md` no longer exists; `plugin/rules/model-selection.md` exists with equivalent + expanded content
- [ ] No skill or subagent SKILL.md contains `model: sonnet` or `model: opus` in frontmatter
- [ ] `model-selection.md` has an entry for every skill and subagent that does not have a `model:` frontmatter entry, specifying which model it should use
- [ ] All existing `model: haiku` frontmatter entries remain unchanged
- [ ] E2E verification passes: `mvn -f client/pom.xml verify -e`
