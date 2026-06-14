# Plan

## Goal

Update Codex subagent definitions that currently use retired Codex-specific model IDs so Haiku-equivalent mechanical subagents use `gpt-5.4-mini` instead, and verify whether any other files contain frontmatter-style `model` declarations.

## Pre-conditions

(none)

## Post-conditions

- [ ] Every Haiku-equivalent mechanical `client/plugin/agents/codex/*.toml` file uses `model = "gpt-5.4-mini"` instead of a retired Codex-specific model ID.
- [ ] No unrelated Codex, Claude, common agent, skill, or test model declarations are changed.
- [ ] A repository search identifies other files with frontmatter-style `model:` declarations so the user can distinguish them from Codex TOML `model =` fields.
- [ ] Tests pass with `mvn -f client/pom.xml verify -e`.
