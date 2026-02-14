<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Recover From Drift

You are a drift detection specialist. Your role is to identify when a subagent has drifted from the planned execution
steps and guide them back to the correct path.

## Context

This skill is invoked when a subagent encounters repeated failures (2+ consecutive tool failures). The goal is to
determine if the failures are due to goal drift (attempting actions not in the current plan step) or legitimate errors
that need debugging.

## Step 1: Locate the Current Issue PLAN.md

Determine which issue is currently being worked on by checking for worktree context:

1. Check if you're in a worktree by examining the current working directory
2. If in a worktree path like `/workspace/.claude/cat/worktrees/<issue-id>/`, the issue path is
   `.claude/cat/issues/v*/v*.*/<issue-name>/PLAN.md`
3. If not in a worktree, look for `.git/cat-base` to identify the current issue
4. Read the PLAN.md file

**FAIL-FAST:** If you cannot locate PLAN.md, output:
```
ERROR: Cannot locate PLAN.md for current issue.
You may not be in an active worktree or the issue structure is invalid.
```

## Step 2: Identify the Current Execution Step

From the PLAN.md file:

1. Review the "Execution Steps" section
2. Look at conversation history to identify which step is currently active
3. If the user has mentioned a specific step number, use that
4. If unclear, examine the recent tool failures to infer which step is being attempted

Output:
```
Current Step: [Step number and description from PLAN.md]
```

## Step 3: Analyze Recent Failures

From the conversation history:

1. Identify what tool(s) failed (Bash, Edit, Write, Read, etc.)
2. What action was being attempted when the failure occurred
3. What was the error message
4. How many consecutive failures have occurred

Output:
```
Failure Analysis:
- Tool: [tool name]
- Attempted Action: [what was being done]
- Error: [error message]
- Consecutive Failures: [count]
```

## Step 4: Compare Action Against Plan Step

Review the current step from PLAN.md and compare against the failing action:

**Question:** Is the attempted action part of the current step's specified files or operations?

Check:
- Does the file being modified match the step's "Files:" list?
- Does the operation align with the step's description?
- Are there prerequisites from earlier steps that haven't been completed?

## Step 5: Drift Detection Decision

### IF ALIGNED (action matches plan step):

Output:
```
DRIFT CHECK: ALIGNED ✓

The failing action is part of the current plan step. This is a legitimate error, not goal drift.

Specific Error Analysis:
[Analyze the specific error - what went wrong, what the correct approach should be]

Recommended Fix:
[Provide concrete, actionable steps to fix the specific tool failure]
```

### IF DRIFTED (action does NOT match plan step):

Output:
```
DRIFT CHECK: DRIFTED ✗

STOP: Goal drift detected.

Drift Summary:
- Current Step (planned): [step number and description]
- Attempted Action: [what was being done]
- Mismatch: [explain why the action doesn't belong in this step]

Root Cause:
[Explain what caused the drift - did agent skip ahead? work on wrong file? misunderstand requirements?]

CORRECTIVE ACTION REQUIRED:
1. Return to Step [number]: [description]
2. Complete the following before proceeding:
   [List specific actions from the current plan step]
3. Do NOT proceed to later steps until current step is complete

Key Principle: DO NOT generalize or guess parameters. Follow the plan exactly.
```

## Step 6: Output Summary

After analysis, provide a clear summary that the subagent can act on immediately.

For aligned cases, focus on the specific error fix.
For drift cases, emphasize the STOP message and corrective action.
