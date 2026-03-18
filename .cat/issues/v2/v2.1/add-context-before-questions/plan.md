# Plan: add-context-before-questions

## Goal
Document the convention that all `AskUserQuestion` calls must be preceded by a plain-text display of relevant context, so agents follow this persistently across sessions (including after context compaction).

## Parent Requirements
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** None — documentation-only change to plugin/concepts/questioning.md
- **Mitigation:** Read existing file structure before adding section to avoid duplication

## Files to Modify
- `plugin/concepts/questioning.md` — add `<context_before_questions>` section documenting the convention

## Pre-conditions
- [ ] All dependent issues are closed

## Main Agent Waves

## Sub-Agent Waves

### Wave 1
- Add `<context_before_questions>` section to `plugin/concepts/questioning.md`
  - Files: `plugin/concepts/questioning.md`
  - Place after the `<critical_rule>` section (or at the end if no such section exists)
  - Section content must document:
    1. The convention: display relevant context as plain text immediately before every `AskUserQuestion` call
    2. Why: context compaction may remove earlier conversation content from the user's terminal view
    3. What to include: the data being asked about, relevant decisions already made, current state
    4. Pattern: context block (plain text) → AskUserQuestion call
    5. Example showing a context paragraph followed by an AskUserQuestion invocation

## Post-conditions
- [ ] `plugin/concepts/questioning.md` contains a `<context_before_questions>` section
- [ ] The section specifies that relevant context must be displayed as plain text immediately before every `AskUserQuestion` call
- [ ] The section explains that compaction may remove earlier content from the user's view
- [ ] The section includes a concrete pattern example showing the display-then-question sequence
- [ ] No new priming is introduced (section uses positive actionable language describing what TO do)
- [ ] E2E: The convention is visible when reading `plugin/concepts/questioning.md` and accurately describes the expected behavior
