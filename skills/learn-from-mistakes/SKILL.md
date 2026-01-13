---
name: cat:learn-from-mistakes
description: Analyze mistakes with conversation length as potential cause (CAT-specific)
---

# Learn From Mistakes (CAT-Specific)

## Purpose

Analyze mistakes using 5-whys methodology with CAT-specific consideration of conversation length
and context degradation as potential root causes. Integrates with token tracking data to identify
context-related failure patterns and recommend preventive measures including earlier decomposition.

## When to Use

- Any mistake occurs during CAT orchestration
- Subagent produces incorrect or incomplete results
- Task requires rework or correction
- Build failures, test failures, logical errors
- Repeated attempts at same operation
- Quality degradation observed over time

## Workflow

### 1. Document the Mistake

```yaml
mistake:
  timestamp: 2026-01-10T16:30:00Z
  type: incorrect_implementation
  description: |
    Subagent implemented parser with wrong precedence rules.
    Expressions like "a + b * c" parsed as "(a + b) * c" instead
    of "a + (b * c)".
  impact: |
    All tests using operator precedence failing.
    Required complete rewrite of expression parsing.
```

### 2. Gather Context Metrics

**CAT-specific: Always collect token data**

```bash
SESSION_ID="${SUBAGENT_SESSION}"
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"

# Token usage at time of mistake
TOKENS_AT_ERROR=$(jq -s 'map(select(.type == "assistant")) |
  map(.message.usage | .input_tokens + .output_tokens) | add' "${SESSION_FILE}")

# Compaction events before mistake
COMPACTIONS=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")

# Messages before mistake
MESSAGE_COUNT=$(jq -s '[.[] | select(.type == "assistant")] | length' "${SESSION_FILE}")

# Time since session start
SESSION_DURATION=$(calculate_duration "${SESSION_FILE}")
```

### 3. Perform 5-Whys Analysis

Standard analysis with CAT-specific consideration:

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
category: CONTEXT_DEGRADATION
```

### 4. Check for Context Degradation Patterns

**CAT-specific analysis checklist:**

```yaml
context_degradation_analysis:
  # Token-related factors
  tokens_at_error: 95000
  threshold_exceeded: true  # > 80K
  threshold_exceeded_by: 15000

  # Compaction factors
  compaction_events: 2
  errors_after_compaction: true

  # Temporal factors
  session_duration: 4.5 hours
  messages_before_error: 127

  # Quality trend
  early_session_quality: high
  late_session_quality: degraded
  quality_degradation_detected: true

  # Conclusion
  context_related: LIKELY
  confidence: 0.85
```

### 5. Identify Prevention Level

Standard hierarchy with CAT additions:

```yaml
prevention_hierarchy:
  # Level 1: Code fix (best)
  - level: 1
    type: code_fix
    description: "Make code self-correcting or impossible to get wrong"

  # Level 2: CAT-specific - Earlier decomposition
  - level: 2
    type: earlier_decomposition
    description: "Trigger task split before context degradation occurs"
    cat_specific: true

  # Level 3: Validation/hook
  - level: 3
    type: validation
    description: "Add automated checks that catch the mistake early"

  # Level 4: Lower threshold
  - level: 4
    type: threshold_adjustment
    description: "Reduce context threshold from 40% to more conservative value"
    cat_specific: true

  # Level 5: Process change
  - level: 5
    type: process
    description: "Change workflow to prevent mistake"

  # Level 6: Documentation (last resort)
  - level: 6
    type: documentation
    description: "Document to prevent future occurrence"
```

### 6. Evaluate Prevention Quality

**BEFORE implementing, verify the prevention is robust:**

```yaml
prevention_quality_check:
  # Positive vs Negative verification
  verification_type:
    positive: "Check for PRESENCE of correct behavior"  # ✅ Preferred
    negative: "Check for ABSENCE of specific failure"   # ❌ Fragile

  # Ask: Am I checking for what I WANT, or what I DON'T want?
  # Example:
  #   ❌ grep "Initial implementation -"  (catches ONE placeholder pattern)
  #   ✅ grep "^- \`[a-f0-9]{7,}\`"        (checks for correct commit format)

  # Generality test
  generality:
    question: "If the failure mode varies slightly, will this still catch it?"
    examples:
      - "What if placeholder text changes from 'Initial' to 'First'?"
      - "What if someone uses 'TBD' or 'TODO' instead?"
      - "What if the format is subtly wrong in a different way?"

    # If answer is NO → prevention is too specific → redesign

  # Inversion test
  inversion:
    question: "Can I invert this check to verify correctness instead?"
    pattern: |
      Instead of: "Fail if BAD_PATTERN exists"
      Try:        "Fail if GOOD_PATTERN is missing"

    # Positive verification catches ALL failures, not just anticipated ones

  # Fragility score
  fragility_assessment:
    low:    "Checks for correct format/behavior (positive verification)"
    medium: "Checks for category of errors (e.g., any TODO-like text)"
    high:   "Checks for exact observed failure (specific string match)"
```

**Decision gate:** If fragility is HIGH, redesign the prevention before implementing.

### 7. Implement Prevention

For context-related mistakes:

```yaml
prevention_action:
  if_context_related:
    primary:
      action: "Adjust token monitoring threshold"
      current_threshold: 80000  # 40%
      new_threshold: 60000      # 30%
      rationale: "Earlier warning gives time to decompose"

    secondary:
      action: "Add quality checkpoint at 50% context"
      implementation: |
        At 50% context (100K tokens), pause and verify:
        - Is work quality consistent with early session?
        - Are earlier decisions still being referenced?
        - Should task be decomposed now?

    tertiary:
      action: "Enhance PLAN.md with explicit checkpoints"
      implementation: |
        Add context-aware milestones to task plans.
        Each milestone = potential decomposition point.
```

### 8. Verify Prevention Works

```yaml
verification:
  action: "Rerun similar task with new threshold"
  success_criteria:
    - Decomposition triggered before 60K tokens
    - No quality degradation observed
    - Original mistake type does not recur
```

### 9. Record Learning

**MANDATORY: Persist learning to file, not just context.**

**CRITICAL VALIDATION - Before recording, verify prevention is REAL:**

```yaml
prevention_path_validation:
  # INVALID paths (will cause recurrence):
  invalid_examples:
    - "N/A"
    - "N/A - behavioral change"
    - "behavioral"
    - "process change"
    - ""  # empty
    - "TBD"

  # VALID paths (actual files that were changed):
  valid_examples:
    - "/workspace/cat/commands/execute-task.md"
    - ".claude/hooks/validate-commit.sh"
    - "src/main/java/Parser.java"

  # Rule: prevention_path MUST be a real file path that was actually modified
  # "Behavioral change" without enforcement is NOT prevention - it WILL recur
```

**If you cannot identify a real file to change, you have NOT implemented prevention.**
Go back to step 7 and find a code/config/documentation fix.

**File:** `.claude/cat/retrospectives/mistakes.json`

**CRITICAL PATH CHECK**: Files MUST be in `.claude/cat/retrospectives/`, NOT `.claude/retrospectives/`.
If files exist at wrong location, migrate them first:

```bash
# Check for files at wrong location and migrate
if [ -d .claude/retrospectives ] && [ ! -d .claude/cat/retrospectives ]; then
  mkdir -p .claude/cat/retrospectives
  mv .claude/retrospectives/*.json .claude/cat/retrospectives/ 2>/dev/null || true
  rmdir .claude/retrospectives 2>/dev/null || true
fi
```

```bash
# Create retrospectives directory if needed
mkdir -p .claude/cat/retrospectives

# Initialize mistakes.json if it doesn't exist
[ -f .claude/cat/retrospectives/mistakes.json ] || echo '[]' > .claude/cat/retrospectives/mistakes.json

# Determine next mistake ID
LAST_ID=$(jq -r 'map(.id) | map(select(startswith("M"))) | sort | last // "M000"' \
  .claude/cat/retrospectives/mistakes.json)
NEXT_NUM=$((${LAST_ID#M} + 1))
NEXT_ID=$(printf "M%03d" $NEXT_NUM)
```

**Append entry to mistakes.json:**

```json
{
  "id": "{NEXT_ID}",
  "timestamp": "{ISO-8601 timestamp}",
  "category": "{protocol_violation|prompt_engineering|context_degradation|tool_misuse}",
  "description": "{One-line description of the mistake}",
  "root_cause": "{Root cause from 5-whys analysis}",
  "prevention_type": "{code|hook|skill|threshold|process|documentation}",
  "prevention_path": "{path/to/file/changed}",
  "pattern_keywords": ["{keyword1}", "{keyword2}"],
  "prevention_implemented": true,
  "prevention_verified": true,
  "prevention_quality": {
    "verification_type": "{positive|negative}",
    "fragility": "{low|medium|high}",
    "catches_variations": true
  },
  "correct_behavior": "{What should be done instead}"
}
```

**Use jq to append (safe for concurrent access):**

```bash
jq --argjson new '{...new entry...}' '. += [$new]' \
  .claude/cat/retrospectives/mistakes.json > .claude/cat/retrospectives/mistakes.json.tmp \
  && mv .claude/cat/retrospectives/mistakes.json.tmp .claude/cat/retrospectives/mistakes.json
```

### 10. Update Retrospective Counter and Commit

**MANDATORY: Update counter and commit BOTH files together.**

```bash
RETRO_FILE=".claude/cat/retrospectives/retrospectives.json"

# Increment mistake counter
jq '.mistake_count_since_last += 1' "$RETRO_FILE" > "$RETRO_FILE.tmp" \
  && mv "$RETRO_FILE.tmp" "$RETRO_FILE"

# Commit BOTH files together (mistakes.json + retrospectives.json)
git add .claude/cat/retrospectives/mistakes.json .claude/cat/retrospectives/retrospectives.json
git commit -m "docs: record learning ${NEXT_ID} - {short description}"

# Get current values to check trigger
MISTAKES=$(jq '.mistake_count_since_last' "$RETRO_FILE")
THRESHOLD=$(jq '.config.mistake_count_threshold' "$RETRO_FILE")
LAST_RETRO=$(jq -r '.last_retrospective' "$RETRO_FILE")
INTERVAL=$(jq '.config.trigger_interval_days' "$RETRO_FILE")

# Calculate days since last retrospective
LAST_EPOCH=$(date -d "$LAST_RETRO" +%s 2>/dev/null || echo 0)
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

**If triggered, invoke the retrospective skill:**

```yaml
retrospective_trigger:
  condition: mistakes >= threshold OR days >= interval
  action: "Invoke /cat:run-retrospective"
  workflow:
    - Analyze mistakes since last retrospective
    - Check action item effectiveness
    - Derive new action items
    - Create escalations for ineffective fixes
    - Present action items for execution
```

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

### Do NOT ignore token metrics

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

### Do NOT assume all mistakes are context-related

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

### Do NOT adjust thresholds without data

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

### Do NOT skip verification

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

### Do NOT use fragile negative verification

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
# Positive verification catches all failures, not just anticipated ones
```

## Related Skills

- `cat:run-retrospective` - Aggregate analysis triggered by this skill
- `cat:token-report` - Provides data for context analysis
- `cat:decompose-task` - Implements earlier decomposition
- `cat:monitor-subagents` - Catches context issues early
- `cat:collect-results` - Preserves progress before intervention
