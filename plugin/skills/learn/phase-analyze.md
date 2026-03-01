<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Phase 2: Analyze

This phase documents the mistake, gathers context metrics, performs root cause analysis, and verifies depth.

## Your Task

Complete the analysis phase for the learn skill.

**IMPORTANT:** You will receive investigation results from Phase 1 as input.

Your final message must be ONLY the JSON result object with no surrounding text or explanation. The parent agent parses
your response as JSON.

## Input

You will receive a JSON object with investigation results containing:
- Event sequence and timeline
- Documents the agent read
- Priming analysis results
- Session ID

## Step 1: Document the Mistake

```yaml
mistake:
  timestamp: {ISO-8601 timestamp}
  type: {mistake type from categories}
  description: |
    {actual description of what happened from investigation or main agent}
  impact: |
    {impact of the mistake}
```

## Step 2: Gather Context Metrics

**CAT-specific: Always collect token data from investigation output**

Use the context metrics that Phase 1 (Investigation) already gathered from the session. Phase 1 investigation includes
token tracking as part of event sequence verification. Reference those values directly.

## Step 3: Perform Root Cause Analysis

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
- **Documentation priming?** - Did docs teach wrong approach?
- **Architectural flaw?** - Is LLM being asked to fight its training? (See Step 4d)

**Record the method used** in the final JSON entry:

```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier"
}
```

## Step 3b: RCA Depth Verification (BLOCKING GATE - M299)

**MANDATORY: Verify RCA reached actual cause before proceeding.**

After completing Step 3, answer these questions:

```yaml
rca_depth_check:
  # Question 1: Did you ask WHY at the action level?
  action_level_why:
    question: "Why did the agent take THIS action instead of the correct one?"
    example_shallow: "Agent manually constructed instead of using template"
    example_deep: "Output tag content taught construction algorithm, priming manual approach"
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
```

### Step 3b-R: Recurrence Independence Gate (BLOCKING GATE - M416)

**MANDATORY: Complete this gate to verify your RCA is independent before checking recurrence history.**

You MUST verify your RCA independently before reading past conclusions from mistakes.json:

```yaml
recurrence_independence_gate:
  step_1:
    action: "Complete your independent RCA using ONLY fresh evidence from this session"
    not_allowed:
      - "Referencing past mistake entries (M341, M353, etc.) as if they explain this occurrence"
      - "Reading mistakes.json or checking for recurrence_of until this step is complete"
    allowed: "Reading the source file/code directly to find the structural problem"
    your_independent_root_cause: "_______________"  # Fill this in FIRST

  step_2:
    action: "Only after step_1 is complete: Compare against Phase 1 investigation output"
    purpose: "Did Phase 1 identify the same root cause as your independent analysis?"
    questions:
      - "Does your finding match what Phase 1 discovered?"
      - "If they match: confirms the pattern is consistent"
      - "If they differ: your fresh analysis takes precedence"

  step_3:
    action: "Only after step_1 AND step_2 are complete: Check recurrence history"
    question: "Is this a recurring failure? Check recurrence_of in investigation output or mistakes.json"
    purpose: "Verify the pattern after independent analysis is complete"
    your_answer: "_______________"
```

**Why this gate exists (M416):** When a mistake recurs, historical information about past mistake chains
makes it hard to analyze independently. This gate ensures you form your own conclusions from source files
first, before reading mistakes.json or past mistake entries. Recurrence checks happen AFTER independent
RCA is complete.

**BLOCKING CONDITION:** Do NOT proceed to Question 5 below until:
1. `step_1.your_independent_root_cause` is filled in with a cause based on reading source code ONLY
2. `step_3.your_answer` is filled in with recurrence information checked AFTER steps 1 and 2 are complete
   (never before)

### Continuing Step 3b After Recurrence Gate

Proceed to Question 5 (Prevention vs Detection) after all steps 1-3 of the independence gate are complete.

```yaml
  # Question 5: Prevention vs Detection (after recurrence gate)
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

**Prevention vs Detection Examples:**

| Problem | Detection Fix (❌) | Prevention Fix (✅) |
|---------|-------------------|---------------------|
| Subagent fabricates scores | Verify independently | Remove expected values from prompts |
| Wrong file edited | Check file path after | Hook blocks edits to wrong paths |
| Threshold wrong | Validate on read | Fix the source template |
| Skill bypassed | Check if skill was invoked | Make skill the only path (hook) |

**Key insight:** If your first instinct is "add a check/verification", you haven't found the root cause.
The root cause is whatever made the wrong thing possible. Fix THAT.

**BLOCKING CONDITION:**

If ANY answer is blank or says "agent should have...":
- STOP - RCA is incomplete
- Return to Step 4 and ask deeper "why" questions
- Investigate what DOCUMENTATION or SYSTEM enabled the mistake
- Only proceed when you can point to a SPECIFIC file to change

**Why this gate exists:** M299 showed that completion bias causes premature RCA termination.
Stopping at "agent did X wrong" is describing the SYMPTOM, not the CAUSE.
The cause is always in the system that allowed or encouraged the wrong action.

## Step 3c: Multiple Independent Mistakes

**If investigation reveals multiple independent mistakes:** Read `MULTIPLE-MISTAKES.md` and follow its workflow.

Each independent mistake gets its own `/cat:learn` invocation with full RCA and prevention implementation.

## Step 3d: Architectural Root Cause Analysis

**CRITICAL: Check for recurring patterns that indicate architectural flaws.**

When a mistake has recurrences (use `recurrence_of` from Step 3b-R step_3 output), independently verify the root cause.
Do NOT assume past analyses were correct -- they may have had wrong conclusions that cascaded.

**Recurrence Chain Check:**

If Phase 1 investigation identified this as a recurring mistake (via `recurrence_of` field in investigation output),
reference those past mistake entries directly from Phase 1 to build your recurrence chain. Do NOT attempt to query
mistakes.json from within this phase.

**If 3+ recurrences exist for the same failure type (indicated by recurrence chain in Phase 1 output):**

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

**NOTE:** The `continue: false` + `stopReason` bypass pattern does NOT work.
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

## Step 3e: Investigate Hook Workarounds

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
    "tokens_at_error": "{from Phase 1 investigation output}",
    "compactions": "{from Phase 1 investigation output}",
    "message_count": "{from Phase 1 investigation output}",
    "session_duration_hours": "{from Phase 1 investigation output}"
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
