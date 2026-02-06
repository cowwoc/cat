---
description: >
  MANDATORY: Use after ANY mistake to record learning, perform RCA, and implement prevention.
  Integrates token tracking for context-related failures.
---

# Learn From Mistakes: Thin Orchestrator

Analyze mistakes using 5-whys with CAT-specific consideration of conversation length and context
degradation. Integrates token tracking to identify context-related failures and recommend preventive
measures including earlier decomposition.

**Architecture:** Main agent orchestrates 4 phase subagents. Each phase runs in isolation with
its own context, keeping main agent context minimal.

## Purpose

Analyze mistakes using 5-whys with CAT-specific consideration of conversation length and context
degradation. Integrates token tracking to identify context-related failures and recommend preventive
measures including earlier decomposition.

## When to Use

- Any mistake during CAT orchestration
- Subagent produces incorrect/incomplete results
- Issue requires rework or correction
- Build/test/logical errors
- Repeated attempts at same operation
- Quality degradation over time

## Phase 1: Investigate

Verify event sequence and analyze documentation path to understand what caused the mistake.

Delegate to general-purpose subagent using the Task tool with these JSON parameters:

- **description:** `"Learn Phase 1: Investigate event sequence and documentation path"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values)

Prompt for the subagent:

> Execute the learn-investigate phase.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
>
> Load and follow: ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-investigate.md
>
> Your FINAL message must be ONLY the JSON result object — no surrounding text, no explanation.
> This is critical because the parent agent parses your response as JSON.

**Handle result:**

| Status | Action |
|--------|--------|
| COMPLETE | Store investigation results, continue to Phase 2 |
| ERROR | Display error, stop |
| No JSON / empty | Subagent failed to produce output - display error, stop |

**Parsing the result:** The subagent's final message is returned as text. Extract the JSON
object from it — look for `{` through the matching `}`. If the result contains surrounding text,
ignore the text and parse just the JSON block.

**Store phase 1 results:**
- `event_sequence`, `documents_read`, `priming_analysis`, `session_id`

## Phase 2: Analyze

Document the mistake, gather context metrics, perform RCA, and verify depth.

Delegate to general-purpose subagent using the Task tool with these JSON parameters:

- **description:** `"Learn Phase 2: Analyze mistake and perform RCA"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values and investigation results)

Prompt for the subagent:

> Execute the learn-analyze phase.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
>
> Investigation results from Phase 1:
> ```json
> {investigation_results_json}
> ```
>
> Load and follow: ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-analyze.md
>
> Your FINAL message must be ONLY the JSON result object — no surrounding text, no explanation.
> This is critical because the parent agent parses your response as JSON.

**Handle result:**

| Status | Action |
|--------|--------|
| COMPLETE | Store analysis results, continue to Phase 3 |
| ERROR | Display error, stop |
| No JSON / empty | Subagent failed to produce output - display error, stop |

**Store phase 2 results:**
- `mistake_description`, `context_metrics`, `root_cause`, `rca_method`, `rca_method_name`
- `rca_depth_verified`, `architectural_issue`, `recurrence_of`, `category`

## Phase 3: Prevent

Identify prevention strategies, evaluate quality, implement fixes, and verify no new priming.

Delegate to general-purpose subagent using the Task tool with these JSON parameters:

- **description:** `"Learn Phase 3: Implement prevention"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values and previous results)

Prompt for the subagent:

> Execute the learn-prevent phase.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
>
> Investigation results from Phase 1:
> ```json
> {investigation_results_json}
> ```
>
> Analysis results from Phase 2:
> ```json
> {analysis_results_json}
> ```
>
> Load and follow: ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-prevent.md
>
> Your FINAL message must be ONLY the JSON result object — no surrounding text, no explanation.
> This is critical because the parent agent parses your response as JSON.

**Handle result:**

| Status | Action |
|--------|--------|
| COMPLETE | Store prevention results, continue to Phase 4 |
| ERROR | Display error, stop |
| No JSON / empty | Subagent failed to produce output - display error, stop |

**Store phase 3 results:**
- `prevention_type`, `prevention_level`, `prevention_quality`
- `scenario_verified`, `existing_prevention_failed`, `files_modified`
- `prevention_description`, `priming_verified`, `related_files_checked`

## Phase 4: Record

Verify prevention works, record learning in MEMORY.md, update retrospective counter, and commit.

Delegate to general-purpose subagent using the Task tool with these JSON parameters:

- **description:** `"Learn Phase 4: Record learning and update retrospective"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values and all previous results)

Prompt for the subagent:

> Execute the learn-record phase.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
>
> Investigation results from Phase 1:
> ```json
> {investigation_results_json}
> ```
>
> Analysis results from Phase 2:
> ```json
> {analysis_results_json}
> ```
>
> Prevention results from Phase 3:
> ```json
> {prevention_results_json}
> ```
>
> Load and follow: ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-record.md
>
> Your FINAL message must be ONLY the JSON result object — no surrounding text, no explanation.
> This is critical because the parent agent parses your response as JSON.

**Handle result:**

| Status | Action |
|--------|--------|
| COMPLETE | Display summary, check retrospective trigger |
| ERROR | Display error, stop |
| No JSON / empty | Subagent failed to produce output - display error, stop |

**Store phase 4 results:**
- `learning_id`, `memory_updated`, `counter_updated`, `committed`
- `commit_hash`, `retrospective_triggered`, `retrospective_status`

## Summary Display

After all phases complete, display a summary:

```
Learning recorded: {learning_id}

Category: {category}
Root Cause: {root_cause}
RCA Method: {rca_method_name}

Prevention:
- Type: {prevention_type} (level {prevention_level})
- Files Modified: {count}
- Quality: {fragility} fragility, {verification_type} verification

Commit: {commit_hash}
{retrospective_status}
```

If `retrospective_triggered` is true, use AskUserQuestion to offer user choice:

```yaml
question: "Retrospective threshold exceeded. Run retrospective now?"
options:
  - label: "Run now"
    action: "Invoke /cat:run-retrospective immediately"
  - label: "Later"
    action: "Inform user to run /cat:run-retrospective when ready"
  - label: "Skip this cycle"
    action: "Reset counter without running"
```

## Examples

**For context analysis examples:** Read `EXAMPLES.md` for context-related, non-context, and ambiguous cases.

## Anti-Patterns

**For common mistakes when using this skill:** Read `ANTI-PATTERNS.md`.

Key anti-patterns to avoid:
- Stopping 5-whys too early (missing context degradation as root cause)
- Blaming context for non-context mistakes
- Implementing prevention without verification
- Recording documentation prevention when documentation already failed (escalate to hooks instead)

## Related Skills

- `cat:run-retrospective` - Aggregate analysis triggered by this skill
- `cat:token-report` - Provides data for context analysis
- `cat:decompose-issue` - Implements earlier decomposition
- `cat:monitor-subagents` - Catches context issues early
- `cat:collect-results` - Preserves progress before intervention

## A/B Test: RCA Method Comparison

**STATUS: ACTIVE** - See [RCA-AB-TEST.md](RCA-AB-TEST.md) for full specification.

### Current Test Parameters

- **Start:** M086
- **Methods:** A (5-Whys), B (Taxonomy), C (Causal Barrier)
- **Assignment:** Mistake ID modulo 3

### Milestone Reviews (MANDATORY)

At each milestone, run analysis and document decision:

| Milestone | Trigger | Action |
|-----------|---------|--------|
| 30 mistakes | M115 recorded | Run analysis, check for >2x difference |
| 60 mistakes | M145 recorded | Run analysis, check for >50% difference |
| 90 mistakes | M175 recorded | Final determination, lock in winner |

### Milestone Review Command

```bash
# Run at each milestone
MISTAKES_FILE=".claude/cat/retrospectives/mistakes.json"
START_ID=86

jq --argjson start "$START_ID" '
  [.mistakes[] | select((.id | ltrimstr("M") | tonumber) >= $start)] |
  group_by(.rca_method) |
  map({
    method: .[0].rca_method // "unassigned",
    count: length,
    recurrences: [.[] | select(.recurrence_of != null)] | length,
    recurrence_rate: (([.[] | select(.recurrence_of != null)] | length) / length * 100 | floor)
  }) |
  sort_by(.method)
' "$MISTAKES_FILE"
```

### Early Termination

If at 30 mistakes one method shows **>2x better recurrence rate** than control (Method A):

1. Verify recurrences had >14 days to manifest
2. Check improvement is consistent across categories
3. Confirm no confounding factors
4. If validated: declare winner, proceed to lock-in

### Lock-In Process

When winner determined:

1. Remove A/B test infrastructure from this skill
2. Keep only winning method as Step 3
3. Archive RCA-AB-TEST.md to `archive/` subdirectory
4. Update this section to document final result

## Error Handling

If any phase subagent fails unexpectedly:

1. Capture error message
2. Display error to user with phase context
3. Offer: Retry phase, Abort, or Manual intervention

## Success Criteria

- [ ] All 4 phases complete successfully
- [ ] Learning recorded in mistakes-YYYY-MM.json
- [ ] Retrospective counter updated
- [ ] Prevention implemented and committed
- [ ] Summary displayed to user
