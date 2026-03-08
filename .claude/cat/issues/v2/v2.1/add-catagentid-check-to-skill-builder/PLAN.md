# Plan: add-catagentid-check-to-skill-builder

## Goal

Add validation checks to skill-builder's priming prevention checklist covering both argument-passing
patterns for skills that use `skill-loader`. This prevents runtime failures and documentation gaps caused
by incomplete `argument-hint` fields.

## Parent Requirements

None

## Design

All positional arguments (`$0`...`$N` and `$ARGUMENTS`) are caller-provided. `argument-hint` must
document every argument the caller passes, including `$0`.

Skills using `skill-loader` conventionally pass catAgentId as the first argument:

| Pattern | Example | argument-hint |
|---------|---------|---------------|
| `skill-loader` + `$ARGUMENTS` | `!skill-loader foo "$ARGUMENTS"` | `<catAgentId> <arg1> ...` |
| Fixed `$N` positional refs | `!skill-loader foo "$0" "$1"` | `<catAgentId> <arg1>` |

**Check A — catAgentId presence:** Detect when a skill:
1. Uses `skill-loader` in its preprocessor directive
2. Does NOT have `<catAgentId>` as the first token in `argument-hint`

**Check B — positional completeness (M503):** Detect when a skill:
1. References `$0`...`$N` in its preprocessor directive
2. Does NOT have `argument-hint` documenting ALL positional args (including `$0`)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** False positives on skills using `$ARGUMENTS` without `skill-loader`
- **Mitigation:** Check specifically for `skill-loader` in the preprocessor directive

## Files to Modify

- `plugin/skills/skill-builder-agent/first-use.md` — Add catAgentId check to the Frontmatter section
  (after the existing `argument-hint` documentation, around line 448)

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add both argument-hint validation checks to skill-builder's frontmatter section:
  - Location: `plugin/skills/skill-builder-agent/first-use.md`, in the "Positional arguments" block
    (after the existing `argument-hint` documentation)
  - **Check A** (catAgentId for `$ARGUMENTS` skills):
    ```
    **catAgentId requirement**: If `user-invocable: false` AND the preprocessor directive uses
    `skill-loader` with `$ARGUMENTS` (i.e., `!skill-loader <name> "$ARGUMENTS"`), then
    `argument-hint` MUST start with `<catAgentId>`. Omitting it causes runtime failures:
    `catAgentId '<first-arg>' does not match a valid format`.

    - [ ] If `user-invocable: false` and skill uses `skill-loader "$ARGUMENTS"`: argument-hint
          starts with `<catAgentId>`
    ```
  - **Check B** (complete `$N` positional documentation):
    ```
    **Positional argument completeness**: If the preprocessor directive references `$0`...`$N`,
    `argument-hint` MUST document ALL positional args (including `$0`). Every `$N` reference
    must have a corresponding token in `argument-hint`.

    - [ ] Count of tokens in argument-hint matches the highest `$N` reference + 1
    - [ ] Each positional arg has a descriptive name (e.g., `<catAgentId>`, `<issue-path>`)
    ```
  - Update STATE.md: status=closed, progress=100%
  - Commit: `feature: add catAgentId and positional arg-hint checks to skill-builder`

## Post-conditions

- [ ] skill-builder documents Check A: catAgentId required in argument-hint for `$ARGUMENTS` skills
- [ ] skill-builder documents Check B: all `$0`...`$N` args documented in argument-hint
- [ ] E2E: Invoke skill-builder review on a `$ARGUMENTS` skill missing `<catAgentId>` — flagged
- [ ] E2E: Invoke skill-builder review on a `$N` skill with incomplete argument-hint — flagged
