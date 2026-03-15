<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Plan

## Type

bugfix

## Goal

Implement prevention rules and enforcement mechanisms to prevent approval gate authorization bypass (M561). Ensure that only explicit AskUserQuestion option selection counts as valid approval for destructive operations (merge, force-push, branch deletion). Conversational signals ("continue", "ok", "yes", "proceed") must NOT be treated as authorization.

## Problem

The agent executed a merge to the target branch without explicit approval gate selection after the user interrupted the approval gate with a clarifying question. When the user resumed with "continue", the agent misinterpreted this conversational signal as authorization to proceed with the merge operation. The documented approval gate protocol requires explicit option selection from AskUserQuestion, but enforcement was missing and the distinction between "resume conversation" and "approve destructive operation" was not clearly defined.

## Post-conditions

When complete, all of the following must be true:

1. **Explicit approval rules documented**: `plugin/rules/approval-gate-protocol.md` exists and defines:
   - What counts as valid approval (explicit AskUserQuestion option selection only)
   - What does NOT count as approval (conversational signals, implicit consent, assumed permission)
   - Examples of both valid and invalid approval scenarios

2. **work-merge-agent rejects unauthorized merges**: `plugin/skills/work/first-use.md` (work-merge-agent skill) Step 9 contains:
   - Rejection detection logic: if `toolUseResult.answers` is empty OR doesn't match any presented option exactly, treat as gate rejection
   - Re-presentation requirement: after rejection, display full approval gate context again before proceeding to merge
   - Code prevents merge from proceeding without explicit option selection

3. **Project-level enforcement rules**: `.claude/rules/approval-gate-protocol.md` documents:
   - How to detect when approval gates have been interrupted or rejected
   - Mandatory re-presentation of gates after interruptions
   - Consequences of proceeding without explicit approval (approval gate violation)

4. **Safety-net gate in work-merge-agent Step 10**: Before executing merge, verify that Step 9 recorded explicit approval; if not, invoke final verification AskUserQuestion

5. **All tests pass**: Run full test suite to ensure no regressions from changes to work-merge-agent skill

## Implementation

### Wave 1: Create Approval Gate Protocol Rules

#### Step 1: Create `plugin/rules/approval-gate-protocol.md`

Document explicit rules for destructive operation approval:

- **Valid approval sources** (examples):
  - Explicit option selection from AskUserQuestion ("Approve and merge" button clicked)
  - User message containing specific confirmation phrase (to be documented per operation type)

- **Invalid approval sources** (examples):
  - Conversational signals: "continue", "ok", "yes", "go ahead", "proceed", "let's do it"
  - Resume signals: "continue with the workflow"
  - Implicit consent: assuming permission from prior decisions
  - Silence/timeout

- **Interruption handling**:
  - When approval gate is interrupted by non-option message, the gate is REJECTED
  - Rejection requires re-presentation of gate
  - No assumption of implicit approval after answering clarifying questions

- **Definition**: For work-merge-agent context, "explicit option selection" means user selecting one of the presented AskUserQuestion options (not just responding to the message)

#### Step 2: Create `.claude/rules/approval-gate-protocol.md`

Document project-level enforcement for approval gates:

- Approval gate re-presentation triggers:
  - User sends non-option message during approval gate
  - User interrupts with clarifying question
  - Conversation shifts or context changes

- Verification required before destructive operations:
  - Gate must be presented with full context
  - User must select explicit option
  - Conversational signals alone are insufficient

- Recovery from gate violation:
  - If violation is detected, invoke learn workflow
  - Document mistake and design prevention
  - Update enforcement rules to prevent recurrence

### Wave 2: Update work-merge-agent Skill

#### Step 3: Update `plugin/skills/work/first-use.md` - Step 9 (Approval Gate)

Add rejection detection logic (post-line ~399):

```
**Detect gate rejection:**

After AskUserQuestion invocation, check if user selected an option:
- If toolUseResult.answers is empty or null → GATE REJECTED
- If toolUseResult.answers does not match exactly any presented option → GATE REJECTED
- If toolUseResult.answers matches a presented option → GATE ACCEPTED

**Handle rejection:**

If GATE REJECTED:
  - Log: "User did not select approval option. Re-presenting approval gate."
  - Display: Full approval gate context again (what will happen, options, consequences)
  - Do NOT proceed to merge
  - Return to approval gate presentation (do not continue to Step 10)

If GATE ACCEPTED:
  - Extract approved option and log it
  - Set APPROVAL_MARKER=true for Step 10 verification
  - Continue to Step 10 (merge execution)
```

Document that conversational signals (continue, ok, yes) are NOT option selections and do NOT satisfy gate requirements.

#### Step 4: Update `plugin/skills/work/first-use.md` - Step 10 (Merge Execution)

Add pre-merge safety verification (pre-line ~475):

```
**Verify explicit approval before merge:**

If TRUST != "high" (i.e., trust is "medium" or "low"):
  - Check APPROVAL_MARKER from Step 9
  - If APPROVAL_MARKER != true:
    - Display warning: "Merge approval not verified. Re-presenting approval gate."
    - Return to Step 9 (do not execute merge)
  - If APPROVAL_MARKER == true:
    - Proceed to merge execution

If TRUST == "high":
  - Skip this check (high trust skips approval gate entirely)
  - Proceed directly to merge execution
```

This serves as a safety-net to catch any gate violations that slip through Step 9.

#### Step 5: Add comment to work-merge-agent explaining interruption handling

Add section documenting that conversational signals don't count as approval:

```markdown
## Approval Gate Interruption Handling

When a user interrupts the approval gate with a clarifying question:

1. The gate is considered REJECTED (no explicit option selected)
2. Answer the clarifying question
3. Re-present the full approval gate with all options
4. Wait for explicit option selection before proceeding to merge

**Examples of interruptions that require re-presentation:**
- User asks: "Why can't we do X?" (gate is rejected, needs re-presentation)
- User says: "continue" (conversational resume signal, not approval)
- User: "ok" or "yes" (conversational agreement, not explicit option selection)

Only explicit AskUserQuestion option selection (e.g., "Approve and merge" button) counts as valid approval.
```

### Wave 3: Testing

#### Step 6: Verify skill changes don't break work-merge-agent execution

Run full test suite to ensure changes to work-merge-agent don't introduce regressions:

```bash
mvn -f client/pom.xml test -Dtest=WorkMergeTest
```

Specific test scenarios to verify:
- Merge with explicit approval (trust=medium/low) succeeds
- Merge without approval (empty answers) is rejected
- Merge with conversational signal ("continue") is rejected
- Re-presentation of gate after rejection works correctly
- Merge with trust=high skips approval gate (high trust override)

#### Step 7: Manual verification with CAT workflow

Execute `/cat:work` on an issue and verify:
1. Approval gate is presented with clear options
2. Selecting an option executes merge
3. Interrupting with question and responding "continue" re-presents gate instead of merging
4. Non-option messages are rejected as approval

## Notes

- This prevention addresses M561 (approval gate authorization bypass)
- Prevention derived from learning workflow analysis of approval gate protocol violations
- Rules are enforceable in code (rejection detection) not just documentation
- Effort level: high (requires documentation, skill updates, testing, verification)
