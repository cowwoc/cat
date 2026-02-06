# Phase 2: Analyze

This phase documents the mistake, gathers context metrics, performs root cause analysis, and verifies depth.

## Your Task

Complete the analysis phase for the learn skill. You will receive investigation results from Phase 1 as input.

Your final message must be ONLY the JSON result object with no surrounding text or explanation. The parent agent parses
your response as JSON.

## Input

You will receive a JSON object with investigation results containing:
- Event sequence and timeline
- Documents the agent read
- Priming analysis results
- Session ID

## Step 2: Document the Mistake

```yaml
mistake:
  timestamp: 2026-01-10T16:30:00Z
  type: incorrect_implementation
  description: |
    Subagent implemented parser with wrong precedence rules.
    Expressions like "a + b * c" parsed as "(a + b) * c" instead of "a + (b * c)".
  impact: |
    All tests using operator precedence failing. Required complete rewrite.
```

## Step 3: Gather Context Metrics

**CAT-specific: Always collect token data**

```bash
# Replace with actual subagent session ID (M359 - env vars not available in bash)
SESSION_ID="actual-subagent-session-id-here"
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"

TOKENS_AT_ERROR=$(jq -s 'map(select(.type == "assistant")) |
  map(.message.usage | .input_tokens + .output_tokens) | add' "${SESSION_FILE}")
COMPACTIONS=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")
MESSAGE_COUNT=$(jq -s '[.[] | select(.type == "assistant")] | length' "${SESSION_FILE}")
SESSION_DURATION=$(calculate_duration "${SESSION_FILE}")
```

## Step 4: Perform Root Cause Analysis

**Reference:** See [rca-methods.md](rca-methods.md) for detailed method specifications.

**A/B TEST IN PROGRESS** - See [RCA-AB-TEST.md](RCA-AB-TEST.md) for full specification.

**Method Assignment Rule:** Use mistake ID modulo 3:
- IDs where `N mod 3 = 0` → Method A (5-Whys)
- IDs where `N mod 3 = 1` → Method B (Taxonomy)
- IDs where `N mod 3 = 2` → Method C (Causal Barrier)

**Quick Reference:**

| Method | Core Approach | When Best |
|--------|---------------|-----------|
| A: 5-Whys | Ask "why" 5 times iteratively | General mistakes, process issues |
| B: Taxonomy | Classify into MEMORY/PLANNING/ACTION/REFLECTION/SYSTEM | Tool misuse, capability failures |
| C: Causal Barrier | List candidates, verify cause vs symptom, analyze barriers | Compliance failures, repeated mistakes |

**Common root cause patterns to check:**
- Assumption without verification?
- Completion bias (rationalized ignoring rules)?
- Memory reliance (didn't re-verify)?
- Environment state mismatch?
- Documentation ignored (rule existed)?
- **Documentation priming (M269)?** - Did docs teach wrong approach?
- **Architectural flaw (M408)?** - Is LLM being asked to fight its training? (See Step 4d)

**Record the method used** in the final JSON entry:

```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier"
}
```

## Step 4b: RCA Depth Verification (BLOCKING GATE - M299)

**MANDATORY: Verify RCA reached actual cause before proceeding.**

After completing Step 4, answer these questions:

```yaml
rca_depth_check:
  # Question 1: Did you ask WHY at the action level?
  action_level_why:
    question: "Why did the agent take THIS action instead of the correct one?"
    example_shallow: "Agent manually constructed instead of using template"
    example_deep: "SCRIPT OUTPUT CONTENT taught construction algorithm, priming manual approach"
    your_answer: "_______________"

  # Question 2: Can you trace to a SYSTEM/DOCUMENT cause?
  system_cause:
    question: "What in the system/documentation enabled or encouraged this mistake?"
    if_answer_is_nothing: "RCA is incomplete - keep asking why"
    if_answer_is_agent_error: "RCA is incomplete - what allowed agent to make this error?"
    your_answer: "_______________"

  # Question 3: Would the prevention CHANGE something external?
  external_change:
    question: "Does prevention modify code, config, or documentation?"
    if_answer_is_no: "RCA is incomplete - behavioral changes without enforcement recur"
    your_answer: "_______________"

  # Question 4: Is this a recurring failure? (M408)
  recurring_pattern:
    question: "Has this type of failure occurred before? Check recurrence_of in mistakes.json"
    if_yes_multiple: "Previous fixes FAILED - dig deeper into WHY they failed"
    if_3_plus_recurrences: "ARCHITECTURAL issue likely - see Step 4d"
    your_answer: "_______________"

  # Question 5: Prevention vs Detection (M422)
  fix_type:
    question: "Does your proposed fix PREVENT the problem, or DETECT/MITIGATE after it occurs?"
    prevention: "Makes the wrong thing impossible or the right thing automatic"
    detection: "Catches the mistake after it happens (verification layer, validation)"
    mitigation: "Reduces impact but doesn't stop occurrence (warnings, documentation)"
    if_detection_or_mitigation: |
      RCA is incomplete. You're treating symptoms, not cause.
      Ask: "WHY did the bad thing happen in the first place?"
      Keep asking WHY until you find something you can PREVENT.
    your_answer: "_______________"
    your_fix_type: "prevention | detection | mitigation"
```

**Prevention vs Detection Examples (M422):**

| Problem | Detection Fix (❌) | Prevention Fix (✅) |
|---------|-------------------|---------------------|
| Subagent fabricates scores | Verify independently | Remove expected values from prompts |
| Wrong file edited | Check file path after | Hook blocks edits to wrong paths |
| Threshold wrong | Validate on read | Fix the source template |
| Skill bypassed | Check if skill was invoked | Make skill the only path (hook) |

**Key insight:** If your first instinct is "add a check/verification", you haven't found the root cause.
The root cause is whatever made the wrong thing possible. Fix THAT.

**BLOCKING CONDITION (M299):**

If ANY answer is blank or says "agent should have...":
- STOP - RCA is incomplete
- Return to Step 4 and ask deeper "why" questions
- Investigate what DOCUMENTATION or SYSTEM enabled the mistake
- Only proceed when you can point to a SPECIFIC file to change

**Why this gate exists:** M299 showed that completion bias causes premature RCA termination.
Stopping at "agent did X wrong" is describing the SYMPTOM, not the CAUSE.
The cause is always in the system that allowed or encouraged the wrong action.

## Step 4c: Multiple Independent Mistakes (M378)

**If investigation reveals multiple independent mistakes:** Read `MULTIPLE-MISTAKES.md` and follow its workflow.

Each independent mistake gets its own `/cat:learn` invocation with full RCA and prevention implementation.

## Step 4d: Architectural Root Cause Analysis (M408)

**CRITICAL: Check for recurring patterns that indicate architectural flaws.**

When a mistake has recurrences (check `recurrence_of` field in mistakes.json), the fixes have failed.
Multiple failed fixes indicate the root cause is DEEPER than documentation or hooks can address.

**Recurrence Chain Check:**

```bash
# Find recurrence chains in mistakes
jq -r '.mistakes[] | select(.recurrence_of != null) |
  "\(.id) recurs \(.recurrence_of)"' "$MISTAKES_FILE"
```

**If 3+ recurrences exist for the same failure type:**

Ask these architectural questions:

| Question | If YES |
|----------|--------|
| Is the LLM being asked to fight its training? | Use user-centric framing (see below) |
| Does the task require LLM intelligence? | If no, use preprocessing scripts |
| Does system prompt guidance conflict with task? | The task design is flawed |
| Are we asking for mechanical output? | Use user-centric framing + enforcement hooks |

**LLM Training Conflicts (M408 Pattern):**

LLMs are trained to be helpful, synthesize information, and be concise. Tasks that conflict with this
training will repeatedly fail despite documentation fixes:

| Task Type | Conflicts With | Solution |
|-----------|---------------|----------|
| Verbatim copy-paste | "Be concise" training | User-centric framing + enforcement hook |
| Mechanical formatting | Helpful synthesis | Use preprocessing scripts |
| Exact reproduction | Interpretation instinct | User-centric framing |
| Strict protocol following | Flexible helpfulness | Enforcement hooks |

**NOTE (M410):** The `continue: false` + `stopReason` bypass pattern does NOT work.
Claude Code adds "Operation stopped by hook:" prefix to all stopReason values, making output appear
like an error message. Do not attempt to bypass the LLM for output - use user-centric framing instead.

**User-Centric Framing Pattern (M408 empirical finding):**

When LLM involvement is required but verbatim output is needed, use user-centric framing:

| Framing | Result | Why |
|---------|--------|-----|
| `The user wants you to respond with this text verbatim:` | ✅ Verbatim | Aligns with helpful training |
| `Echo this:` | ✅ Verbatim | Triggers mechanical execution mode |
| `MANDATORY: Copy-paste...` | ❌ Summarized | Triggers analytical/processing mode |
| `Your response must be:` | ❌ Questions | Triggers conversational mode |
| Content with no instruction | ❌ Interpreted | Default helpful behavior |

**Key insight:** User-centric framing ("The user wants...") leverages LLM training to be helpful.
Instructional framing ("MANDATORY", "must", "requirement") triggers interpretation. Keep prompts
minimal - remove all explanatory content that could prime analytical thinking.

**Record architectural findings:**

```json
{
  "category": "architectural_flaw",
  "root_cause": "ARCHITECTURAL: [explain the training conflict]",
  "immediate_fix": { "type": "...", "description": "..." },
  "deeper_fix_needed": {
    "type": "framing",
    "description": "Use user-centric framing with enforcement hook",
    "implementation": "User-centric prompt + PostToolUse validation hook"
  },
  "recurrence_chain": ["M001", "M002", "M003"]
}
```

## Step 4e: Investigate Hook Workarounds (M398)

**If mistake involves bypassing a hook:** Read `HOOK-WORKAROUNDS.md` and follow its investigation checklist.

Check: Was the right thing possible? Did guidance exist? Why wasn't it followed?

## Output Format

Your final message MUST be ONLY this JSON (no other text):

```json
{
  "phase": "analyze",
  "status": "COMPLETE",
  "user_summary": "1-3 sentence summary of what this phase found (for display to user between phases)",
  "mistake_description": {
    "timestamp": "ISO-8601",
    "type": "incorrect_implementation|protocol_violation|tool_misuse|etc",
    "description": "detailed description",
    "impact": "impact description"
  },
  "context_metrics": {
    "tokens_at_error": 95000,
    "compactions": 2,
    "message_count": 127,
    "session_duration_hours": 4.5
  },
  "root_cause": "The actual root cause from RCA",
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier",
  "rca_depth_verified": true,
  "architectural_issue": false,
  "recurrence_of": null,
  "category": "mistake category from mistake-categories.md"
}
```
