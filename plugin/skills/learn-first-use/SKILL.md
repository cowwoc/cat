---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Learn From Mistakes: Tiered Orchestrator

Analyze mistakes using 5-whys with CAT-specific consideration of conversation length and context
degradation. Integrates token tracking to identify context-related failures and recommend preventive
measures including earlier decomposition.

**Architecture:** Main agent classifies mistake into tier (quick/deep), spawns single subagent that
loads appropriate phase files and executes all phases in one context.

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

## Step 1: Classify Mistake Tier

Determine whether this is a quick-tier or deep-tier mistake.

**Classification Logic:**

| Tier | Criteria | Phases to Run | Rationale |
|------|----------|---------------|-----------|
| **Quick** | `protocol_violation` category OR `recurrence_of` is set | Analyze, Prevent, Record (skip Investigate) | Known pattern, investigation already done |
| **Deep** | All other cases | Investigate, Analyze, Prevent, Record (all 4) | Novel failure, needs full investigation |

**How to determine:**

1. Ask user for mistake category (if not already provided in invocation)
2. Ask if this is a recurrence of a previous mistake (check mistakes.json if needed)
3. Apply classification logic above

**Store tier decision:**
- `tier: "quick" | "deep"`
- `phases_to_run: ["analyze", "prevent", "record"] | ["investigate", "analyze", "prevent", "record"]`

## Step 2: Spawn Single Tiered Subagent

Delegate to general-purpose subagent using the Task tool with these JSON parameters:

- **description:** `"Learn ({tier} tier): Execute all phases for mistake analysis"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values)

**Prompt for quick-tier subagent:**

> Execute the learn skill phases for a quick-tier mistake.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
> TIER: quick
>
> **Your task:** Execute phases in sequence: Analyze → Prevent → Record
>
> For each phase:
> 1. Use the Read tool to load the phase file from ${CLAUDE_PLUGIN_ROOT}/skills/learn/
> 2. Follow the instructions in that phase file
> 3. Generate a user_summary (1-3 sentences) of what you found/did
> 4. Include the phase result in your final JSON output
>
> **Phase files to load:**
> - Phase 2 (Analyze): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-analyze.md
> - Phase 3 (Prevent): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-prevent.md
> - Phase 4 (Record): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-record.md
>
> **Your FINAL message must be ONLY the JSON result object below — no surrounding text, no explanation.**
> This is critical because the parent agent parses your response as JSON.
>
> ```json
> {
>   "tier": "quick",
>   "phases_executed": ["analyze", "prevent", "record"],
>   "phase_summaries": {
>     "analyze": "1-3 sentence summary for user",
>     "prevent": "1-3 sentence summary for user",
>     "record": "1-3 sentence summary for user"
>   },
>   "analyze": { ...phase 2 output fields... },
>   "prevent": { ...phase 3 output fields... },
>   "record": { ...phase 4 output fields... }
> }
> ```

**Prompt for deep-tier subagent:**

> Execute the learn skill phases for a deep-tier mistake.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
> TIER: deep
>
> **Your task:** Execute phases in sequence: Investigate → Analyze → Prevent → Record
>
> For each phase:
> 1. Use the Read tool to load the phase file from ${CLAUDE_PLUGIN_ROOT}/skills/learn/
> 2. Follow the instructions in that phase file
> 3. Generate a user_summary (1-3 sentences) of what you found/did
> 4. Include the phase result in your final JSON output
> 5. Pass results from previous phases as input to subsequent phases (as documented in phase files)
>
> **Phase files to load:**
> - Phase 1 (Investigate): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-investigate.md
> - Phase 2 (Analyze): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-analyze.md
> - Phase 3 (Prevent): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-prevent.md
> - Phase 4 (Record): ${CLAUDE_PLUGIN_ROOT}/skills/learn/phase-record.md
>
> **Your FINAL message must be ONLY the JSON result object below — no surrounding text, no explanation.**
> This is critical because the parent agent parses your response as JSON.
>
> ```json
> {
>   "tier": "deep",
>   "phases_executed": ["investigate", "analyze", "prevent", "record"],
>   "phase_summaries": {
>     "investigate": "1-3 sentence summary for user",
>     "analyze": "1-3 sentence summary for user",
>     "prevent": "1-3 sentence summary for user",
>     "record": "1-3 sentence summary for user"
>   },
>   "investigate": { ...phase 1 output fields... },
>   "analyze": { ...phase 2 output fields... },
>   "prevent": { ...phase 3 output fields... },
>   "record": { ...phase 4 output fields... }
> }
> ```

## Step 3: Display Phase Summaries

After the subagent completes, parse the result JSON and display each phase summary to the user:

```
Phase: Investigate
{investigate.user_summary or phase_summaries.investigate}

Phase: Analyze
{analyze.user_summary or phase_summaries.analyze}

Phase: Prevent
{prevent.user_summary or phase_summaries.prevent}

Phase: Record
{record.user_summary or phase_summaries.record}
```

**If prevent.prevention_implemented is false:**

The prevent phase could not implement prevention directly because the current branch is protected and the prevention
requires source code changes. Create a CAT issue from the task_creation_info:

1. Display to user: "Prevention requires code changes that cannot be committed on protected branch {branch}. Creating
   follow-up issue."
2. The task_creation_info from the prevent phase contains suggested_title, suggested_description, and
   suggested_acceptance_criteria
3. Continue to Step 4 (Display Final Summary) - note that prevention_implemented is false in the summary

**Error handling:**

| Condition | Action |
|-----------|--------|
| Subagent returns no JSON | Display error, stop |
| JSON missing required fields | Display error with details, stop |
| Phase status is ERROR | Display error from that phase, stop |

## Step 4: Display Final Summary

After all phases complete, display a summary showing tier used and results:

```
Learning recorded: {learning_id}
Tier: {tier} ({phases_skipped} if quick tier)

Category: {category}
Root Cause: {root_cause}
RCA Method: {rca_method_name}

Prevention:
- Type: {prevention_type} (level {prevention_level})
- Files Modified: {count}
- Quality: {fragility} fragility, {verification_type} verification

Commit: {commit_hash}
{retrospective_status}

Token Efficiency: {tier}-tier analysis (skipped {N} phase(s) for known pattern)
```

**Token savings note for quick tier:**
- Quick tier skips investigation phase (known pattern)
- Typical savings: ~15-20K tokens per learning session
- Use for protocol violations and recurrences

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
