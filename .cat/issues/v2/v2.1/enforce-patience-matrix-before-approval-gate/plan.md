# Plan: enforce-patience-matrix-before-approval-gate

## Problem

Step 6 (Deferred Concern Review) in work-with-issue-agent/first-use.md primes agents to recommend
skipping deferred concerns. Two priming sources:

1. **Positional bias:** "Skip all" is listed as the first AskUserQuestion option, making it the default
   recommendation path
2. **Framing bias:** The option is labeled "Skip all (no action needed)", framing inaction as the safe
   and easy choice

Root cause (M498): The AskUserQuestion option ordering and labeling in Step 6 Part B primes agents
toward recommending "Skip all" instead of presenting options neutrally.

## Parent Requirements

None — workflow correctness fix

## Expected vs Actual

- **Expected:** Agent presents deferred concerns neutrally; user decides without agent bias
- **Actual:** Agent adds "(Recommended)" to "Skip all" and lists it first, steering user toward inaction

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Option reordering only; no logic changes
- **Mitigation:** Change is confined to AskUserQuestion option ordering and labeling in one step

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` — reorder and relabel Step 6 Part B options

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Fix priming in Step 6 Part B AskUserQuestion in work-with-issue-agent/first-use.md:
  - Reorder options: "Create issues for selected concerns" first, "Skip all" second
  - Remove "no action needed" framing from "Skip all" label — use neutral description instead
    (e.g., "No tracking needed for these concerns")
  - Ensure no option is labeled with "(Recommended)" — let the user decide without agent bias
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`

## Post-conditions

- [ ] "Skip all" is NOT the first option in Step 6 Part B AskUserQuestion
- [ ] No option in Step 6 Part B uses "(Recommended)" or "no action needed" framing
- [ ] Options are presented in neutral order without positional or labeling bias
- [ ] E2E: Run /cat:work on an issue with stakeholder concerns; verify agent presents deferred concern
  options neutrally without recommending "Skip all"
