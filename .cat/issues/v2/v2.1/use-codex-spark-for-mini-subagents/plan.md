# Plan

## Goal

Update Codex subagent definitions that currently use `gpt-5.4-mini` so the Haiku-equivalent mechanical subagents use `gpt-5.3-codex-spark` instead, and verify whether any other files contain frontmatter-style `model` declarations.

## Pre-conditions

(none)

## Post-conditions

- [ ] Every `client/plugin/agents/codex/*.toml` file currently containing `model = "gpt-5.4-mini"` uses `model = "gpt-5.3-codex-spark"` instead.
- [ ] No unrelated Codex, Claude, common agent, skill, or test model declarations are changed.
- [ ] A repository search identifies other files with frontmatter-style `model:` declarations so the user can distinguish them from Codex TOML `model =` fields.
- [ ] Tests pass with `mvn -f client/pom.xml verify -e`.
