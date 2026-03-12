# Plan: fix-implementation-delegation-priming

## Problem

`plugin/rules/implementation-delegation.md` contains two sections that prime the main agent to
rationalize making direct code edits: (1) a "Main agent directly handles" list that explicitly
names "Single-line config changes" as an exception, and (2) a "Delegate via Task tool when" list
implying a threshold of 2-3 edits below which direct editing is acceptable. These primed the main
agent to judge whether a change is "small enough" to do directly rather than always delegating.

## Parent Requirements

None

## Root Cause

The rule was written with threshold-based exceptions instead of an absolute prohibition. The
exceptions create a judgment gap where the main agent reasons "this is small, I'll just edit
it directly" — exactly the behavior the rule is meant to prevent. Root cause documented in
learning M522.

## Expected vs Actual

- **Expected:** Main agent never uses Edit or Write tools for implementation work.
- **Actual:** Main agent edits files directly when it judges the change to be "small enough"
  (1-2 edits, single file, comment fix, etc.) based on the exception list.

## Risk Assessment

- **Risk Level:** LOW
- **Regression Risk:** Removing the exceptions may remove useful guidance; must preserve the
  "why" and the correct main agent behaviors (read, orchestrate, spawn subagents).
- **Mitigation:** Review file before and after to confirm the prohibition is clear and the
  preserved content is still useful.

## Files to Modify

- `plugin/rules/implementation-delegation.md` — remove "Main agent directly handles" section
  (specifically "Single-line config changes" exception) and the threshold-based "Delegate via
  Task tool when" list; rewrite to state the rule as an unconditional prohibition.

## Test Cases

- [ ] `implementation-delegation.md` does not contain "Single-line config changes"
- [ ] `implementation-delegation.md` does not contain "2-3 edits" threshold language
- [ ] `implementation-delegation.md` does not contain "Main agent directly handles" section
      with file-editing permissions
- [ ] The rule clearly states "MUST NOT use Edit or Write tools" or equivalent
- [ ] Preserved content (why delegation matters, main agent read-only role) remains accurate

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Rewrite `plugin/rules/implementation-delegation.md`:
  - Remove the "Delegate via Task tool when" bullet list (the threshold-based list)
  - Remove "Single-line config changes" from any main agent exception list
  - Remove the entire "Main agent directly handles" section if it grants file-editing permission
  - Add explicit unconditional prohibition: "The main agent MUST NOT use Edit or Write tools for
    implementation work, regardless of change size"
  - Preserve: the rule header, why delegation matters, correct main agent behaviors (read-only
    operations, orchestration, spawning subagents), and the CRITICAL marker
  - Files: `plugin/rules/implementation-delegation.md`
- Update STATE.md: status closed, progress 100%, resolution implemented
  - Files: `.cat/issues/v2/v2.1/fix-implementation-delegation-priming/STATE.md`

## Post-conditions

- [ ] `plugin/rules/implementation-delegation.md` contains no threshold language or exceptions
      permitting direct file edits by the main agent
- [ ] The rule states unconditionally that main agent must not use Edit/Write for implementation
- [ ] All test cases pass (content verification via grep)
- [ ] E2E: Read `plugin/rules/implementation-delegation.md` and confirm the priming text is
      absent and the prohibition is clearly stated
