# Plan: upgrade-recover-from-drift-model

## Goal

Upgrade the `model` field in `plugin/skills/recover-from-drift-agent/SKILL.md` from `haiku` to `sonnet`
to improve the quality of error diagnostics when goal drift is detected.

## Parent Requirements

None

## Approaches

### A: Direct field edit in SKILL.md frontmatter
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Change the single `model: haiku` line to `model: sonnet` in the YAML frontmatter of
  `plugin/skills/recover-from-drift-agent/SKILL.md`. No other files reference this field; the skill loader
  reads it at invocation time.

### B: No alternative approach warranted
- The model field is a single YAML key in a single file. There is no meaningful alternative; the only
  decision is whether to use `sonnet` (chosen) or `opus` (unnecessary cost for diagnostic work).
  `sonnet` is already used by analogous diagnostic/analysis skills: `learn-agent`, `optimize-execution-agent`,
  `rebase-impact-agent`, `verify-implementation-agent`.

**Chosen approach: A** — single-file edit with no risk of regressions.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — changing a model field in a skill's YAML frontmatter has no side effects on other
  files, tests, or runtime behavior beyond the invoked model at skill execution time.
- **Mitigation:** Run the full test suite after the change to confirm no regressions (tests do not
  exercise the model field directly, but confirm the skill file remains parseable and the system is
  consistent).

## Files to Modify

- `plugin/skills/recover-from-drift-agent/SKILL.md` — change `model: haiku` to `model: sonnet` in the
  YAML frontmatter (line 3)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Edit `plugin/skills/recover-from-drift-agent/SKILL.md`: change `model: haiku` to `model: sonnet` in
  the YAML frontmatter.
  - Files: `plugin/skills/recover-from-drift-agent/SKILL.md`
- Run the full test suite to verify no regressions:
  - Command: `mvn -f client/pom.xml test`
- Update `STATE.md` for this issue to reflect completion.
  - Files: `.cat/issues/v2/v2.1/upgrade-recover-from-drift-model/STATE.md`

## Post-conditions

- [ ] `plugin/skills/recover-from-drift-agent/SKILL.md` YAML frontmatter contains `model: sonnet`
- [ ] All existing tests pass with no regressions (`mvn -f client/pom.xml test` exits 0)
- [ ] E2E: invoke the `recover-from-drift-agent` skill in a drift scenario and confirm it executes
  successfully with the sonnet model
