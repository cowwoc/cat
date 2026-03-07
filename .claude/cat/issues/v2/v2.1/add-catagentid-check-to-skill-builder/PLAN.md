# Plan: add-catagentid-check-to-skill-builder

## Goal

Add a validation check to skill-builder's priming prevention checklist that verifies non-user-invocable
skills using `skill-loader` with `$ARGUMENTS` include `<catAgentId>` as the first positional argument in
their `argument-hint`. This prevents runtime failures ("catAgentId does not match a valid format") caused
by skills that omit the required first argument.

## Parent Requirements

None

## Design

Skills fall into two categories based on how they handle arguments:

| Pattern | Example | catAgentId in argument-hint? |
|---------|---------|------------------------------|
| `skill-loader` + `$ARGUMENTS` | `!skill-loader foo "$ARGUMENTS"` | **Required** — first token consumed as catAgentId |
| Fixed `$N` positional refs | `!my-tool "$0" "$1"` | **Not required** — catAgentId not consumed |

The check must detect when a skill:
1. Has `user-invocable: false` (agent-invoked only)
2. Uses `skill-loader` + `$ARGUMENTS` in its preprocessor directive
3. Does NOT have `<catAgentId>` as the first token in `argument-hint`

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

- Add catAgentId validation item to skill-builder's frontmatter section:
  - Location: `plugin/skills/skill-builder-agent/first-use.md`, in the "Positional arguments" block
    (after line 448 where `argument-hint` is described)
  - Add this checklist item to the existing frontmatter defaults section:
    ```
    **catAgentId requirement**: If `user-invocable: false` AND the preprocessor directive uses
    `skill-loader` with `$ARGUMENTS` (i.e., `!skill-loader <name> "$ARGUMENTS"`), then
    `argument-hint` MUST start with `<catAgentId>`. Omitting it causes runtime failures:
    `catAgentId '<first-arg>' does not match a valid format`.

    Checklist item to add to Step N (Validate with Test Prompts or final review):
    - [ ] If `user-invocable: false` and skill uses `skill-loader "$ARGUMENTS"`: argument-hint
          starts with `<catAgentId>`
    ```
  - Update STATE.md: status=closed, progress=100%
  - Commit: `feature: add catAgentId argument-hint check to skill-builder`

## Post-conditions

- [ ] skill-builder's frontmatter section documents the catAgentId requirement for skill-loader skills
- [ ] The check distinguishes between skill-loader+$ARGUMENTS skills (need catAgentId) and
      fixed-$N skills (do not need catAgentId)
- [ ] E2E: Invoke skill-builder review on a skill with `user-invocable: false`, `skill-loader "$ARGUMENTS"`,
      and `argument-hint` missing `<catAgentId>` — skill-builder flags the issue
