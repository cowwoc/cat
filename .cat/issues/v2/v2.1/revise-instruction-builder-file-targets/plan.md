# Plan

## Goal

Revise instruction-builder to treat any context-loaded file as a build/review/optimize/test target, and relocate tests from skill-directory/test/ to plugin/tests/<path-to-file-without-extension>/

Two concrete changes:

1. **Generalize file targets**: Currently instruction-builder only targets SKILL.md and first-use.md files. Revise it to treat any file loaded into the agent context as a valid target for building, reviewing, optimizing, and testing. This includes plugin/rules/*.md, plugin/agents/*.md, plugin/concepts/*.md, and any other context-loaded file.

2. **Relocate test directory**: Instead of storing tests in <skill-directory>/test/ (e.g., plugin/skills/instruction-builder-agent/test/), store tests in plugin/tests/<path-to-file-without-extension>/ (e.g., plugin/tests/rules/skill-models/ for plugin/rules/skill-models.md).

## Pre-conditions

(none)

## Post-conditions

- [ ] instruction-builder treats any context-loaded file as a valid build/review/optimize/test target
- [ ] Tests relocated from <skill-dir>/test/ to plugin/tests/<path-to-file-without-extension>/
- [ ] All existing instruction-builder tests continue to pass after relocation
- [ ] No regressions in existing skill-building workflows
- [ ] E2E: instruction-builder can target plugin/rules/skill-models.md and produce tests under plugin/tests/rules/skill-models/
