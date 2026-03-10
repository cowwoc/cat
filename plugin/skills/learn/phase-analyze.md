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

Your final message must be ONLY the JSON result object with no surrounding text or explanation.
The parent agent parses your response as JSON.

## Input

You will receive a JSON object with investigation results containing:
- Event sequence and timeline
- Documents the agent read
- Priming analysis results
- Session ID
- Context metrics

**MANDATORY INPUT VALIDATION (BLOCKING GATE):**

Before performing any step, verify the Phase 1 input contains all required fields. If ANY required
field is absent or null, output ONLY the following JSON and STOP:

```json
{
  "phase": "analyze",
  "status": "BLOCKED",
  "reason": "missing required fields",
  "fields_received": ["list every top-level key that WAS present in the input"],
  "fields_missing": ["list every required key that was absent"]
}
```

Required fields that must be non-null and non-empty: event_sequence, documents_read, priming_analysis,
session_id, context_metrics. Do NOT substitute assumed values, empty objects, or "N/A" placeholders.
A BLOCKED response without both fields_received and fields_missing is invalid.

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

Use the context metrics that Phase 1 (Investigation) already gathered from the session. Phase 1
investigation includes token tracking as part of event sequence verification. Reference those values
directly.

## Step 3: Perform Root Cause Analysis

**Reference:** See [rca-method.md](rca-method.md) for the full method template.

**Method: Causal Barrier Analysis**
See [rca-method.md](rca-method.md) for the full template.

**Common root cause patterns to check:**
- Assumption without verification?
- Completion bias (rationalized ignoring rules)?
- Memory reliance (didn't re-verify)?
- Environment state mismatch?
- Documentation ignored (rule existed)?
- **Documentation priming?** - Did docs teach wrong approach?
- **Architectural flaw?** - Is LLM being asked to fight its training? (See Step 4d)

## Step 3a: Select Cause Signature

After completing RCA and before proceeding to the depth-verification gate, select a `cause_signature` from the
controlled vocabulary in [rca-method.md § Cause Signature Vocabulary](rca-method.md).

Select from the controlled vocabulary in `rca-method.md § Cause Signature Vocabulary`, then
combine into:
`<cause_type>:<barrier_type>:<context>`

**MANDATORY: Select ALL THREE components from the controlled vocabulary below. Do NOT invent values
outside these lists. If no value fits exactly, pick the closest match and note the discrepancy in
`root_cause`.**

**Valid cause_type values (pick exactly one):**
`compliance_failure` | `knowledge_gap` | `tool_limitation` | `environment_mismatch` |
`context_degradation` | `architectural_conflict`

**Valid barrier_type values (pick exactly one):**
`hook_absent` | `hook_bypassed` | `doc_missing` | `doc_ignored` | `validation_absent` |
`config_wrong` | `skill_incomplete` | `process_gap`

**Valid context values (pick exactly one):**
`pre_tool_use` | `post_tool_use` | `plugin_rules` | `skill_execution` | `git_operations` |
`file_operations` | `subagent_delegation` | `issue_workflow` | `rca_process`

**Signature comparison (recurrence detection):**

After selecting the candidate signature, check whether it matches any existing entry in
`mistakes-YYYY-MM.json`. Only do this AFTER completing Step 3b-R (Recurrence Independence Gate) —
signature comparison is a secondary check, not a substitute for independent RCA.

```yaml
signature_comparison:
  candidate_signature: "_______________"   # Fill in your selected signature
  matching_entries: []                     # List any existing entry IDs with same signature
  recurrence_detected: false              # Set true if matching_entries is non-empty
  recurrence_of: null                     # If recurrence detected, set to earliest matching entry ID
```

**Recurrence linkage workflow (automated, no user confirmation needed):**

1. Compare `candidate_signature` against entries in `mistakes-YYYY-MM.json`
2. If a match exists: set `recurrence_of` to the earliest matching entry ID
3. Apply the Prevention Strength Gate (see rca-method.md § Prevention Strength Gate)
4. Continue to Step 3b

This is an automated analysis step — the agent links recurrences directly without prompting
the user for confirmation.

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

**BLOCKING CONDITION:** Every `your_answer` field in `rca_depth_check` MUST be filled with a
substantive answer — at minimum 10 words, naming a specific file or document, not merely restating
the mistake. The JSON output field `rca_depth_evidence` must quote these answers verbatim. Outputting
`rca_depth_verified: true` without populated `rca_depth_evidence` answers is a protocol violation.

### Step 3b-R: Recurrence Independence Gate (BLOCKING GATE - M416)

**MANDATORY: Complete this gate to verify your RCA is independent before checking recurrence
history.**

You MUST verify your RCA independently before reading past conclusions from mistakes.json:

**ORDERING ENFORCEMENT:** You MUST NOT read `mistakes-YYYY-MM.json` or any prior mistake entry until
`step_1.your_independent_root_cause` is filled in below. Reading recurrence history before completing
step_1 contaminates the independence requirement — if you read recurrence data first, discard your
analysis and restart from step_1.

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

**Why this gate exists (M416):** When a mistake recurs, historical information about past mistake
chains makes it hard to analyze independently. This gate ensures you form your own conclusions from
source files first, before reading mistakes.json or past mistake entries. Recurrence checks happen
AFTER independent RCA is complete.

**BLOCKING CONDITION:** Do NOT proceed to Question 5 below until:
1. `step_1.your_independent_root_cause` is filled in with a cause based on reading source code ONLY
2. `step_3.your_answer` is filled in with recurrence information checked AFTER steps 1 and 2 are
   complete (never before)

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

**If investigation reveals multiple independent mistakes:** Read `MULTIPLE-MISTAKES.md` and follow its
workflow.

Each independent mistake gets its own `/cat:learn-agent` invocation with full RCA and prevention
implementation.

## Step 3d: Architectural Root Cause Analysis

**CRITICAL: Check for recurring patterns that indicate architectural flaws.**

When a mistake has recurrences (use `recurrence_of` from Step 3b-R step_3 output), independently
verify the root cause. Do NOT assume past analyses were correct -- they may have had wrong
conclusions that cascaded.

**Recurrence Chain Check:**

If Phase 1 investigation identified this as a recurring mistake (via `recurrence_of` field in
investigation output), reference those past mistake entries directly from Phase 1 to build your
recurrence chain. Do NOT attempt to query mistakes.json from within this phase.

**If 3+ recurrences exist for the same failure type (indicated by recurrence chain in Phase 1
output):**

Ask these architectural questions:

| Question | If YES |
|----------|--------|
| Is the LLM being asked to fight its training? | Use user-centric framing (see below) |
| Does the task require LLM intelligence? | If no, use preprocessing scripts |
| Does system prompt guidance conflict with task? | The task design is flawed |
| Are we asking for mechanical output? | Use user-centric framing + enforcement hooks |

**LLM Training Conflicts (M408 Pattern):**

LLMs are trained to be helpful, synthesize information, and be concise. Tasks that conflict with
this training will repeatedly fail despite documentation fixes:

| Task Type | Conflicts With | Solution |
|-----------|---------------|----------|
| Verbatim copy-paste | "Be concise" training | User-centric framing + enforcement hook |
| Mechanical formatting | Helpful synthesis | Use preprocessing scripts |
| Exact reproduction | Interpretation instinct | User-centric framing |
| Strict protocol following | Flexible helpfulness | Enforcement hooks |

**NOTE:** The `continue: false` + `stopReason` bypass pattern does NOT work. Claude Code adds
"Operation stopped by hook:" prefix to all stopReason values, making output appear like an error
message. Do not attempt to bypass the LLM for output - use user-centric framing instead.

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

**If mistake involves bypassing a hook:** Read `HOOK-WORKAROUNDS.md` and follow its investigation
checklist.

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
  "cause_signature": "<cause_type>:<barrier_type>:<context>",
  "rca_depth_verified": true,
  "rca_depth_check": {
    "action_level_why": "verbatim answer from Step 3b rca_depth_check.action_level_why.your_answer",
    "system_cause": "verbatim answer from Step 3b rca_depth_check.system_cause.your_answer",
    "external_change": "verbatim answer from Step 3b rca_depth_check.external_change.your_answer",
    "fix_type": "verbatim answer from Step 3b rca_depth_check.fix_type.your_answer",
    "fix_type_classification": "prevention | detection | mitigation"
  },
  "rca_depth_evidence": {
    "action_level_why": "(same as rca_depth_check.action_level_why)",
    "system_cause": "(same as rca_depth_check.system_cause)",
    "external_change": "(same as rca_depth_check.external_change)",
    "independent_root_cause": "verbatim answer from Step 3b-R step_1.your_independent_root_cause",
    "fix_type": "prevention | detection | mitigation"
  },
  "architectural_issue": false,
  "recurrence_of": null,
  "category": "mistake category from mistake-categories.md"
}
```

**BLOCKING OUTPUT RULE:** `rca_depth_verified` MUST be `true` only when ALL five fields in
`rca_depth_evidence` are populated with substantive answers (minimum 10 words each, naming a specific
file or document, not restating the mistake). A parent agent or reviewer can validate by checking that
`rca_depth_evidence` fields are non-empty and internally consistent with `root_cause`. Note: full
enforcement of content quality requires an external hook or reviewer; this is an accepted architectural
limitation of text-based instructions.
