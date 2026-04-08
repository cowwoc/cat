# Plan

## Goal

Add `paths:` frontmatter to `.claude/rules/` files that are narrowly scoped to specific file types or
directories, so they are only loaded into context when relevant files are active. This reduces token
consumption on sessions that don't touch the filtered paths.

## Pre-conditions

(none)

## Post-conditions

- [ ] `.claude/rules/scope-passing.md` has `paths: ["*.java"]` frontmatter
- [ ] `.claude/rules/jackson.md` has `paths: ["*.java"]` frontmatter
- [ ] `.claude/rules/llm-to-java.md` has `paths: ["plugin/**", "client/**"]` frontmatter
- [ ] `.claude/rules/hooks.md` has `paths: ["plugin/hooks/**", ".claude/settings.json"]` frontmatter
- [ ] `.claude/rules/skills.md` has `paths: ["plugin/skills/**", "plugin/commands/**"]` frontmatter
- [ ] `.claude/rules/plugin-file-references.md` has `paths: ["plugin/**"]` frontmatter
- [ ] No other content in any of the above files is changed
- [ ] `java.md` and `index-schema.md` (already path-filtered) are not modified
