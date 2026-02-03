---
description: >
  MANDATORY: Use after ANY mistake to record learning, perform RCA, and implement prevention.
  Integrates token tracking for context-related failures.
---

# Learn From Mistakes (CAT-Specific)

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

## Workflow

### 1. Verify Event Sequence (MANDATORY)

**CRITICAL: Do NOT rely on memory for root cause analysis.**

Verify actual event sequence using get-history:

```bash
/cat:get-history
# Look for: When stated? Action order? User corrections? Actual trigger?
```

**Anti-Pattern (M037):** Root cause analysis based on memory without get-history verification.
Memory is unreliable for causation, timing, attribution.

**If get-history unavailable:** Document analysis based on current context only, may be incomplete.

### 1b. Analyze Documentation Path (M269, M274, M381)

**CRITICAL: ALWAYS check documentation path FIRST after collecting history.**

**MANDATORY FIRST STEP (M381):** Before any other analysis, identify what documents/skills the agent
read and check if they caused the mistake. Do NOT skip to "agent error" conclusions without first
checking if documentation primed the wrong behavior.

Using the session history from Step 1, identify all documents the agent read.

**NOTE (M359):** `CLAUDE_SESSION_ID` is available in skill preprocessing but NOT exported to bash.
You must substitute the actual session ID value in bash commands, not use the variable reference.

```bash
# Replace with actual session ID - do NOT use ${CLAUDE_SESSION_ID} in bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/YOUR-SESSION-ID-HERE.jsonl"

# Note: Tool uses are nested inside assistant messages as content blocks
# Structure: {type: "assistant", message: {content: [{type: "tool_use", name: "...", input: {...}}]}}

# Find documents read
echo "=== Documents Read ==="
grep '"type":"assistant"' "$SESSION_FILE" | \
  jq -r '.message.content[]? | select(.type == "tool_use") |
    select(.name == "Read" or .name == "Skill") |
    if .name == "Read" then .input.file_path
    else "skill:" + .input.skill end' 2>/dev/null | sort -u

# Find skill invocations vs expected
echo "=== Skill Invocations ==="
grep '"type":"assistant"' "$SESSION_FILE" | \
  jq -r '.message.content[]? | select(.type == "tool_use" and .name == "Skill") |
    .input.skill + " " + (.input.args // "")' 2>/dev/null

# Find Issue prompts (delegation prompts are documents too!) (M274)
echo "=== Issue Delegation Prompts ==="
grep '"type":"assistant"' "$SESSION_FILE" | \
  jq -r '.message.content[]? | select(.type == "tool_use" and .name == "Issue") |
    "Issue: " + .input.description + "\n" + .input.prompt' 2>/dev/null
```

**For each document, check for priming patterns:**

| Pattern | Example | Risk |
|---------|---------|------|
| Algorithm before invocation | "How to compress: 1. Remove redundancy..." then "invoke skill" | Agent learns to bypass skill |
| Output format exposure | "Expected: \| File \| Score \|..." | Agent fabricates output |
| Cost/efficiency concerns | "This spawns 2 subagents..." | Agent takes shortcuts |
| Internal prompts exposed | "Agent Prompt Template: ..." | Agent applies directly |
| Expected value in output (M274) | "OUTPUT FORMAT: validation_score: 1.0 (required)" | Agent reports expected value, not actual |
| Rendered output example (M320) | "The banner looks like: ┌─ 🐱..." | Agent constructs instead of running script |
| Parameter inference from context (M381) | Skill shows `${WORKTREE_PATH}` for Task prompts | Agent invents flags for other tools |

**For tool invocation errors (M381):**

When a mistake involves invoking a tool/skill with wrong parameters:
1. Read the tool's actual interface (Parameters section, supported flags)
2. Compare against what was invoked
3. Check what documentation showed similar-looking parameters that may have primed the incorrect usage
4. The cause is often "saw parameter X used somewhere, assumed it applies to tool Y"

**For subagent mistakes, ALSO check the Issue prompt that spawned it (M274):**

The delegation prompt IS the primary "document" the subagent received. Check it for:
- Expected values embedded in output format (e.g., "score: 1.0 (required)")
- Outcome requirements that conflict with reality (e.g., "MUST be 1.0")
- Any content telling the subagent what to report vs what to measure

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

### 2. Document the Mistake

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

### 3. Gather Context Metrics

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

### 4. Perform Root Cause Analysis

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

**Record the method used** in the final JSON entry:

```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier"
}
```

### 4b. RCA Depth Verification (BLOCKING GATE - M299)

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
```

**BLOCKING CONDITION (M299):**

If ANY answer is blank or says "agent should have...":
- STOP - RCA is incomplete
- Return to Step 4 and ask deeper "why" questions
- Investigate what DOCUMENTATION or SYSTEM enabled the mistake
- Only proceed when you can point to a SPECIFIC file to change

**Why this gate exists:** M299 showed that completion bias causes premature RCA termination.
Stopping at "agent did X wrong" is describing the SYMPTOM, not the CAUSE.
The cause is always in the system that allowed or encouraged the wrong action.

### 4c. Multiple Independent Mistakes (M378)

**When investigating a problem reveals multiple independent mistakes, invoke `/cat:learn` for each.**

During investigation, you may discover that the observed failure resulted from multiple independent
issues - not just multiple causes of one mistake, but genuinely separate mistakes that each warrant
their own RCA and prevention.

**Identification pattern:**

```yaml
multiple_mistakes_check:
  question: "Are these separate issues that could occur independently?"
  if_yes: "Invoke /cat:learn separately for EACH mistake"
  if_no: "Continue with single /cat:learn for the one mistake"
```

**When multiple independent mistakes are discovered:**

1. **Complete the current `/cat:learn`** for the first mistake you identified
2. **Invoke `/cat:learn` again** for each additional independent mistake
3. **Each gets its own M-number**, RCA, and prevention

**Example:**

```yaml
# Observed failure: Batch compression failed midway through
# Investigation reveals TWO independent mistakes:

mistake_1:
  description: "Handler didn't exist for /cat:delegate preprocessing"
  action: "Complete /cat:learn -> M377"

mistake_2:
  description: "Agent didn't acknowledge user message mid-operation"
  action: "Invoke /cat:learn again -> M378"

# Each mistake gets separate /cat:learn invocation with full RCA workflow
```

**Why separate invocations matter:**
- Each mistake may have different root causes requiring different RCA methods
- Each prevention needs its own verification
- Retrospective tracking is more accurate with distinct M-numbers
- Patterns are easier to identify when mistakes are properly separated

**BLOCKING: Do not bundle unrelated mistakes.** If you discover a second independent issue during
investigation, note it, complete the current `/cat:learn`, then invoke `/cat:learn` again for the
second issue.

### 5. Check for Context Degradation Patterns

**CAT-specific analysis checklist:**

Reference: agent-architecture.md § Context Limit Constants

```yaml
context_degradation_analysis:
  tokens_at_error: 95000
  threshold_exceeded: true
  threshold_exceeded_by: 15000
  compaction_events: 2
  errors_after_compaction: true
  session_duration: 4.5 hours
  messages_before_error: 127
  early_session_quality: high
  late_session_quality: degraded
  quality_degradation_detected: true
  context_related: LIKELY
  confidence: 0.85
```

### 6. Identify Prevention Level

**Reference:** See [prevention-hierarchy.md](prevention-hierarchy.md) for detailed hierarchy and escalation rules.

**Quick Reference:**

| Level | Type | Description |
|-------|------|-------------|
| 1 | code_fix | Make incorrect behavior impossible in code |
| 2 | hook | Automated enforcement via PreToolUse/PostToolUse |
| 3 | validation | Automated checks that catch mistakes early |
| 4 | config | Configuration or threshold changes |
| 5 | skill | Update skill documentation with explicit guidance |
| 6 | process | Change workflow steps or ordering |
| 7 | documentation | Document to prevent future occurrence (weakest) |

**Key principle:** Lower level = stronger prevention. Always prefer level 1-3 over level 5-7.

### 7. Evaluate Prevention Quality

**BEFORE implementing, verify the prevention is robust:**

```yaml
prevention_quality_check:
  verification_type:
    positive: "Check for PRESENCE of correct behavior"  # ✅ Preferred
    negative: "Check for ABSENCE of specific failure"   # ❌ Fragile

  # Ask: Am I checking for what I WANT, or what I DON'T want?
  # Example:
  #   ❌ grep "Initial implementation -"  (catches ONE placeholder pattern)
  #   ✅ grep "^- \`[a-f0-9]{7,}\`"        (checks for correct commit format)

  generality:
    question: "If the failure mode varies slightly, will this still catch it?"
    examples:
      - "What if placeholder text changes from 'Initial' to 'First'?"
      - "What if someone uses 'TBD' or 'TODO' instead?"
      - "What if the format is subtly wrong in a different way?"
    # If answer is NO → prevention is too specific → redesign

  inversion:
    question: "Can I invert this check to verify correctness instead?"
    pattern: |
      Instead of: "Fail if BAD_PATTERN exists"
      Try:        "Fail if GOOD_PATTERN is missing"
    # Positive verification catches ALL failures, not just anticipated ones

  fragility_assessment:
    low:    "Checks for correct format/behavior (positive verification)"
    medium: "Checks for category of errors (e.g., any TODO-like text)"
    high:   "Checks for exact observed failure (specific string match)"
```

**Decision gate:** If fragility is HIGH, redesign the prevention before implementing.

### 7b. Replay Scenario Verification (BLOCKING GATE - M305)

**MANDATORY: Verify prevention would have prevented THIS specific problem.**

Before proceeding, mentally replay the exact scenario that caused the mistake:

```yaml
scenario_replay:
  # Step 1: Describe the exact sequence that led to the mistake
  what_happened:
    step_1: "{first action/state}"
    step_2: "{second action/state}"
    step_3: "{action where mistake occurred}"
    result: "{the bad outcome}"

  # Step 2: Insert your proposed prevention and replay
  with_prevention:
    step_1: "{same first action/state}"
    step_2: "{same second action/state}"
    prevention_activates: "{when/how does prevention trigger?}"
    step_3: "{what happens differently?}"
    result: "{the good outcome}"

  # Step 3: Verify causation
  verification:
    prevents_root_cause: true|false  # Does it fix the CAUSE or just a symptom?
    would_have_blocked: true|false   # Would this SPECIFIC scenario have been prevented?
    timing_correct: true|false       # Does prevention activate BEFORE the mistake?
```

**BLOCKING CONDITION:**

If `would_have_blocked: false` or `prevents_root_cause: false`:
- STOP - your prevention fixes a symptom, not the cause
- Return to RCA and dig deeper into WHY the mistake occurred
- Find prevention that addresses the actual failure point

**Fix Source, Not Symptoms (M355):**

When a mistake involves incorrect output from a subagent or downstream process:

| Symptom Fix (❌ WRONG) | Source Fix (✅ CORRECT) |
|------------------------|-------------------------|
| Add validation to catch bad output | Fix the prompt/input that caused bad output |
| Add check for fabricated scores | Remove priming content from delegation prompt |
| Add warning when result looks wrong | Fix the instructions that led to wrong result |
| Double-check subagent work | Fix the task description given to subagent |

**The question to ask:** "Why did the subagent produce wrong output?"
- If answer involves the PROMPT you gave it → fix the prompt
- If answer involves the DOCUMENTATION it read → fix the documentation
- Adding validation AFTER is treating the symptom, not the cause

**Example - M355 Pattern:**

```yaml
# Mistake: Subagent reported unexpected validation scores

# ❌ SYMPTOM FIX: Add validation layer to catch "wrong" results
prevention: "Run independent validation and compare scores"
prevents_root_cause: false  # Another subagent is no more independent!

# ✅ SOURCE FIX: Investigate and fix the prompt or skill
prevention: "Review delegation prompt for priming; fix skill instructions if ambiguous"
prevents_root_cause: true  # Subagent now produces correct results
```

**Note (M357):** Identical scores (e.g., all 1.0) do NOT inherently indicate fabrication. Multiple files
can legitimately achieve the same score. When results differ from expectations, investigate the prompt
or skill methodology - don't add validation layers.

**Anti-pattern:** "The subagent did X wrong, so I'll add a check for X."
**Correct approach:** "The subagent did X wrong because my prompt said Y. Fix Y."

**Example - would_have_blocked: false:**

```yaml
# Mistake: Squash captured stale file state from diverged worktree

# ❌ FAILS VERIFICATION: "Add warning when worktree diverges from base"
scenario_replay:
  what_happened:
    step_1: "Worktree created from v2.1 at commit A"
    step_2: "v2.1 advanced to commit D, worktree not updated"
    step_3: "git reset --soft v2.1 captured stale working directory"
    result: "M304 changes reverted"
  with_prevention:
    prevention_activates: "Before squash, detect and warn about divergence"
    step_3: "Warning printed, but squash proceeds anyway"
    result: "M304 changes still reverted"
  verification:
    would_have_blocked: false  # Warning doesn't prevent the failure!
    prevents_root_cause: false
    # STOP: This prevention is useless - find one that actually blocks

# ✅ PASSES VERIFICATION: "Rebase onto base before squashing"
scenario_replay:
  with_prevention:
    prevention_activates: "Before squash, rebase onto current base"
    step_3: "Working directory updated to include M304, then squash"
    result: "M304 changes preserved"
  verification:
    would_have_blocked: true   # This specific scenario prevented
    prevents_root_cause: true  # Addresses stale working directory state
```

**Why this gate exists:** M305 showed that proposed solutions may sound reasonable but fail
to actually prevent the failure. Replaying the exact scenario exposes whether prevention
changes the outcome or just adds noise (warnings, documentation).

### 8. Check If Prevention Already Exists (MANDATORY)

**CRITICAL: If prevention already exists, it FAILED and MUST be replaced with stronger prevention.**

Before implementing prevention, check if it already exists:

```yaml
existing_prevention_check:
  question: "Does documentation/process already cover this?"
  check_locations:
    - Workflow files (work.md, etc.)
    - CLAUDE.md / project instructions
    - Skill documentation
    - Existing hooks

  if_exists:
    conclusion: "Existing prevention FAILED - it was ineffective"
    action: "MUST escalate to higher prevention level"
    rationale: |
      If prevention exists and the mistake still occurred, that prevention
      is NOT WORKING. Pointing to it again changes nothing. The mistake
      WILL recur unless you implement STRONGER prevention.
```

**Key insight:** Existing prevention that didn't prevent the mistake is NOT prevention - it's
failed prevention. You must escalate to a level that will actually work.

**Escalation hierarchy (when current level failed):**

| Failed Level | Escalate To | Example |
|--------------|-------------|---------|
| Documentation | Hook/Validation | Add pre-commit hook that blocks incorrect behavior |
| Process | Code fix | Make incorrect path impossible in code |
| Threshold | Lower threshold + hook | Add monitoring that forces action |
| Validation | Code fix | Compile-time or runtime enforcement |

**Example - Documentation failed:**

```yaml
# Situation: Workflow says "MANDATORY: Execute different issue when locked"
# Agent ignored it and tried to delete the lock

# ❌ WRONG: Record prevention as "documentation" pointing to same workflow
prevention_type: documentation
prevention_path: "work.md"  # Already says MANDATORY - and it FAILED!

# ✅ CORRECT: Escalate to hook that enforces the behavior
prevention_type: hook
prevention_path: "${CLAUDE_PROJECT_DIR}/.claude/hooks/enforce-lock-protocol.sh"
action: |
  Create hook that detects lock investigation patterns and blocks them.
  Or: Modify issue-lock.sh to output ONLY "find another issue" guidance,
  removing any information that could be used to bypass the lock.
```

**The prevention step MUST take NEW action.** Recording a mistake without implementing NEW prevention
(beyond what already existed) is not learning - it's just logging. The same mistake WILL recur.

**BLOCKING CRITERIA (A002) - Documentation-level prevention NOT ALLOWED when:**

| Condition | Why Blocked | Required Action |
|-----------|-------------|-----------------|
| Similar documentation already exists | Documentation already failed | Escalate to hook or code_fix |
| Mistake category is `protocol_violation` | Protocol was documented but violated | Escalate to hook enforcement |
| This is a recurrence (`recurrence_of` is set) | Previous prevention failed | Escalate to stronger level |
| prevention_type would be `documentation` (level 7) | Weakest level, often ineffective | Consider hook (level 2) or validation (level 3) |

**Self-check before recording prevention_type: documentation:**

```yaml
documentation_prevention_blocked_if:
  - Similar instruction already exists in workflow/skill docs
  - The mistake was ignoring existing documentation
  - Category is protocol_violation (protocols ARE documentation)
  - This is a recurrence of a previous mistake

# If ANY of the above is true:
action: "STOP. Escalate to hook, validation, or code_fix instead."
```

**Verification questions:**
1. "Did prevention for this already exist?" → If YES, it failed and must be escalated
2. "What NEW mechanism will prevent this tomorrow?" → Must be different from what failed today
3. "Is this prevention stronger than what failed?" → Must be higher in the hierarchy
4. "Am I choosing documentation because it's easy?" → If YES, find a stronger approach (A002)

**If you cannot identify NEW prevention stronger than what already exists, you have NOT learned.**

### 8b. Check for Misleading Documentation (M256)

**CRITICAL: Documentation may have ACTIVELY MISLED the agent toward the wrong approach.**

Beyond checking if prevention exists, check if documentation contains content that:
- Teaches HOW to do something manually before saying "don't do it manually"
- Provides implementation details the agent shouldn't use directly
- Has reference information that only applies when pre-rendered content exists
- Contains examples or functions that prime the agent for incorrect behavior

```yaml
misleading_documentation_check:
  questions:
    - "Does the doc teach a skill/approach BEFORE saying not to use it?"
    - "Are there 'reference' sections with info the agent might try to use?"
    - "Does section ordering prime the agent for wrong approach?"
    - "Is there info that should ONLY appear with pre-rendered content?"

  patterns_to_find:
    - Functions/Prerequisites sections before Procedure
    - "Reference" sections with usable implementation details
    - Examples of manual construction in skills that use pre-computation
    - Emoji/formatting references outside pre-rendered content

  if_misleading_content_found:
    action: "Remove or relocate misleading content as part of prevention"
    principle: |
      If information is only needed when pre-rendered content exists,
      it should BE IN the pre-rendered content, not the skill doc.
      This prevents agents from attempting manual construction when
      pre-rendered content is missing.
```

**The "Conditional Information" Principle:**

| Information Type | Where It Belongs |
|------------------|------------------|
| How to copy-paste pre-rendered content | Skill doc (always needed) |
| Emoji meanings, formatting rules | Output template output (only needed when it exists) |
| Implementation functions | Handler code only (never in skill doc) |
| What to do if pre-rendered content missing | Skill doc (FAIL instruction) |

**Example - M256 Pattern:**

```yaml
# WRONG: Skill doc teaches emoji selection
## Functions
### select_emoji(status) -> emoji
if status == "completed": return "☑️"
...
## Procedure
Step 1: Use pre-rendered content...

# RIGHT: Move emoji info to pre-rendered content
## Procedure
Step 1: Use pre-rendered content...
# (Emoji reference appears IN the template content, not skill doc)
```

### 9. Implement Prevention

**MANDATORY: Take concrete action. Prevention without action changes nothing.**

The prevention step must result in a modified file - code, hook, configuration, or documentation.
If you finish this step without editing a file, you have not implemented prevention.

**Escalation and Layered Prevention (M342):**

When escalating from documentation to hook/validation, **keep both layers** but align them:

| Layer | Purpose | Keep? |
|-------|---------|-------|
| **Skill/Doc** | Proactive guidance - teaches WHY, guides before action | YES |
| **Hook** | Reactive enforcement - blocks after attempt, provides fix | YES (new) |

**Why keep both:**
- Skills guide agents BEFORE they act (proactive)
- Hooks catch mistakes AFTER the attempt (reactive)
- Skills explain context and cover related rules
- Hooks can fail (bugs, edge cases, not loaded)

**When escalating, update the skill to reference the hook:**
```markdown
- NEVER do X - use Y instead
  *(Enforced by hook MXXX - blocked if condition)*
```

This creates defense-in-depth: guidance prevents most mistakes, enforcement catches the rest.

**Script vs Skill Instructions (M363):**

Before adding prevention to a skill, ask: **Does this require LLM decision-making?**

| If the check is... | Implement as... | Why |
|--------------------|-----------------|-----|
| Deterministic (fixed inputs → fixed outputs) | Script with tests | Testable, consistent, no LLM variance |
| Requires context/judgment | Skill instructions | LLM needed for interpretation |

**Examples:**

| Check | Deterministic? | Implementation |
|-------|----------------|----------------|
| "Does branch have commits ahead of base?" | Yes - `git log` | Script |
| "Is file path inside worktree?" | Yes - path comparison | Script |
| "Does commit message follow convention?" | Yes - regex match | Hook |
| "Is this change architecturally sound?" | No - requires judgment | Skill instructions |
| "Should we decompose this task?" | No - context-dependent | Skill instructions |

**When in doubt, ask:** "Could a bash script do this with no LLM?" If yes → script with tests.

**Fix Location Principle: Apply to deepest document possible.**

When choosing WHERE to implement a fix, prefer the lowest-level document that addresses the issue:

| Level | Example | Benefit |
|-------|---------|---------|
| Concept doc | `concepts/subagent-delegation.md` | All skills/workflows referencing it inherit the fix |
| Skill doc | `skills/shrink-doc/SKILL.md` | All invocations of that skill get the fix |
| Workflow doc | `concepts/work.md` | Specific workflow improved |
| Command doc | `commands/work.md` | Single entry point fixed |

**Why depth matters:** A fix in a concept document (e.g., subagent-delegation.md) benefits every skill
and workflow that references it. A fix in a command document benefits only that command. Apply fixes
at the deepest level where they're relevant to maximize fix propagation.

**Example:** M277 (validation separation) belongs in `shrink-doc/SKILL.md` (skill-specific validation)
not `work.md` (generic workflow) because the per-file subagent pattern is shrink-doc-specific.

**Verification question (M297):** Before committing a fix, ask: "Is this rule specific to one skill/context,
or genuinely applies to all issues?" If specific → find the skill doc. If generic → workflow doc is correct.

**Language requirements for documentation/prompt changes (M177):**

When prevention involves updating documentation, prompts, or instructions, use **positive actionable
language** that guides toward correct behavior rather than warning against mistakes.

| Instead of (negative) | Use (positive) |
|----------------------|----------------|
| "Do NOT use approximate content" | "Copy-paste exact content from final output" |
| "Never skip the verification step" | "Complete verification before proceeding" |
| "Don't forget to commit" | "Commit changes before requesting review" |
| "Avoid using placeholder text" | "Write final content first, then calculate" |

**Why positive framing works better:**
- Tells the agent what TO do (actionable) vs what to avoid (requires inference)
- Creates clear mental model of correct behavior
- Reduces cognitive load - no need to invert the instruction
- Section titles should name the solution, not the problem (e.g., "Copy-Paste Workflow" not "Avoiding Content Mismatch")

**Self-check before finalizing prevention text:**
1. Does each instruction describe an action to take?
2. Are section titles named after solutions, not problems?
3. Would someone know exactly what to do after reading this?

Keep negative language only when no actionable positive alternative exists (e.g., security warnings
where the "don't" is the entire point).

**Fail-Fast Error Handling (M361):**

When implementing prevention that modifies error handling, apply the fail-fast principle:

| Situation | Wrong Approach | Correct Approach |
|-----------|----------------|------------------|
| Required parameter missing | Return None (allow) | Block with error |
| Can't verify safety | Assume safe | Assume unsafe, block |
| Validation impossible | Skip validation | Fail the operation |

**The mental model:**
- "Unknown safety" = "unsafe"
- If you can't verify an operation is safe, block it
- Never allow potentially dangerous operations to proceed when validation fails

**Example (M361):**
```python
# ❌ WRONG: Allow when can't validate
cwd = context.get("cwd")
if not cwd:
    return None  # Allows dangerous command!

# ✅ CORRECT: Block when can't validate
cwd = context.get("cwd")
if not cwd:
    return {"decision": "block", "reason": "Cannot verify safety - cwd missing"}
```

**Complete Fix Requirement for Documentation Priming (M345):**

When documentation primed the agent for wrong behavior, the fix must be **complete**:

| Scenario | Incomplete Fix | Complete Fix |
|----------|----------------|--------------|
| Automation exists but broken | "NEVER manually construct" | Fix the preprocessing/handler |
| Automation doesn't exist yet | "NEVER manually construct" | ASK USER: build it or simplify? |
| Skill references non-existent feature | "Don't use feature X" | ASK USER: build it or simplify? |

**The fix must make correct behavior possible, not just prohibit wrong behavior.**

**MANDATORY: Preserve Output Format When Possible (M345):**

When a skill cannot produce its intended output due to missing automation:

1. **Do NOT unilaterally change the output format**
2. **Use AskUserQuestion to offer the choice:**

```yaml
question: "Skill '{skill}' references output that cannot be generated. How should I proceed?"
options:
  - label: "Build the missing automation"
    description: "Create the preprocessing script/handler to generate the intended output"
  - label: "Simplify the output format"
    description: "Change skill to use simpler format (e.g., markdown instead of boxes)"
```

3. Implement whichever option the user selects

**Checklist before finalizing documentation priming fix:**

```yaml
complete_fix_checklist:
  negative_guidance: "Does fix say what NOT to do?"  # Necessary but insufficient
  positive_guidance: "Does fix say what TO do?"      # Required
  ability_to_act: "Can agent actually do it?"        # Required
  format_preserved: "Is original output format retained?"  # Preferred

  # If ability_to_act is NO:
  if_automation_broken:
    action: "Fix the automation"
  if_automation_missing:
    action: "ASK USER: build automation or simplify output?"
    do_not: "Unilaterally change output format"
```

**Anti-pattern (M345):** Adding "NEVER do X" without ensuring the agent CAN do Y.
**Anti-pattern (M345):** Changing output format without user consent.

**For context-related mistakes:**

```yaml
prevention_action:
  if_context_related:
    # Context limits are fixed - see agent-architecture.md § Context Limit Constants
    primary:
      action: "Improve issue size estimation"
      rationale: "Better estimates prevent exceeding limits"

    secondary:
      action: "Add quality checkpoint at 50% context"
      implementation: |
        At 50% context, pause and verify:
        - Is work quality consistent with early session?
        - Are earlier decisions still being referenced?
        - Should issue be decomposed now?

    tertiary:
      action: "Enhance PLAN.md with explicit checkpoints"
      implementation: |
        Add context-aware milestones to issue plans.
        Each milestone = potential decomposition point.
```

**BLOCKING GATE (M134/A022) - Prevention File Edit Verification:**

BEFORE proceeding to "Record Learning", you MUST complete this gate:

1. **List EVERY file you edited in Step 9:**
   - File 1: _______________
   - File 2: _______________
   (Add more lines as needed)

2. **Verification check:**
   - [ ] At least ONE file path is listed above
   - [ ] Each listed file was ACTUALLY edited (not just read)
   - [ ] The edit tool was used, not just planned

3. **BLOCKING CONDITION:**
   If the file list above is BLANK or contains only placeholders:
   - **STOP IMMEDIATELY**
   - Go back to Step 9
   - Make an ACTUAL edit to implement prevention
   - Return here and fill in the file path(s)
   - Only then proceed to Record Learning

4. **Why this gate exists (M134/M135):**
   Recording `prevention_implemented: true` without editing a file is FALSE.
   The prevention_path in the JSON entry MUST match a file listed above.
   If they don't match, the learning system is corrupted.

### 9b. Verify Fix Doesn't Introduce Priming (M370)

**MANDATORY: Check if your fix introduces new priming patterns.**

When implementing documentation fixes, you may inadvertently introduce the same priming
patterns that cause fabrication (M346). After editing files, verify:

```yaml
priming_check:
  # Check each file you edited in Step 9
  for_each_edited_file:
    contains_output_format_example: true|false
    if_true:
      has_concrete_values: true|false  # e.g., "1.0", "0.87", "SUCCESS"
      has_placeholders: true|false     # e.g., "{actual score}", "{status}"

  # BLOCKING: If concrete values found in output format
  if_concrete_values:
    action: "Replace with descriptive placeholders"
    examples:
      wrong: "| file1.md | 1.0 | PASS |"
      right: "| {filename} | {actual score from /compare-docs} | {PASS|FAIL} |"
```

**Common priming patterns to avoid in fixes:**

| Pattern | Risk | Fix |
|---------|------|-----|
| Result table with scores | Agent produces those exact scores | Use `{actual score}` placeholder |
| Status examples like "SUCCESS" | Agent reports success without verification | Use `{status}` |
| Concrete token counts | Agent fabricates similar counts | Use `{count}` |

**Why this gate exists (M370):** When fixing M369, example result tables were added with
concrete values (1.0, 0.87), which would prime agents to produce those values instead
of running actual validation.

### 9c. Check Related Files for Similar Mistakes (M341)

**MANDATORY: After fixing a file, check if similar files have the same vulnerability.**

Many mistakes reflect patterns that exist across multiple files. After implementing the fix:

1. **Identify the pattern fixed:**
   ```yaml
   pattern_fixed:
     file_type: "skill"  # skill, hook, handler, config, etc.
     vulnerability: "weak copy-paste instruction for pre-rendered content"
     fix_applied: "added prominent MANDATORY OUTPUT REQUIREMENT header"
   ```

2. **Find related files:**
   ```bash
   # Examples by file type:

   # Skills with pre-rendered content
   grep -l '!\`' plugin/skills/*/SKILL.md

   # Handlers with similar validation
   find plugin/hooks -name "*.py" -exec grep -l "similar_pattern" {} \;

   # Config files with same structure
   find . -name "cat-config.json"
   ```

3. **Check each related file for the same vulnerability:**
   - Read the file
   - Determine if it has the same weakness
   - If yes: apply the same fix pattern

4. **Record related fixes:**
   ```yaml
   related_files_checked:
     - path: "plugin/skills/help/SKILL.md"
       had_vulnerability: true
       fixed: true
     - path: "plugin/skills/work/SKILL.md"
       had_vulnerability: true
       fixed: true
     - path: "plugin/skills/init/SKILL.md"
       had_vulnerability: false
       reason: "uses named box references instead of direct copy-paste"
   ```

**Why this matters:** Fixing one file while leaving identical vulnerabilities in similar files
means the same mistake WILL recur. Proactive checking prevents future M-numbers for the same root cause.

**Skip this step only when:**
- The fix is truly unique to one file (e.g., typo fix)
- No similar files exist (verified, not assumed)

### 10. Verify Prevention Works

```yaml
verification:
  action: "Rerun similar issue with new threshold"
  success_criteria:
    - Decomposition triggered before 60K tokens
    - No quality degradation observed
    - Original mistake type does not recur
```

### 11. Record Learning

**MANDATORY: Persist learning to file, not just context.**

**CRITICAL VALIDATION - Before recording, verify prevention is REAL:**

```yaml
prevention_path_validation:
  invalid_examples:
    - "N/A"
    - "N/A - behavioral change"
    - "behavioral"
    - "process change"
    - ""  # empty
    - "TBD"

  valid_examples:
    - "/workspace/cat/commands/work.md"
    - ".claude/hooks/validate-commit.sh"
    - "src/main/java/Parser.java"

  # Rule: prevention_path MUST be a real file path that was actually modified
  # "Behavioral change" without enforcement is NOT prevention - it WILL recur

  # (A022) BLOCKING: prevention_path must match a file listed in the BLOCKING GATE above
  # If prevention_path doesn't match what you edited, STOP and fix before recording
  # Recording with mismatched path corrupts the learning system
```

**If you cannot identify a real file to change, you have NOT implemented prevention.**
Go back to step 9 and find a code/config/documentation fix.

**Directory:** `.claude/cat/retrospectives/`

**File Structure (v2.0 - time-based splits):**
- `index.json` - Centralized config and file tracking
- `mistakes-YYYY-MM.json` - Mistakes for each month
- `retrospectives-YYYY-MM.json` - Retrospectives for each month

**CRITICAL PATH CHECKS**:

1. **Directory path**: Files MUST be in `.claude/cat/retrospectives/`, NOT `.claude/retrospectives/`.

2. **prevention_path format (M040)**: MUST use `${CLAUDE_PROJECT_DIR}` prefix for project-relative
paths:
   ```yaml
   # INVALID - relative paths break when cwd changes
   prevention_path: ".claude/hooks/my-hook.sh"
   prevention_path: "hooks/my-hook.sh"

   # VALID - absolute paths work from any directory
   prevention_path: "${CLAUDE_PROJECT_DIR}/.claude/hooks/my-hook.sh"
   prevention_path: "/workspace/cat/skills/my-skill/SKILL.md"
   ```

3. **Plugin source vs cache (M041)**: When fixing CAT plugin files, edit SOURCE not CACHE:
   ```yaml
   # WRONG - edits lost on plugin update
   ~/.config/claude/plugins/cache/claude-code-cat/cat/1.1/skills/...

   # CORRECT - edit the source repository
   /workspace/cat/skills/...  # or wherever CAT source is cloned
   ```

If files exist at wrong location, migrate:

```bash
if [ -d .claude/retrospectives ] && [ ! -d .claude/cat/retrospectives ]; then
  mkdir -p .claude/cat/retrospectives
  mv .claude/retrospectives/*.json .claude/cat/retrospectives/ 2>/dev/null || true
  rmdir .claude/retrospectives 2>/dev/null || true
fi
```

```bash
RETRO_DIR=".claude/cat/retrospectives"
INDEX_FILE="$RETRO_DIR/index.json"

# Get current year-month for file naming
YEAR_MONTH=$(date +%Y-%m)
MISTAKES_FILE="$RETRO_DIR/mistakes-${YEAR_MONTH}.json"

mkdir -p "$RETRO_DIR"

# Initialize index.json if needed
if [ ! -f "$INDEX_FILE" ]; then
  cat > "$INDEX_FILE" << 'EOF'
{
  "version": "2.0",
  "config": {
    "mistake_count_threshold": 5,
    "trigger_interval_days": 7
  },
  "last_retrospective": null,
  "mistake_count_since_last": 0,
  "files": {
    "mistakes": [],
    "retrospectives": []
  }
}
EOF
fi

# Initialize split file for current month if needed
if [ ! -f "$MISTAKES_FILE" ]; then
  echo "{\"period\":\"$YEAR_MONTH\",\"mistakes\":[]}" > "$MISTAKES_FILE"
  # Add to index
  jq --arg f "mistakes-${YEAR_MONTH}.json" \
    'if (.files.mistakes | index($f)) then . else .files.mistakes += [$f] | .files.mistakes |= sort end' \
    "$INDEX_FILE" > "$INDEX_FILE.tmp" && mv "$INDEX_FILE.tmp" "$INDEX_FILE"
fi

# Get max ID across ALL split files (handles gaps correctly)
MAX_NUM=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
  jq -s '[.[].mistakes[].id] | map(select(startswith("M")) | ltrimstr("M") | tonumber) | max // 0')
NEXT_NUM=$((MAX_NUM + 1))
NEXT_ID=$(printf "M%03d" $NEXT_NUM)

# Verify ID doesn't already exist across all files (safety check)
if cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | jq -s -e --arg id "$NEXT_ID" \
    '[.[].mistakes[] | select(.id == $id)] | length > 0' >/dev/null 2>&1; then
  echo "ERROR: ID $NEXT_ID already exists! Finding next available..."
  MAX_NUM=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
    jq -s '[.[].mistakes[].id] | map(ltrimstr("M") | tonumber) | max')
  NEXT_NUM=$((MAX_NUM + 1))
  NEXT_ID=$(printf "M%03d" $NEXT_NUM)
fi
```

**Append entry to mistakes.json:**

```json
{
  "id": "{NEXT_ID}",
  "timestamp": "{ISO-8601 timestamp}",
  "category": "{see category reference below}",
  "description": "{One-line description of the mistake}",
  "root_cause": "{Root cause from analysis}",
  "rca_method": "{A|B|C}",
  "rca_method_name": "{5-whys|taxonomy|causal-barrier}",
  "prevention_type": "{code_fix|hook|validation|config|skill|threshold|process|documentation}",
  "prevention_path": "{path/to/file/changed}",
  "pattern_keywords": ["{keyword1}", "{keyword2}"],
  "prevention_implemented": true,
  "prevention_verified": true,
  "recurrence_of": "{null or ID of original mistake if this is a recurrence}",
  "prevention_quality": {
    "verification_type": "{positive|negative}",
    "fragility": "{low|medium|high}",
    "catches_variations": true
  },
  "correct_behavior": "{What should be done instead}"
}
```

**Category and Prevention Type Reference:**

See [mistake-categories.md](mistake-categories.md) for full category list, prevention types, and common root cause patterns.

**Common categories:** protocol_violation, prompt_engineering, context_degradation, tool_misuse, assumption_without_verification, misleading_documentation (M269)

**Use jq to append to current month's split file:**

```bash
# Append to current month's split file (mistakes-YYYY-MM.json)
jq --argjson new '{...new entry...}' '.mistakes += [$new]' \
  "$MISTAKES_FILE" > "$MISTAKES_FILE.tmp" \
  && mv "$MISTAKES_FILE.tmp" "$MISTAKES_FILE"
```

### 12. Update Retrospective Counter and Commit

**MANDATORY: Update counter and commit files together.**

**VALIDATION CHECK**: Before incrementing, verify counter matches actual mistake count:

```bash
RETRO_DIR=".claude/cat/retrospectives"
INDEX_FILE="$RETRO_DIR/index.json"

LAST_RETRO=$(jq -r '.last_retrospective // empty' "$INDEX_FILE")

# Count actual mistakes since last retrospective across ALL split files
if [[ -n "$LAST_RETRO" && "$LAST_RETRO" != "null" ]]; then
  ACTUAL_COUNT=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
    jq -s --arg date "$LAST_RETRO" \
    '[.[].mistakes[] | select(.timestamp > $date)] | length')
else
  ACTUAL_COUNT=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
    jq -s '[.[].mistakes[]] | length')
fi

COUNTER=$(jq '.mistake_count_since_last' "$INDEX_FILE")

# Warn if mismatch (counter should be ACTUAL_COUNT - 1 before we increment)
if [[ $COUNTER -ne $((ACTUAL_COUNT - 1)) ]] && [[ $COUNTER -ne $ACTUAL_COUNT ]]; then
  echo "WARNING: Counter mismatch! Counter=$COUNTER, Actual mistakes since $LAST_RETRO=$ACTUAL_COUNT"
  echo "Fixing counter to match actual count..."
  jq --argjson count "$ACTUAL_COUNT" '.mistake_count_since_last = $count' "$INDEX_FILE" > "$INDEX_FILE.tmp" \
    && mv "$INDEX_FILE.tmp" "$INDEX_FILE"
else
  jq '.mistake_count_since_last += 1' "$INDEX_FILE" > "$INDEX_FILE.tmp" \
    && mv "$INDEX_FILE.tmp" "$INDEX_FILE"
fi

# Commit split file and index together
git add "$MISTAKES_FILE" "$INDEX_FILE"
git commit -m "config: record learning ${NEXT_ID} - {short description}"

# Get current values to check trigger
MISTAKES=$(jq '.mistake_count_since_last' "$INDEX_FILE")
THRESHOLD=$(jq '.config.mistake_count_threshold' "$INDEX_FILE")
LAST_RETRO=$(jq -r '.last_retrospective // empty' "$INDEX_FILE")
INTERVAL=$(jq '.config.trigger_interval_days' "$INDEX_FILE")

# Calculate days since last retrospective
if [[ -n "$LAST_RETRO" && "$LAST_RETRO" != "null" ]]; then
  LAST_EPOCH=$(date -d "$LAST_RETRO" +%s 2>/dev/null || echo 0)
else
  LAST_EPOCH=0
fi
NOW_EPOCH=$(date +%s)
DAYS_SINCE=$(( (NOW_EPOCH - LAST_EPOCH) / 86400 ))

# Check thresholds
if [[ $MISTAKES -ge $THRESHOLD ]]; then
  echo "RETROSPECTIVE TRIGGERED: Mistake threshold reached ($MISTAKES >= $THRESHOLD)"
  echo "Run: /cat:run-retrospective"
elif [[ $DAYS_SINCE -ge $INTERVAL ]]; then
  echo "RETROSPECTIVE TRIGGERED: Time threshold reached ($DAYS_SINCE days >= $INTERVAL)"
  echo "Run: /cat:run-retrospective"
else
  echo "Retrospective status: $MISTAKES/$THRESHOLD mistakes, $DAYS_SINCE/$INTERVAL days"
fi
```

**If triggered, MUST use AskUserQuestion (M071):**

```yaml
retrospective_trigger:
  condition: mistakes >= threshold OR days >= interval
  action: "Use AskUserQuestion to offer user choice"
  mandatory_prompt:
    question: "Retrospective threshold exceeded ({count}/{threshold}). Run retrospective now?"
    options:
      - "Run now" - Invoke /cat:run-retrospective immediately
      - "Later" - Inform user to run /cat:run-retrospective when ready
      - "Skip this cycle" - Reset counter without running
```

**Anti-pattern (M071):** Printing "retrospective should be triggered" without using AskUserQuestion
to give user explicit choice.

## Examples

### Context-Related Mistake

```yaml
mistake:
  type: "Forgot earlier requirement"
  tokens_at_error: 110000
  compactions: 3

analysis:
  context_related: YES
  pattern: "Requirement stated at 15K tokens, forgotten by 110K"

prevention:
  type: earlier_decomposition
  action: "Split issue at 40K tokens, before degradation"
```

### Non-Context-Related Mistake

```yaml
mistake:
  type: "Used wrong API method"
  tokens_at_error: 25000
  compactions: 0

analysis:
  context_related: NO
  pattern: "Simple misunderstanding of API, not context issue"

prevention:
  type: validation
  action: "Add API usage verification in code review checklist"
```

### Ambiguous Case

```yaml
mistake:
  type: "Inconsistent code style"
  tokens_at_error: 75000
  compactions: 1

analysis:
  context_related: POSSIBLY
  pattern: "Style was consistent until compaction, then diverged"
  contributing_factors:
    - Compaction lost style context
    - No automated style check

prevention:
  type: hybrid
  actions:
    - "Add automated style linting (code fix)"
    - "Lower threshold to avoid compaction (CAT-specific)"
```

## Anti-Patterns

### Always analyze token metrics in 5-whys

```yaml
# ❌ Standard analysis only
five_whys:
  - "Why error?" -> "Bad implementation"
  - "Why bad?" -> "Misunderstood requirements"
  # Stops here, misses context cause

# ✅ CAT-specific analysis
five_whys:
  - "Why error?" -> "Bad implementation"
  - "Why bad?" -> "Misunderstood requirements"
  - "Why misunderstood?" -> "Earlier context not referenced"
  - "Why not referenced?" -> "95K tokens, context pressure"
  - "Why 95K tokens?" -> "Issue not decomposed"
```

### Distinguish context-related from non-context mistakes

```yaml
# ❌ Blaming context for everything
mistake: "Typo in variable name"
analysis: "Must be context degradation"

# ✅ Honest analysis
mistake: "Typo in variable name"
analysis: |
  Tokens at error: 15000 (15% of context)
  Compactions: 0
  Context-related: NO
  Actual cause: Simple typo, needs spellcheck
```

### Base threshold adjustments on data

```yaml
# ❌ Arbitrary threshold change
new_threshold: 20000  # "Let's be extra safe"

# ✅ Data-driven adjustment
analysis: |
  Errors consistently occur after 70K tokens.
  Quality degradation measurable at 60K.
  Setting threshold at 50K provides safety margin.
new_threshold: 50000
```

### Always verify prevention works

```yaml
# ❌ Implement and forget
prevention: "Lower threshold to 30%"
# Never verified!

# ✅ Verify prevention works
prevention: "Lower threshold to 30%"
verification:
  - Run similar issue
  - Confirm decomposition triggers at 30%
  - Confirm mistake type doesn't recur
```

### Use robust positive verification (check for correct format)

```yaml
# ❌ Check for specific failure pattern (fragile)
prevention: |
  grep "TODO" file.java  # Only catches THIS exact text
  # What if next failure uses "FIXME", "XXX", "HACK", etc.?

# ✅ Check for correct format (robust)
prevention: |
  ./mvnw checkstyle:check  # Verifies code meets all style requirements
  # Catches ANY code quality failure, not just anticipated ones

# Key insight: Verify what you WANT, not what you DON'T want
```

### Invoke the skill first when user says "Learn from mistakes" (M072)

When user explicitly requests mistake analysis:

```yaml
# ❌ WRONG: Fix immediate problem, skip skill invocation
user: "Learn from mistakes: you didn't commit before approval"
agent: [makes the commit]
agent: "Done, here's the approval gate again"
# Mistake not recorded, will recur!

# ✅ CORRECT: Invoke skill, analyze, record, THEN fix
user: "Learn from mistakes: you didn't commit before approval"
agent: [invokes /cat:learn skill]
agent: [performs 5-whys analysis]
agent: [records in mistakes.json]
agent: [implements prevention]
agent: [then fixes immediate problem]
```

**Key principle:** "Learn from mistakes" is a trigger to invoke this skill, not a description of what
to conceptually do. Always invoke the actual skill.

### Escalate to enforcement when documentation failed (M084)

```yaml
# ❌ WRONG: Documentation already existed and was ignored
situation: "Workflow said MANDATORY but agent ignored it"
recorded_prevention:
  type: documentation
  path: "work.md"  # Same file that was already ignored!
# This is NOT prevention - the documentation already failed!

# ✅ CORRECT: Escalate to enforcement
situation: "Workflow said MANDATORY but agent ignored it"
analysis: "Documentation alone is insufficient - must automate"
recorded_prevention:
  type: hook
  path: ".claude/hooks/enforce-lock-protocol.sh"
  action: "Created hook that blocks lock bypass attempts"
# NEW mechanism that didn't exist before
```

**Key insight:** If you're pointing to a file that already contained the instruction you violated,
you have NOT implemented prevention. You've just documented your failure to read. Escalate to
automation that makes the incorrect behavior impossible or blocked.

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
