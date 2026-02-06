# Phase 4: Record

This phase verifies the prevention works, records the learning in MEMORY.md, updates the retrospective counter, and commits.

## Your Task

Complete the recording phase for the learn skill. You will receive prevention results from Phase 3 as input.

Your final message must be ONLY the JSON result object with no surrounding text or explanation. The parent agent parses your response as JSON.

## Input

You will receive JSON objects from all previous phases containing:
- Investigation results (event sequence, documents read, priming analysis)
- Analysis results (mistake description, root cause, RCA method, category)
- Prevention results (prevention type, files modified, quality metrics)

## Step 10: Verify Prevention Works

```yaml
verification:
  action: "Rerun similar issue with new threshold"
  success_criteria:
    - Decomposition triggered before 60K tokens
    - No quality degradation observed
    - Original mistake type does not recur
```

## Step 11: Record Learning

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

  # (A022) BLOCKING: prevention_path must match a file listed in the BLOCKING GATE from Phase 3
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
    "mistake_count_threshold": 10,
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

## Step 12: Update Retrospective Counter and Commit

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

## Output Format

Your final message MUST be ONLY this JSON (no other text):

```json
{
  "phase": "record",
  "status": "COMPLETE",
  "user_summary": "1-3 sentence summary of what this phase did (for display to user between phases)",
  "learning_id": "M123",
  "memory_updated": true,
  "counter_updated": true,
  "committed": true,
  "commit_hash": "abc123",
  "retrospective_triggered": false,
  "retrospective_status": "5/10 mistakes, 3/7 days"
}
```
