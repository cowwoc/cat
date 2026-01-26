---
name: cat:learn-from-mistakes
description: Analyze mistakes with conversation length as potential cause (CAT-specific)
---

# Learn From Mistakes (CAT-Specific)

## Purpose

Analyze mistakes using 5-whys with CAT-specific consideration of conversation length and context
degradation. Integrates token tracking to identify context-related failures and recommend preventive
measures including earlier decomposition.

## When to Use

- Any mistake during CAT orchestration
- Subagent produces incorrect/incomplete results
- Task requires rework or correction
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
SESSION_ID="${SUBAGENT_SESSION}"
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"

TOKENS_AT_ERROR=$(jq -s 'map(select(.type == "assistant")) |
  map(.message.usage | .input_tokens + .output_tokens) | add' "${SESSION_FILE}")
COMPACTIONS=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")
MESSAGE_COUNT=$(jq -s '[.[] | select(.type == "assistant")] | length' "${SESSION_FILE}")
SESSION_DURATION=$(calculate_duration "${SESSION_FILE}")
```

### 4. Perform Root Cause Analysis

**A/B TEST IN PROGRESS** - See [RCA-AB-TEST.md](RCA-AB-TEST.md) for full specification.

**Method Assignment Rule:** Use mistake ID modulo 3:
- IDs ending in 6,9,2,5,8 (mod 3 = 0) → Method A (5-Whys)
- IDs ending in 7,0,3 (mod 3 = 1) → Method B (Taxonomy)
- IDs ending in 8,1,4 (mod 3 = 2) → Method C (Causal Barrier)

---

#### Method A: 5-Whys (Control)

Ask "why" iteratively until reaching fundamental cause (typically 5 levels):

```yaml
five_whys:
  - why: "Why did this happen?"
    answer: "Immediate cause of the mistake"
  - why: "Why [previous answer]?"
    answer: "Deeper contributing factor"
  - why: "Why [previous answer]?"
    answer: "Organizational or process factor"
  - why: "Why [previous answer]?"
    answer: "Systemic or environmental factor"
  - why: "Why [previous answer]?"
    answer: "Root cause - fundamental issue"

root_cause: "The fundamental issue identified at deepest 'why'"
category: "Select from category reference"
rca_method: "A"
```

**Example:**

```yaml
five_whys:
  - why: "Why was precedence implemented incorrectly?"
    answer: "Subagent confused multiplication and addition handling"
  - why: "Why was the subagent confused?"
    answer: "Earlier context about precedence rules was not referenced"
  - why: "Why wasn't earlier context referenced?"
    answer: "Session had 95K tokens, approaching context limit"
  - why: "Why were there 95K tokens in the session?"
    answer: "Task scope was too large for single context window"
  - why: "Why wasn't the task decomposed earlier?"
    answer: "Token monitoring wasn't triggering at 40% threshold"

root_cause: "Task exceeded safe context bounds without decomposition"
category: "context_degradation"
rca_method: "A"
```

**Check against common root cause patterns:**
- Assumption without verification?
- Completion bias (rationalized ignoring rules)?
- Memory reliance (didn't re-verify)?
- Environment state mismatch?
- Documentation ignored (rule existed)?

---

#### Method B: Modular Error Taxonomy

Based on [AgentErrorTaxonomy](https://arxiv.org/abs/2509.25370) (24% accuracy improvement).

```yaml
taxonomy_analysis:
  # Step 1: Classify into module
  module: MEMORY | PLANNING | ACTION | REFLECTION | SYSTEM
  module_definitions:
    MEMORY: "Failed to retain/recall earlier context"
    PLANNING: "Poor task decomposition or sequencing"
    ACTION: "Incorrect tool use or execution"
    REFLECTION: "Failed to detect/correct own error"
    SYSTEM: "Environment, tooling, or integration failure"

  # Step 2: Identify failure mode within module
  failure_mode: "What specific capability failed?"
  failure_type: FALSE_POSITIVE | FALSE_NEGATIVE
    # FALSE_POSITIVE = did something wrong
    # FALSE_NEGATIVE = missed something

  # Step 3: Check for cascading
  cascading:
    caused_downstream: true | false
    is_symptom_of: null | "earlier failure description"

  # Step 4: Corrective feedback
  corrective_feedback: "What specific guidance would have prevented this?"
  intervention_point: "At what step should intervention have occurred?"

root_cause: "..."
category: "..."
rca_method: "B"
```

---

#### Method C: Causal Barrier Analysis

Based on [causal reasoning research](https://www.infoq.com/articles/causal-reasoning-observability/).

```yaml
causal_barrier_analysis:
  # Step 1: List ALL candidate causes
  candidates:
    - cause: "Knowledge gap - didn't know correct approach"
      expected_symptoms: ["asked questions", "explored alternatives"]
      observed: false
      likelihood: LOW

    - cause: "Compliance failure - knew rule, didn't follow"
      expected_symptoms: ["rule exists in docs", "no confusion expressed"]
      observed: true
      likelihood: HIGH

    - cause: "Tool limitation - tool couldn't do what was needed"
      expected_symptoms: ["error messages", "tried alternatives"]
      observed: false
      likelihood: LOW

  # Step 2: Select most likely cause
  selected_cause: "Compliance failure"
  confidence: HIGH | MEDIUM | LOW
  evidence: "Rule documented in X, no exploration attempts observed"

  # Step 3: Verify cause vs symptom
  verification:
    question: "If we fixed this, would the problem definitely not recur?"
    answer: "Yes, if enforcement hook blocks the incorrect behavior"
    is_root_cause: true  # If uncertain, this may be a symptom

  # Step 4: Barrier analysis
  barriers:
    - barrier: "Documentation in CLAUDE.md"
      existed: true
      why_failed: "Agent did not read/follow it"

    - barrier: "PreToolUse hook"
      existed: false
      should_exist: true
      strength_if_added: "Would block incorrect behavior"

  minimum_effective_barrier: "hook (level 2)"

root_cause: "..."
category: "..."
rca_method: "C"
```

---

**Record the method used** in the final JSON entry:

```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier"
}
```

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

**Choose the strongest prevention level that addresses the root cause:**

```yaml
prevention_hierarchy:
  - level: 1
    type: code_fix
    description: "Make incorrect behavior impossible in code"
    examples: ["compile-time check", "type system enforcement", "API design"]
  - level: 2
    type: hook
    description: "Automated enforcement via PreToolUse/PostToolUse hooks"
    examples: ["block dangerous commands", "require confirmation", "validate state"]
  - level: 3
    type: validation
    description: "Automated checks that catch mistakes early"
    examples: ["build verification", "lint rules", "test assertions"]
  - level: 4
    type: config
    description: "Configuration or threshold changes"
    examples: ["lower context threshold", "adjust timeouts", "change defaults"]
    cat_specific: true
  - level: 5
    type: skill
    description: "Update skill documentation with explicit guidance"
    examples: ["add anti-pattern section", "add checklist item", "clarify steps"]
  - level: 6
    type: process
    description: "Change workflow steps or ordering"
    examples: ["add mandatory checkpoint", "reorder operations", "add verification"]
  - level: 7
    type: documentation
    description: "Document to prevent future occurrence"
    examples: ["add to CLAUDE.md", "update style guide", "add comments"]
    note: "Weakest prevention - escalate if documentation already exists"
```

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
# Situation: Workflow says "MANDATORY: Execute different task when locked"
# Agent ignored it and tried to delete the lock

# ❌ WRONG: Record prevention as "documentation" pointing to same workflow
prevention_type: documentation
prevention_path: "work.md"  # Already says MANDATORY - and it FAILED!

# ✅ CORRECT: Escalate to hook that enforces the behavior
prevention_type: hook
prevention_path: "${CLAUDE_PROJECT_DIR}/.claude/hooks/enforce-lock-protocol.sh"
action: |
  Create hook that detects lock investigation patterns and blocks them.
  Or: Modify task-lock.sh to output ONLY "find another task" guidance,
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
- Has reference information that only applies when output template exists
- Contains examples or functions that prime the agent for incorrect behavior

```yaml
misleading_documentation_check:
  questions:
    - "Does the doc teach a skill/approach BEFORE saying not to use it?"
    - "Are there 'reference' sections with info the agent might try to use?"
    - "Does section ordering prime the agent for wrong approach?"
    - "Is there info that should ONLY appear with output template?"

  patterns_to_find:
    - Functions/Prerequisites sections before Procedure
    - "Reference" sections with usable implementation details
    - Examples of manual construction in skills that use pre-computation
    - Emoji/formatting references outside output template

  if_misleading_content_found:
    action: "Remove or relocate misleading content as part of prevention"
    principle: |
      If information is only needed when output template exists,
      it should BE IN the output template, not the skill doc.
      This prevents agents from attempting manual construction when
      output template is missing.
```

**The "Conditional Information" Principle:**

| Information Type | Where It Belongs |
|------------------|------------------|
| How to copy-paste output template | Skill doc (always needed) |
| Emoji meanings, formatting rules | Output template output (only needed when it exists) |
| Implementation functions | Handler code only (never in skill doc) |
| What to do if output template missing | Skill doc (FAIL instruction) |

**Example - M256 Pattern:**

```yaml
# WRONG: Skill doc teaches emoji selection
## Functions
### select_emoji(status) -> emoji
if status == "completed": return "☑️"
...
## Procedure
Step 1: Use output template...

# RIGHT: Move emoji info to output template
## Procedure
Step 1: Use output template...
# (Emoji reference appears IN the template content, not skill doc)
```

### 9. Implement Prevention

**MANDATORY: Take concrete action. Prevention without action changes nothing.**

The prevention step must result in a modified file - code, hook, configuration, or documentation.
If you finish this step without editing a file, you have not implemented prevention.

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

**For context-related mistakes:**

```yaml
prevention_action:
  if_context_related:
    # Context limits are fixed - see agent-architecture.md § Context Limit Constants
    primary:
      action: "Improve task size estimation"
      rationale: "Better estimates prevent exceeding limits"

    secondary:
      action: "Add quality checkpoint at 50% context"
      implementation: |
        At 50% context, pause and verify:
        - Is work quality consistent with early session?
        - Are earlier decisions still being referenced?
        - Should task be decomposed now?

    tertiary:
      action: "Enhance PLAN.md with explicit checkpoints"
      implementation: |
        Add context-aware milestones to task plans.
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

### 10. Verify Prevention Works

```yaml
verification:
  action: "Rerun similar task with new threshold"
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

**Category Reference:**

| Category | Description |
|----------|-------------|
| protocol_violation | Violated documented workflow, skill steps, or mandatory instructions |
| prompt_engineering | Subagent prompt lacked necessary instructions or constraints |
| context_degradation | Quality degraded due to context window pressure or compaction |
| tool_misuse | Used wrong tool, wrong flags, or misunderstood tool behavior |
| assumption_without_verification | Claimed state without measurement or verification |
| bash_error | Shell script error (syntax, reserved variables, compatibility) |
| git_operation_failure | Git command failed or produced unexpected results |
| build_failure | Compilation, checkstyle, PMD, or other build tool failure |
| test_failure | Test assertion failure or incorrect test construction |
| logical_error | Incorrect reasoning or misapplied rule |
| detection_gap | Monitoring/hook failed to catch a problem |
| architecture_issue | Structural or design-level mistake |
| documentation_violation | Violated documented standard (not workflow protocol) |
| giving_up | Presented options instead of implementing, or stopped prematurely |

**Prevention Type Reference:**

| Type | Level | Description | Example |
|------|-------|-------------|---------|
| code_fix | 1 | Make incorrect behavior impossible in code | Compile-time check, type system |
| hook | 2 | Automated enforcement via PreToolUse/PostToolUse | Block dangerous commands |
| validation | 3 | Automated check that catches mistakes early | Build verification, lint |
| config | 4 | Configuration change that affects behavior | Threshold adjustment, settings |
| skill | 5 | Update skill documentation with explicit guidance | Add anti-pattern section |
| process | 6 | Change workflow steps or ordering | Add mandatory checkpoint |
| documentation | 7 | Document to prevent future occurrence | Add to CLAUDE.md, style guide |

**Common Root Cause Patterns (check during 5-whys):**

| Pattern | Indicators | Typical Prevention |
|---------|------------|-------------------|
| Assumption without verification | "I assumed...", claimed state without measurement | Add verification step (hook/validation) |
| Completion bias | Rationalized ignoring protocol to finish task | Strengthen enforcement (hook/code_fix) |
| Memory reliance | Used memory instead of get-history/re-reading | Add verification requirement (process) |
| Environment state mismatch | Wrong directory, stale data, wrong branch | Add state verification (hook/validation) |
| Documentation ignored | Rule existed but wasn't followed | Escalate to hook/code_fix |
| Shell compatibility | zsh vs bash differences | Document in CLAUDE.md (documentation) |
| Ordering/timing | Operations in wrong sequence | Add explicit ordering (skill/process) |

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
  action: "Split task at 40K tokens, before degradation"
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
  - "Why 95K tokens?" -> "Task not decomposed"
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
  - Run similar task
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
agent: [invokes /cat:learn-from-mistakes skill]
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
- `cat:decompose-task` - Implements earlier decomposition
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
