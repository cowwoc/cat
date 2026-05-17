# Plan

## Goal

Clean up redundant CAT skill and rule frontmatter by scanning all plugin skill and rule files and removing frontmatter entries whose values are identical to loader defaults. Audit runtime environment context rules so Codex keeps the context needed to bootstrap ordinary shell commands, while Claude Code does not carry redundant environment context if `InjectEnv` already exports the same values for Bash.

## Pre-conditions

- Confirm the loader defaults for skill and rule frontmatter before editing.
- Confirm Claude Code `InjectEnv` exports runtime-neutral `CAT_*` variables into future Bash commands.
- Confirm Codex SessionStart injects `rules/codex/cat-environment.md` into main-agent context but does not export variables into ordinary shell commands.

## Post-conditions

- [ ] All `plugin/skills/**/SKILL.md` and `plugin/rules/**/*.md` files are scanned for default-valued frontmatter.
- [ ] Frontmatter fields equivalent to defaults are removed without changing non-default metadata or file behavior.
- [ ] Files whose entire frontmatter only expressed default values have that frontmatter removed.
- [ ] `plugin/rules/codex/cat-environment.md` remains available to Codex main-agent context unless an equivalent automatic shell bootstrap exists.
- [ ] `plugin/rules/claude/cat-environment.md` is removed or narrowed if `InjectEnv` fully covers its shell-use purpose; if retained, the plan documents the remaining non-redundant purpose.
- [ ] Tests cover default frontmatter handling and runtime environment rule behavior for Codex and Claude Code.
- [ ] `mvn -f client/pom.xml verify -e` passes.
