# Plan: harden-reference-md-redirect

## Goal
Prevent LLM from summarizing skill output on repeated invocations by strengthening the generic reference.md redirect
to explicitly require re-execution of the original skill instructions.

## Satisfies
- M473: Verbatim output skills must never be summarized on repeated invocations

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** LLM compliance with stronger wording (documentation-level prevention)
- **Mitigation:** The new wording is explicit and unambiguous; combined with MEMORY.md entry M473

## Root Cause
`load-skill.sh` uses a session marker file to detect repeated invocations. On second+ invocation, it serves
`reference.md` which says "Find the previously loaded skill definition above and follow those instructions." This vague
redirect allows the LLM to interpret it as permission to summarize rather than re-execute. The fix is to make the
redirect explicit about re-execution requirements.

## Approach
Rewrite `plugin/skills/reference.md` with stronger, unambiguous instructions that explicitly prohibit summarization and
require full re-execution of the original skill instructions.

## Files to Modify
- `plugin/skills/reference.md` - Rewrite with stronger re-execution instructions

## Acceptance Criteria
- [ ] reference.md explicitly prohibits summarizing, paraphrasing, or abbreviating skill output
- [ ] reference.md explicitly requires full re-execution of the original skill instructions
- [ ] No other files modified (single-file change)

## Execution Steps
1. **Rewrite reference.md:** Replace the current 2-line generic redirect with stronger instructions that explicitly
   require re-execution and prohibit summarization.
   - Files: `plugin/skills/reference.md`
2. **Run tests** to verify no regressions.
