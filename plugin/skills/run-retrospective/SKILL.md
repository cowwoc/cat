---
description: MANDATORY after learn-from-mistakes threshold - analyzes patterns and derives action items
user-invocable: true
---

# Run Retrospective

## Purpose

Execute scheduled retrospective analysis on accumulated mistakes. Identifies patterns, evaluates
action item effectiveness, derives new action items, and creates escalations for ineffective fixes.
Implements the full workflow defined in `retrospectives.json`.

## When to Use

- Automatically triggered by `learn-from-mistakes` when thresholds met
- Manually invoked with `/cat:run-retrospective`
- After significant project milestones
- When pattern recurrence is suspected

## Trigger Conditions

Retrospective is triggered when EITHER condition is met:

```yaml
triggers:
  time_based: days_since_last_retrospective >= trigger_interval_days  # default: 14
  count_based: mistake_count_since_last >= mistake_count_threshold    # default: 10
```

## Workflow

### 1. Check Trigger Conditions

```bash
RETRO_DIR=".claude/cat/retrospectives"
INDEX_FILE="$RETRO_DIR/index.json"

# Get config and state from index.json
INTERVAL=$(jq -r '.config.trigger_interval_days' "$INDEX_FILE")
THRESHOLD=$(jq -r '.config.mistake_count_threshold' "$INDEX_FILE")
LAST_RETRO=$(jq -r '.last_retrospective // empty' "$INDEX_FILE")
MISTAKES_SINCE=$(jq -r '.mistake_count_since_last' "$INDEX_FILE")

# Calculate days since last retrospective
if [[ -n "$LAST_RETRO" && "$LAST_RETRO" != "null" ]]; then
  LAST_EPOCH=$(date -d "$LAST_RETRO" +%s 2>/dev/null || echo 0)
else
  LAST_EPOCH=0
fi
NOW_EPOCH=$(date +%s)
DAYS_SINCE=$(( (NOW_EPOCH - LAST_EPOCH) / 86400 ))

# Check triggers
if [[ $DAYS_SINCE -ge $INTERVAL ]] || [[ $MISTAKES_SINCE -ge $THRESHOLD ]]; then
  echo "RETROSPECTIVE TRIGGERED"
  echo "  Days since last: $DAYS_SINCE (threshold: $INTERVAL)"
  echo "  Mistakes since last: $MISTAKES_SINCE (threshold: $THRESHOLD)"
else
  echo "No retrospective needed"
  echo "  Days since last: $DAYS_SINCE / $INTERVAL"
  echo "  Mistakes since last: $MISTAKES_SINCE / $THRESHOLD"
  exit 0
fi
```

### 2. Gather Mistakes for Analysis

```bash
# Aggregate ALL mistakes since last retrospective across all split files
if [[ -n "$LAST_RETRO" && "$LAST_RETRO" != "null" ]]; then
  MISTAKES_TO_ANALYZE=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
    jq -s --arg last "$LAST_RETRO" \
    '[.[].mistakes[] | select(.timestamp > $last)]')
else
  # No previous retrospective - analyze all mistakes
  MISTAKES_TO_ANALYZE=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
    jq -s '[.[].mistakes[]]')
fi

MISTAKE_COUNT=$(echo "$MISTAKES_TO_ANALYZE" | jq 'length')
echo "Analyzing $MISTAKE_COUNT mistakes since $LAST_RETRO"
```

### 3. Analyze by Category

```yaml
category_analysis:
  query: |
    # Use pre-aggregated MISTAKES_TO_ANALYZE from step 2
    echo "$MISTAKES_TO_ANALYZE" | jq '
      group_by(.category)
      | map({category: .[0].category, count: length, ids: [.[].id]})
      | sort_by(-.count)
    '

  output_format:
    - category: protocol_violation
      count: 7
      ids: [M028, M034, M035, ...]
    - category: build_failure
      count: 4
      ids: [M029, M030, ...]
```

### 4. Check Action Item Effectiveness

For each existing action item with `status: implemented`:

```yaml
effectiveness_check:
  for_each_action_item:
    # Find mistakes matching this action's pattern AFTER completion
    query: |
      COMPLETED=$(jq -r --arg id "A004" '.action_items[] | select(.id == $id) | .completed_date' "$INDEX_FILE")
      PATTERN=$(jq -r --arg id "A004" '.action_items[] | select(.id == $id) | .pattern_id' "$INDEX_FILE")

      # Count post-fix mistakes across all split files
      POST_FIX=$(cat "$RETRO_DIR"/mistakes-*.json 2>/dev/null | \
        jq -s --arg pattern "$PATTERN" --arg completed "$COMPLETED" \
        '[.[].mistakes[] | select(.pattern_keywords | contains([$pattern])) | select(.timestamp > $completed)] | length')

    verdicts:
      effective: post_fix_count == 0
      partially_effective: post_fix_count > 0 AND post_fix_count < pre_fix_count
      ineffective: post_fix_count >= pre_fix_count
      escalate: post_fix_count >= recurrence_after_fix_threshold  # default: 1
```

### 5. Identify New Patterns

```yaml
pattern_identification:
  steps:
    - Group unaddressed mistakes by category
    - Look for common keywords across mistakes
    - Check if pattern matches existing pattern_id
    - If new pattern with >= 2 occurrences, create PATTERN-XXX

  new_pattern_template:
    pattern_id: "PATTERN-007"
    pattern: "{category}"
    occurrences_total: 3
    occurrences_after_fix: 0
    first_seen: "{earliest_timestamp}"
    last_seen: "{latest_timestamp}"
    last_action_date: null
    status: "new"
    effectiveness: "pending"
    preventions: []
    related_action_items: []
    note: "{description of pattern}"
```

### 6. Derive Action Items

For each identified pattern without an action item:

```yaml
action_item_derivation:
  priority_rules:
    high:
      - Pattern count >= 5
      - Category is git_operation_failure or protocol_violation
      - Escalation of previous action
    medium:
      - Pattern count >= 3
      - Category is build_failure or test_failure
    low:
      - Pattern count >= 2
      - Category is documentation or detection_gap

  action_template:
    id: "A008"
    priority: "{calculated}"
    description: "{derived from pattern analysis}"
    category: "{pattern_category}"
    pattern_id: "PATTERN-007"
    status: "open"
    created_date: "{now}"
    completed_date: null
    related_mistakes: ["M042", "M043", "M044"]
    effectiveness_check:
      mistakes_before: 3
      mistakes_after: null
      post_fix_mistakes: []
      verdict: "pending"
```

### 7. Create Escalations

When action item is ineffective:

```yaml
escalation:
  trigger: effectiveness_verdict == "escalate" OR "ineffective"

  template:
    id: "ESCALATE-{date}-{seq}"
    original_action_id: "A004"
    original_fix_description: "{from action item}"
    failure_analysis:
      expected_result: "{what the fix was supposed to do}"
      actual_result: "{N} new failures in period"
      root_cause: "{analyze why fix didn't work}"
      gap_identified: "{what was missing}"
    proposed_solution:
      approach: "{defense-in-depth|alternative|enhancement}"
      description: "{new proposed fix}"
      prevention_type: "{code|hook|skill|threshold}"
      layers: ["{layer1}", "{layer2}"]
    priority: "high"
    status: "open"
```

### 8. Update index.json and Create Retrospective Record

```bash
# Get current year-month for retrospective split file
YEAR_MONTH=$(date +%Y-%m)
RETRO_SPLIT_FILE="$RETRO_DIR/retrospectives-${YEAR_MONTH}.json"
TIMESTAMP=$(date -Iseconds)

# Initialize retrospective split file if needed
if [ ! -f "$RETRO_SPLIT_FILE" ]; then
  echo "{\"period\":\"$YEAR_MONTH\",\"retrospectives\":[]}" > "$RETRO_SPLIT_FILE"
  # Add to index
  jq --arg f "retrospectives-${YEAR_MONTH}.json" \
    'if (.files.retrospectives | index($f)) then . else .files.retrospectives += [$f] | .files.retrospectives |= sort end' \
    "$INDEX_FILE" > "$INDEX_FILE.tmp" && mv "$INDEX_FILE.tmp" "$INDEX_FILE"
fi

# Get next retrospective ID across all split files
MAX_RETRO_NUM=$(cat "$RETRO_DIR"/retrospectives-*.json 2>/dev/null | \
  jq -s '[.[].retrospectives[].id] | map(select(startswith("R")) | ltrimstr("R") | tonumber) | max // 0')
RETRO_ID="R$(printf '%03d' $((MAX_RETRO_NUM + 1)))"

# Build and append retrospective entry to split file
jq --arg id "$RETRO_ID" \
   --arg ts "$TIMESTAMP" \
   --arg trigger "$TRIGGER_TYPE" \
   --arg period "${LAST_RETRO} to ${TIMESTAMP}" \
   --argjson count "$MISTAKE_COUNT" \
   --argjson findings "$FINDINGS_JSON" \
   '.retrospectives += [{
     id: $id,
     timestamp: $ts,
     trigger: $trigger,
     period_analyzed: $period,
     mistakes_analyzed: $count,
     summary: "...",
     findings: $findings
   }]' "$RETRO_SPLIT_FILE" > "$RETRO_SPLIT_FILE.tmp" && mv "$RETRO_SPLIT_FILE.tmp" "$RETRO_SPLIT_FILE"

# Update index.json with new state
jq --arg ts "$TIMESTAMP" \
   '.last_retrospective = $ts | .mistake_count_since_last = 0' \
   "$INDEX_FILE" > "$INDEX_FILE.tmp" && mv "$INDEX_FILE.tmp" "$INDEX_FILE"
```

### 9. Present Action Items for Execution

```yaml
output_format:
  summary: |
    ## Retrospective R003 Complete

    **Period:** 2026-01-08 to 2026-01-11
    **Mistakes Analyzed:** 5

    ### Category Breakdown
    - protocol_violation: 2
    - prompt_engineering: 2
    - git_operation_failure: 1

    ### Action Item Effectiveness
    - A001: effective
    - A004: escalated -> ESCALATE-2026-01-11-001

    ### New Action Items
    - A008 (high): Add positive verification requirement to all validation hooks
    - A009 (medium): Document common prompt engineering pitfalls

    ### Escalations
    - ESCALATE-2026-01-11-001: Git operations need PreToolUse hook for destructive commands

  next_steps: |
    Execute action items in priority order:
    1. [A008] - /cat:execute-action-item A008
    2. [A009] - /cat:execute-action-item A009
    3. [ESCALATE-2026-01-11-001] - Requires immediate attention
```

### 10. Execute Action Items (Optional)

If user requests execution:

```yaml
execution_workflow:
  for_each_action_item:
    - Read action item description
    - Determine implementation approach:
        hook: Create/update hook in .claude/settings.json
        skill: Create/update skill in cat/skills/
        code: Modify source code
        documentation: Update CLAUDE.md or skill docs
    - Implement the fix
    - Update action_item.status = "implemented"
    - Update action_item.completed_date = now
    - Commit changes with reference to action ID
```

## Anti-Patterns

### Check existing action effectiveness first

```yaml
# ❌ Just add new action items
new_action: "Add hook for X"
# Never checked if previous hook worked!

# ✅ Check existing items first
check: "A004 had 3 post-fix failures -> escalate"
then: "Create ESCALATE with root cause analysis"
```

### Create specific, actionable items

```yaml
# ❌ Vague
description: "Improve git operations"

# ✅ Specific
description: "Add PreToolUse hook to block `git reflog expire` and `git gc --prune` commands"
```

### Treat escalations as high priority

```yaml
# ❌ Create escalation, never act
escalation: "ESCALATE-2026-01-08-001"
status: "open"  # Forever

# ✅ Escalations are HIGH priority
escalation: "ESCALATE-2026-01-08-001"
# Immediately: propose solution, get approval, implement
```

## Integration with learn-from-mistakes

At the end of `learn-from-mistakes` Step 12 (Update Retrospective Counter), add:

```yaml
trigger_check:
  action: "Check if retrospective is needed"
  command: |
    INDEX_FILE=".claude/cat/retrospectives/index.json"

    # Increment mistake counter
    jq '.mistake_count_since_last += 1' "$INDEX_FILE" > "$INDEX_FILE.tmp" \
      && mv "$INDEX_FILE.tmp" "$INDEX_FILE"

    # Check thresholds
    MISTAKES=$(jq '.mistake_count_since_last' "$INDEX_FILE")
    THRESHOLD=$(jq '.config.mistake_count_threshold' "$INDEX_FILE")
    if [[ $MISTAKES -ge $THRESHOLD ]]; then
      echo "RETROSPECTIVE THRESHOLD REACHED ($MISTAKES >= $THRESHOLD)"
      echo "Run: /cat:run-retrospective"
    fi
```

## Verification Checklist

Before completing retrospective:

- [ ] All mistakes since last retrospective analyzed
- [ ] Category breakdown computed correctly
- [ ] Existing action item effectiveness checked
- [ ] New patterns identified (if any)
- [ ] New action items derived for unaddressed patterns
- [ ] Escalations created for ineffective actions
- [ ] Retrospective entry written to split file
- [ ] index.json updated (last_retrospective, counter reset)
- [ ] Files committed to repository

## Related Skills

- `cat:learn-from-mistakes` - Records individual mistakes, triggers this skill
- `cat:token-report` - Provides context metrics for analysis
- `cat:work` - Can be used to implement action items
