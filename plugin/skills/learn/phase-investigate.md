<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Phase 1: Investigate

This phase verifies the event sequence and analyzes the documentation path to understand what caused the mistake.

## Your Task

Complete the investigation phase for the learn skill. Your final message must be ONLY the JSON result object with no
surrounding text or explanation. The parent agent parses your response as JSON.

## Pre-Extracted Investigation Context

**CRITICAL: Raw JSONL is the authoritative source for what the agent actually received.** Source files show what
*should* have been delivered; JSONL shows what *was* delivered. Any corruption, transformation, or injection invisible
in source files is visible in JSONL.

The learn skill's preprocessing provides pre-extracted investigation context as a convenience layer. The pre-extracted
data contains:

- `documents_read`: All files the agent Read during the session (path, tool, timestamp)
- `skill_invocations`: All skills invoked (skill name, args, timestamp)
- `bash_commands`: Bash commands matching the mistake keywords, with their stdout/stderr results. Each entry includes
  `line_number` (1-based JSONL line where the command appeared) and `result_truncated` (true if the result was cut off
  at 2000 characters — use the line number to retrieve the full entry from the session file if needed)
- `timeline_events`: Chronological list of significant events
- `timezone_context`: Container timezone (e.g., `TZ=UTC`)

**When using pre-extracted context:**

1. Use `documents_read` to identify which documents to look up in the JSONL (not to assume delivery was correct)
2. Use `skill_invocations` to identify which skills to look up in the JSONL
3. Use `bash_commands` to find the failing commands and their outputs — no grep/jq needed
4. Use `timeline_events` to reconstruct the event sequence
5. Use `timezone_context` to interpret timestamps — skip timezone investigation
6. Use session-analyzer on the raw JSONL to verify the content the agent actually received for any document that may
   be relevant to the mistake (see Step 2)

**Early termination rule:** Stop searching for evidence once you have enough to establish the timeline. If you have
found 3 or more positive matches for each evidence type (relevant commands, failure outputs, documents read), stop
searching — additional matches rarely change the root cause.

**Parallel reference file reads:** If you need to read reference files (scripts, skill files, agent definitions),
read all of them in a single parallel batch at the start of this phase rather than reading them one at a time as
needed.

## Step 1: Verify Event Sequence (MANDATORY)

**CRITICAL: Do NOT rely on memory for root cause analysis.**

Verify actual event sequence using get-history:

```bash
/cat:get-history
# Look for: When stated? Action order? User corrections? Actual trigger?
```

**Anti-Pattern:** Root cause analysis based on memory without get-history verification.
Memory is unreliable for causation, timing, attribution.

**If get-history unavailable:** Document analysis based on current context only, may be incomplete.

## Step 2: Examine Raw JSONL and Analyze Documentation Path

**CRITICAL: ALWAYS examine raw JSONL FIRST. Source files show what *should* be delivered; JSONL shows what
*was* delivered. Any corruption, transformation, or injection that happens between source files and agent delivery
is invisible when only reading source files.**

**NOTE:** `CLAUDE_SESSION_ID` is available in skill preprocessing but NOT exported to bash.
You must substitute the actual session ID value in bash commands, not use the variable reference.

```bash
# Replace YOUR-SESSION-ID with the actual session ID value
SESSION_ID="YOUR-SESSION-ID-HERE"
SESSION_ANALYZER="${WORKTREE_PATH}/client/target/jlink/bin/session-analyzer"
if [[ ! -x "$SESSION_ANALYZER" ]]; then
  SESSION_ANALYZER="/workspace/client/target/jlink/bin/session-analyzer"
fi
```

**Primary: Examine raw JSONL**

Use raw JSONL as the primary source to determine what the agent actually received.

**Session-analyzer commands** — choose the right one for your investigation need:

| Command | When to Use |
|---------|-------------|
| `search <session-id> <keyword> --context N` | Find specific content the agent received (skills, documents, prompts) |
| `errors <session-id>` | Find tool failures, non-zero exit codes, error patterns |
| `file-history <session-id> <path>` | Trace all reads/writes/edits to a specific file path |
| `analyze <session-id>` | Get full session overview including subagent discovery |

```bash
# Find what content was delivered for a specific skill or document
"$SESSION_ANALYZER" search "$SESSION_ID" "skill-name-or-keyword" --context 5

# Find tool errors that may have triggered the mistake
"$SESSION_ANALYZER" errors "$SESSION_ID"

# Get session overview with subagent discovery
"$SESSION_ANALYZER" analyze "$SESSION_ID"
```

**Subagent investigation:** If the mistake happened inside a subagent, the parent session JSONL does not contain the
subagent's full conversation. Use `analyze` to discover subagent IDs, then search their individual JSONL files:

```bash
# Discover subagent IDs (listed in analyze output under "subagents")
"$SESSION_ANALYZER" analyze "$SESSION_ID"

# Search a specific subagent's conversation
"$SESSION_ANALYZER" search "$SESSION_ID/subagents/agent-AGENT_ID" "keyword" --context 5
```

**What to verify in JSONL before looking at source files:**
- Did the skill/document content actually appear in the agent's context as expected?
- Was any content transformed, truncated, or corrupted during delivery?
- Did injection or preprocessing alter the content?
- If JSONL content differs from source files, flag the discrepancy explicitly

**Secondary: Compare with source files**

After JSONL examination, use source files for comparison only. Source files are SECONDARY evidence — used to compare
"expected vs actual":

```bash
# Cross-reference documents read with actual source files
"$SESSION_ANALYZER" search "$SESSION_ID" "Read" --context 2

# Find skill invocations
"$SESSION_ANALYZER" search "$SESSION_ID" "Skill" --context 2

# Find delegation prompts (Task tool invocations)
"$SESSION_ANALYZER" search "$SESSION_ID" "Task" --context 5
```

**For each document, check for priming patterns:**

| Pattern | Example | Risk |
|---------|---------|------|
| Algorithm before invocation | "How to compress: 1. Remove redundancy..." | Agent bypasses skill |
| Output format with values | "validation_score: 1.0 (required)" | Agent fabricates output |
| Cost/efficiency language | "This spawns 2 subagents..." | Agent takes shortcuts |
| Conflicting general guidance | "Be concise" + "copy verbatim" | General overrides specific |

**SKILL EXECUTION FAILURES - Use skill-builder:**

When the mistake involves an agent failing to execute a **skill** correctly (wrong output, skipped steps,
manual construction instead of preprocessing), analyze the skill using skill-builder:

```
Read the skill file and apply skill-builder's Priming Prevention Checklist:
- Information Ordering Check (does skill teach HOW before WHAT to invoke?)
- Output Format Check (does output format contain expected values?)
- Cost/Efficiency Language Check (does skill suggest proper approach is "expensive"?)
- Reference Information Check (does skill contain "for reference only" info?)
- No Embedded Box Drawings (does skill show visual examples that prime manual construction?)
```

**Reference:** See `/cat:skill-builder` § "Priming Prevention Checklist" for the complete checklist.
If skill has structural issues, fix the SKILL as part of prevention, not just add behavioral guidance.

**MANDATORY CHECK:** After checking documents read, ALSO ask:
1. **Could agent do the right thing?** Search for documentation of the CORRECT approach
2. **If no documentation exists:** Root cause is `missing_documentation`, not `assumption`
3. **If wrong approach is documented but right approach isn't:** Fix BOTH (remove priming AND add guidance)

**CHECK FOR CONFLICTING GUIDANCE:** Also check if general instructions conflict with specific requirements:
- Does system prompt say "be concise" while skill requires verbatim output?
- Does critical thinking prompt say "analyze" while skill requires copy-paste?
- Does general guidance favor interpretation while skill needs literal execution?
When found: The specific skill requirement should take precedence, but add enforcement (hook) since general
guidance may override documented specific requirements.

**For tool invocation errors:**

When a mistake involves invoking a tool/skill with wrong parameters:
1. Read the tool's actual interface (Parameters section, supported flags)
2. Compare against what was invoked
3. Check what documentation showed similar-looking parameters that may have primed the incorrect usage
4. The cause is often "saw parameter X used somewhere, assumed it applies to tool Y"

**For subagent mistakes:** Read `phase-investigate-subagent.md` (in the same directory as this file) for subagent-specific
investigation checks including delegation prompt analysis, technically impossible instructions, and missing skill
preloading.

**CRITICAL: Trace the FULL priming chain:**

When the main agent wrote a bad delegation prompt, ask: **What primed the MAIN AGENT to write that prompt?**

Common priming sources for main agent decisions:
1. **Previous subagent failure messages** - "excessive nesting" or "token budget" may prime bypasses
2. **Error messages from tools** - May suggest workarounds that violate protocols
3. **Cost/efficiency concerns in skill docs** - "This spawns N subagents" primes shortcuts

**Trace the chain backwards:**

```
Main agent wrote bad prompt
  ↑ WHY?
Previous subagent returned FAILED with message
  ↑ WHY did that message prime a bad decision?
Message described problem without actionable guidance
  ↑ FIX: Improve failure message guidance, not just main agent behavior
```

**Search session history for failure messages:**

```bash
# Find subagent failure messages that preceded the bad decision
"$SESSION_ANALYZER" search "$SESSION_ID" "FAILED" --context 5

# Find tool errors (non-zero exit codes, error patterns)
"$SESSION_ANALYZER" errors "$SESSION_ID"
```

**If a subagent failure message primed the main agent:**

The fix must address BOTH:
1. The main agent's behavior (don't bypass skills)
2. The failure message's guidance (provide actionable alternatives, not just problem description)

**If priming found:**

```yaml
documentation_priming:
  document: "{path to document OR 'Issue prompt'}"
  misleading_section: "{section name and line numbers OR 'OUTPUT FORMAT section'}"
  priming_type: "algorithm_exposure | output_format | cost_concern | internal_prompt | expected_value"
  how_it_misled: "Agent learned X, then applied it directly instead of invoking Y"
  fix_required: "Move content to internal-only document / Remove section / Restructure"
```

**Reference:** See [documentation-priming.md](documentation-priming.md) for detailed analysis patterns.

## Output Format

Your final message MUST be ONLY this JSON (no other text):

Valid `priming_type` values: `algorithm_exposure`, `output_format`, `cost_concern`, `conflicting_guidance`,
`impossible_instruction`, `missing_skill_preload`.

```json
{
  "phase": "investigate",
  "status": "COMPLETE",
  "user_summary": "1-3 sentence summary of what this phase found (for display to user between phases)",
  "event_sequence": {
    "timeline": ["Event 1", "Event 2", ...],
    "mistake_trigger": "What actually triggered the mistake"
  },
  "documents_read": [
    {"path": "/path/to/file", "type": "skill|doc|prompt"}
  ],
  "priming_analysis": {
    "priming_found": true,
    "document": "path or description",
    "priming_type": "see valid values above",
    "how_it_misled": "explanation"
  },
  "session_id": "actual-session-id"
}
```
