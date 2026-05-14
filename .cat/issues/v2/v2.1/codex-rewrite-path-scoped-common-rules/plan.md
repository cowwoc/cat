# Plan

## Goal

Generate Codex-compatible stubs for path-scoped common rule files and make rule injection preserve the source path of each injected rule.

## Pre-conditions

(none)

## Post-conditions

- [x] `Inject*AgentRules` wraps each injected rule body in a `<rule path="...">` block.
- [x] The `path` attribute identifies the rule file path being injected into context.
- [x] Common rule files can carry YAML frontmatter, including `paths`.
- [x] Codex SessionStart generates stubs under `rules/codex/<same-filename>` for plugin
  `rules/common` files with `paths` frontmatter.
- [x] Codex SessionStart generates stubs under `.cat/rules/codex/<same-filename>` for end-user
  `.cat/rules/common` files with `paths` frontmatter.
- [x] Generated Codex stubs map frontmatter `paths` to the Codex rule-loading convention.
- [x] Generated Codex stubs use the Codex stub path in the injected `<rule path="...">` wrapper.
- [x] `cat:include` fails fast when a referenced file contains leading YAML frontmatter.
- [x] Regression tests cover rule path wrapping and Codex stub generation.
