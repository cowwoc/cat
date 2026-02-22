# Plan: smart-potentially-complete-check

## Goal
When work-prepare returns `potentially_complete: true`, automatically analyze whether the suspicious commits actually
implement the issue's goal before asking the user. If commits don't implement it, proceed to work without interruption.

## Current State
The work skill's "Potentially Complete Handling" section always displays suspicious commits and asks the user via
AskUserQuestion whether the issue is already complete. This creates unnecessary friction when the suspicious commits are
clearly unrelated (e.g., a bugfix to `work-prepare.py` that happens to mention the issue name in its dependency list
update).

## Target State
The work skill instructs the agent to read the suspicious commit diffs, compare them against the issue's PLAN.md goal,
and make an automated determination:
- If commits **do** implement the issue → ask user permission to mark as closed
- If commits **do not** implement the issue → proceed to Phase 2 automatically (no user question)

## Satisfies
User request: reduce unnecessary prompts when potentially_complete is a false positive.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Agent might incorrectly classify a commit as implementing/not-implementing
- **Mitigation:** Conservative approach — when uncertain, fall back to asking the user (current behavior)

## Files to Modify
- `plugin/skills/work/first-use.md` - Update "Potentially Complete Handling" section (lines 103-118)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps

### Step 1: Update the Potentially Complete Handling section
In `plugin/skills/work/first-use.md`, replace the current "Potentially Complete Handling" section (lines 103-118) with
the new logic:

1. When `potentially_complete: true`, read the suspicious commit diffs using
   `git show --stat <hash>` for each commit hash in `suspicious_commits`
2. Read the issue's PLAN.md goal section
3. Determine if the commits implement the issue's goal:
   - If YES (commits clearly address the goal) → ask user permission to close (same AskUserQuestion as current)
   - If NO (commits are unrelated or tangential) → log a note and proceed to Phase 2 automatically
   - If UNCERTAIN → fall back to asking the user (current behavior)

The key instruction should be concise since this is agent-executed logic, not a script. The agent reads the diffs,
reads the goal, and makes a judgment call.

## Post-conditions
- [ ] The "Potentially Complete Handling" section in `first-use.md` instructs the agent to analyze suspicious commits
      against the issue goal before prompting the user
- [ ] When commits clearly don't implement the issue, the flow proceeds without user interruption
- [ ] When commits do implement the issue, the user is still asked for confirmation before closing
- [ ] Uncertain cases fall back to asking the user (no silent false negatives)
