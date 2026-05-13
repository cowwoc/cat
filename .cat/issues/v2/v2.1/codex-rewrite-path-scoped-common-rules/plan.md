# Plan

## Goal

Generate Codex-compatible stubs for path-scoped common rule files and make rule injection preserve the source path of each injected rule.

## Pre-conditions

(none)

## Post-conditions

- [ ] `Inject*AgentRules` wraps each injected rule body in a `<rule path="...">` block.
- [ ] The `path` attribute identifies the rule file path being injected into context.
- [ ] Common rule files can carry YAML frontmatter, including `paths`.
- [ ] Codex stubs are generated under `rules/codex/<same-filename>` for common rules with `paths` frontmatter.
- [ ] Generated Codex stubs map frontmatter `paths` to the Codex rule-loading convention.
- [ ] Generated Codex stubs use the Codex stub path in the injected `<rule path="...">` wrapper.
- [ ] Included rule bodies explicitly skip leading YAML frontmatter instead of relying on agent behavior.
- [ ] Regression tests cover rule path wrapping and Codex stub generation.
