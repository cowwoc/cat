<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Learn From Mistakes: Orchestrator

**Architecture:** Main agent spawns a subagent that executes phases 1-3 (Investigate, Analyze, Prevent). The
main agent then passes the Phase 3 output directly to the `record-learning` CLI tool for Phase 4, eliminating
an LLM invocation for the purely mechanical recording work.

## Purpose

Investigate the root cause of a mistake and implement prevention so the mistake does not recur.

## When to Use

- Any mistake during CAT orchestration
- Subagent produces incorrect/incomplete results
- Issue requires rework or correction
- Build/test/logical errors
- Repeated attempts at same operation
- Quality degradation over time

## Step 1: Extract Investigation Context

Derive keywords from the mistake description (e.g., command names, file names, skill names mentioned in the mistake).
Then invoke the extraction skill with those keywords:

```
Skill tool:
  skill: "cat:extract-investigation-context-agent"
  args: "keyword1 keyword2 keyword3"
```

The skill runs the extractor invisibly via preprocessing and returns the pre-extracted JSON. Capture this output as
`PRE_EXTRACTED_CONTEXT`.

## Step 2: Decide Foreground vs Background

Determine execution mode before spawning the subagent:

- **BACKGROUND:** Learn was triggered mid-operation (while working on an issue via `/cat:work`) AND the learn results
  (recording to mistakes JSON, updating counter, committing prevention) do not affect the current issue's remaining git
  operations. Since prevention commits go to the cat fork point branch (not the issue worktree), background is always safe
  mid-operation.
- **FOREGROUND:** Learn was explicitly invoked standalone (no issue work in progress).

**Default:** Use background when mid-operation. Use foreground when standalone.

## Step 3: Spawn Subagent

**Background mode:** When background mode was selected in Step 2, inform the user before spawning:
"Running learn analysis in background — will notify when complete."
Then spawn the subagent with `run_in_background: true` (see Task tool parameters below). Control returns immediately
to the caller.

Delegate to general-purpose subagent using the Task tool with these JSON parameters:

- **description:** `"Learn: Execute phases 1-3 for mistake analysis"`
- **subagent_type:** `"general-purpose"`
- **model:** `"sonnet"`
- **prompt:** The prompt below (substitute variables with actual values)
- **run_in_background:** `true` if background mode was selected in Step 2, omit otherwise

**Subagent prompt:**

> Execute the learn skill phases for mistake analysis.
>
> SESSION_ID: ${CLAUDE_SESSION_ID}
> PROJECT_DIR: ${CLAUDE_PROJECT_DIR}
>
> **Your task:** Execute phases in sequence: Investigate → Analyze → Prevent
>
> **CRITICAL:** You MUST read and execute each phase file below. Do NOT summarize prior conversation
> history or reuse results from earlier sessions. Each phase file contains instructions you must follow
> step by step. Start by reading Phase 1's file with the Read tool.
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
>
> **Pre-Extracted Investigation Context (Starting-Point Index Only):**
> ```json
> ${PRE_EXTRACTED_CONTEXT}
> ```
> **IMPORTANT:** This context is a starting-point index to help you navigate the JSONL file more efficiently. It is NOT the authoritative source of events or evidence. JSONL is the authoritative source for what the agent actually received. Always verify critical findings by searching the JSONL directly (using session-analyzer), especially when investigating priming, documentation corruption, or timeline discrepancies.
>
> **Anti-fabrication rule:** `prevention_commit_hash` and `prevention_implemented`
> MUST reflect actual actions taken. Do NOT fill in plausible-looking values. If no commit was made, set the
> hash field to `null`. If prevention was not implemented, set `prevention_implemented` to `false`. Fabricated
> values corrupt the learning record and will be discovered on review.
>
> **Your FINAL message must be ONLY the JSON result object below — no surrounding text, no explanation.**
> This is critical because the parent agent parses your response as JSON.
>
> ```json
> {
>   "phases_executed": ["investigate", "analyze", "prevent"],
>   "phase_summaries": {
>     "investigate": "1-3 sentence summary for user",
>     "analyze": "1-3 sentence summary for user",
>     "prevent": "1-3 sentence summary for user"
>   },
>   "investigate": { ...phase 1 output fields... },
>   "analyze": { ...phase 2 output fields... },
>   "prevent": { ...phase 3 output fields... }
> }
> ```

## Step 4: Run record-learning CLI (Phase 4)

**Note:** If the subagent was spawned in background (Step 2), Steps 4-7 execute when the background task
notification arrives, not immediately after Step 3.

After the subagent completes, pass the Phase 3 output to the `record-learning` CLI tool:

```bash
# Write Phase 3 output to temp file
PHASE3_TMP=$(mktemp /tmp/phase3-output.XXXXXX.json)
cat > "$PHASE3_TMP" << 'PHASE3_EOF'
{...prevent phase JSON output from subagent...}
PHASE3_EOF

# Run the record-learning tool — reads Phase 3 JSON from stdin, outputs recording result JSON to stdout
RECORD_RESULT=$("${CLAUDE_PLUGIN_ROOT}/client/bin/record-learning" < "$PHASE3_TMP")
RECORD_EXIT=$?
rm -f "$PHASE3_TMP"

if [[ $RECORD_EXIT -ne 0 ]]; then
  echo "ERROR: record-learning failed (exit $RECORD_EXIT): $RECORD_RESULT"
  exit 1
fi
```

The tool outputs JSON with fields: `learning_id`, `counter_status`, `retrospective_trigger`, `commit_hash`.
Capture this as the Phase 4 result and proceed to Step 5.

**Error handling for Step 4:**

| Condition | Action |
|-----------|--------|
| `record-learning` exits non-zero | Display error output, stop |
| Output is not valid JSON | Display raw output with error, stop |

## Step 5: Display Phase Summaries

After Phase 4 completes, parse the results and display each phase summary to the user:

```
Phase: Investigate
{investigate.user_summary or phase_summaries.investigate}

Phase: Analyze
{analyze.user_summary or phase_summaries.analyze}

Phase: Prevent
{prevent.user_summary or phase_summaries.prevent}

Phase: Record
Learning {learning_id} recorded. {counter_status.count}/{counter_status.threshold} mistakes since last retrospective.
```

**Error handling:**

| Condition | Action |
|-----------|--------|
| Subagent returns no JSON | Display error, stop |
| JSON missing required fields | Display error with details, stop |
| Phase status is ERROR | Display error from that phase, stop |

## Step 6: Create Follow-up Issue

**MANDATORY when `prevent.prevention_implemented` is false. Skip this step entirely if
`prevent.prevention_implemented` is true.**

When `prevention_implemented` is false, the subagent could not commit prevention because the current branch is
protected. A follow-up issue must be created so the prevention is not lost.

1. **Validate `issue_creation_info` before proceeding.** Verify that:
   - `issue_creation_info` is present and non-empty in the prevent phase output
   - `issue_creation_info.suggested_title` is a non-empty string
   - `issue_creation_info.suggested_description` is a non-empty string
   - `issue_creation_info.suggested_acceptance_criteria` is a non-empty array

   If any field is missing or empty, display:
   ```
   Error: Cannot create follow-up issue — issue_creation_info is incomplete.
   Missing fields: [list the missing field names]
   Please create the issue manually using /cat:add.
   Suggested title: {suggested_title or "(not provided)"}
   Suggested description: {suggested_description or "(not provided)"}
   Suggested acceptance criteria:
   {render each element of suggested_acceptance_criteria as "- {element}", or "(not provided)" if absent}
   ```
   Then continue to Step 7.

2. Display to user: "Prevention requires code changes that cannot be committed on protected branch. Creating
   follow-up issue."

3. Invoke `/cat:add-agent {suggested_title}` where `{suggested_title}` is the one-line summary from
   `issue_creation_info.suggested_title`. When cat:add-agent prompts for more detail, provide
   `suggested_description` as the description and `suggested_acceptance_criteria` as the acceptance criteria.

   If `cat:add-agent` fails or returns an error, display:
   ```
   Error: Failed to create follow-up issue via cat:add-agent.
   You can create the issue manually using /cat:add with the following values:
   Title: {suggested_title}
   Description: {suggested_description}
   Acceptance criteria: {suggested_acceptance_criteria}
   ```

## Step 7: Display Final Summary

After all phases complete, display a summary of results:

```
Learning recorded: {learning_id}

Category: {category}
Root Cause: {root_cause}
RCA Method: {rca_method_name}
Cause Signature: {cause_signature}

Prevention:
- Type: {prevention_type} (level {prevention_level})
- Files Modified: {count}
- Quality: {fragility} fragility, {verification_type} verification

Commit: {commit_hash}
{retrospective_status}
```

If `retrospective_trigger` is true, use AskUserQuestion to offer user choice:

```yaml
question: "Retrospective threshold exceeded. Run retrospective now?"
options:
  - label: "Run now"
    action: "Invoke /cat:retrospective-agent immediately"
  - label: "Later"
    action: "Inform user to run /cat:retrospective when ready"
  - label: "Skip this cycle"
    action: "Reset counter without running"
```

## Cause Signature

Each mistake recorded should include a `cause_signature` field in the analyze phase output JSON. This structured
triple links mistakes that share the same root cause pattern even when they manifest differently across sessions or
tools.

**Format:** `<cause_type>:<barrier_type>:<context>`

**Example:** `"cause_signature": "compliance_failure:hook_absent:file_operations"`

When Phase 2 selects a `cause_signature`, it compares it against existing entries in `mistakes-YYYY-MM.json`. A
matching entry triggers recurrence detection — setting `recurrence_of` to the earliest matching entry ID.

**Full vocabulary, canonical examples, and selection process:** See `rca-methods.md § Cause Signature Vocabulary`.

## Examples

**For context analysis examples:** Read `EXAMPLES.md` for context-related, non-context, and ambiguous cases.

## Anti-Patterns

**For common mistakes when using this skill:** Read `ANTI-PATTERNS.md`.

Key anti-patterns to avoid:
- Stopping barrier analysis too early (missing context degradation as root cause)
- Blaming context for non-context mistakes
- Implementing prevention without verification
- Recording documentation prevention when documentation already failed (escalate to hooks instead)

## Related Skills

- `cat:retrospective` - Aggregate analysis triggered by this skill
- `cat:token-report-agent` - Provides data for context analysis
- `cat:decompose-issue-agent` - Implements earlier decomposition
- `cat:get-subagent-status` - Catches context issues early
- `cat:collect-results-agent` - Preserves progress before intervention

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
