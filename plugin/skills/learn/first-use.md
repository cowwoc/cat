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
  operations. Note: `record-learning` commits to the active worktree branch (not the main workspace), so the counter
  increment and prevention commit are isolated to the worktree until merge. Background is safe mid-operation as long as
  the retrospective post-handler (counter reset) is deferred until after the worktree is merged and closed.
- **FOREGROUND:** Learn was explicitly invoked standalone (no issue work in progress).

**Default:** Use background when mid-operation. Use foreground when standalone.

## Step 3: Spawn Subagent

**Background mode:** When background mode was selected in Step 2, inform the user before spawning:
"Running learn analysis in background — will notify when complete."
Then spawn the subagent with `run_in_background: true` (see Task tool parameters below). Control returns immediately
to the caller.

**Persist spawn time (CRITICAL):** Immediately before issuing the Task tool call, record the spawn time in a file.
In foreground mode, record it in a shell variable (`SPAWN_EPOCH=$(date +%s)`). In background mode, MUST write to a
session-isolated file with tamper-resistant naming to ensure the file path is unique per instance and cannot be
fabricated by concurrent processes:

```bash
# FOREGROUND mode: use shell variable (sufficient for immediate Step 4 execution)
SPAWN_EPOCH=$(date +%s)

# BACKGROUND mode: write to session-isolated file with tamper-resistant naming
SPAWN_EPOCH=$(date +%s%N)  # Millisecond precision to prevent same-second exploits
# File path MUST include session ID to prevent cross-instance collision
# and use full hash (not truncated) to eliminate collision risk
SPAWN_EPOCH_HASH=$(printf "%s:%s" "$CLAUDE_SESSION_ID" "$SPAWN_EPOCH" | sha256sum | cut -d' ' -f1)
SPAWN_EPOCH_FILE="${CLAUDE_CONFIG_DIR}/projects/-workspace/cat/runtime/${CLAUDE_SESSION_ID}/learn-spawn-epoch.${SPAWN_EPOCH_HASH}"
mkdir -p "$(dirname "$SPAWN_EPOCH_FILE")"
printf '%s:%s' "$SPAWN_EPOCH" "$SPAWN_EPOCH_HASH" > "$SPAWN_EPOCH_FILE"
# Pass SPAWN_EPOCH_FILE path to background task completion notification handler
# When background task notification arrives, verify checksum then read:
# FILE_CONTENT=$(cat "$SPAWN_EPOCH_FILE")
# STORED_EPOCH="${FILE_CONTENT%%:*}"
# STORED_HASH="${FILE_CONTENT##*:}"
# RECOMPUTED_HASH=$(printf "%s:%s" "$CLAUDE_SESSION_ID" "$STORED_EPOCH" | sha256sum | cut -d' ' -f1)
# if [[ "$STORED_HASH" != "$RECOMPUTED_HASH" ]]; then ERROR: Spawn time file was tampered with; fi
# SPAWN_EPOCH="$STORED_EPOCH"
```

In background mode, include `SPAWN_EPOCH_FILE` in the subagent's JSON output or pass it as a parameter to Step 4
handler. Step 4b MUST use the persisted value, not a reconstructed or assumed one. MANDATORY: Verify the checksum
before using SPAWN_EPOCH to detect tampering.

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
> 3. Generate an internal_summary (1-3 sentences) of what you found/did
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
> Your final message must be ONLY this JSON object (no other text) — the main agent will parse this to orchestrate the next phase. Copy and fill in the values:
>
> ```json
> {
>   "phases_executed": ["investigate", "analyze", "prevent"],
>   "phase_summaries": {
>     "investigate": "1-3 sentence summary",
>     "analyze": "1-3 sentence summary",
>     "prevent": "1-3 sentence summary"
>   },
>   "investigate": { ...phase 1 output fields... },
>   "analyze": { ...phase 2 output fields... },
>   "prevent": { ...phase 3 output fields... }
> }
> ```

## Step 4: Validate Subagent Output, Verify Commit, Run record-learning CLI (Phase 4)

**Note:** If the subagent was spawned in background (Step 2), Steps 4-7 execute when the background task
notification arrives, not immediately after Step 3. MANDATORY: The orchestrator MUST implement a timeout mechanism
to detect stalled background tasks. The timeout MUST be enforced in code (not advisory): if the background notification
does not arrive within **150 seconds** (5 minutes, supporting complex multi-phase analyses), the orchestrator MUST
display this recovery message and stop waiting:
```
The background learn task appears to be stalled (no notification received after 150 seconds).
Options:
1. Check task logs: Invoke /cat:get-subagent-status-agent to inspect background task status
2. Manual recovery: Invoke /cat:learn in foreground mode to re-run the analysis
```
Timeout enforcement example (MANDATORY in code):
```bash
START_TIME=$(date +%s)
TIMEOUT=150
while [[ $(($(date +%s) - START_TIME)) -lt $TIMEOUT ]]; do
  if [[ -f "$NOTIFICATION_FILE" ]]; then
    # Process notification (Steps 4-7)
    break
  fi
  sleep 2  # Poll every 2 seconds
done
if [[ $(($(date +%s) - START_TIME)) -ge $TIMEOUT ]]; then
  echo "ERROR: Background task timeout (150s exceeded). Learning NOT recorded."
  exit 1
fi
```

### Step 4a: Validate required fields

Before invoking `record-learning`, validate that all of the following fields are present and non-null in the
subagent JSON. If any field is missing, empty, or null, stop and display an error listing every missing field —
do NOT proceed to Step 4b or 4c.

Required fields (exhaustive — no others are treated as required):
- `phases_executed`: array containing EXACTLY the three strings `"investigate"`, `"analyze"`, and `"prevent"` —
  no duplicates, no extra values, any order accepted. **MANDATORY VALIDATION:** Check that all three specific strings
  are present using set logic (all three values present, no others, no duplicates). Validation must be exhaustive:
  count the array length must equal exactly 3, THEN verify each of the three required strings appears exactly once.
  If the array length is not 3, if any required value is missing, if unrecognized values are present, or if any value
  appears more than once, stop with an error. Example check script (MUST be machine-enforced, not advisory):
  ```bash
  # Verify phases_executed contains exactly ["investigate", "analyze", "prevent"]
  # Extract array and validate using explicit occurrence counting
  PHASES_EXECUTED_JSON=$(echo "$SUBAGENT_JSON" | grep -o '"phases_executed":\s*\[[^]]*\]')

  # Count occurrences of each required phase
  INVESTIGATE_COUNT=$(echo "$PHASES_EXECUTED_JSON" | grep -o '"investigate"' | wc -l)
  ANALYZE_COUNT=$(echo "$PHASES_EXECUTED_JSON" | grep -o '"analyze"' | wc -l)
  PREVENT_COUNT=$(echo "$PHASES_EXECUTED_JSON" | grep -o '"prevent"' | wc -l)

  # Count total elements (non-empty strings between commas)
  TOTAL_COUNT=$(echo "$PHASES_EXECUTED_JSON" | grep -o '"[^"]*"' | wc -l)

  # Validate: all three present exactly once, total is 3
  if [[ $INVESTIGATE_COUNT -ne 1 ]] || [[ $ANALYZE_COUNT -ne 1 ]] || [[ $PREVENT_COUNT -ne 1 ]] || [[ $TOTAL_COUNT -ne 3 ]]; then
    echo "ERROR: phases_executed must contain exactly one of each: investigate, analyze, prevent"
    echo "Found: investigate=$INVESTIGATE_COUNT, analyze=$ANALYZE_COUNT, prevent=$PREVENT_COUNT, total=$TOTAL_COUNT"
    exit 1
  fi
  ```
- `phase_summaries.investigate`: string of at least 20 UTF-8 characters (count via `expr "${#string}"`) that is NOT a hollow placeholder. MANDATORY validation: Extract string, count UTF-8 characters using `expr length "$STR"`, verify ≥ 20. Then check for empty string: `if [[ -z "$STR" ]]` — reject. Then validate using this bash rule (machine-enforced):
  ```bash
  FORBIDDEN_WORDS="\\b(phase|done|completed|ok|fine|found|summary|investigation|results|analysis|prevent|prevention)\\b"
  # Remove all forbidden words (case-insensitive) and common connectors
  MEANINGFUL=$(echo "$STR" | sed -E 's/\b(phase|done|completed|ok|fine|found|summary|investigation|results|analysis|prevent|prevention|is|of|the|and|or|a|an|to|in|at|on|by|for|with|from|as|if|this|that|these|those|was|been|be|have|has|had|do|does|did)\b//gi' | sed 's/[[:space:]]*//g')
  if [[ -z "$MEANINGFUL" ]]; then
    echo "ERROR: phase_summaries.investigate is a hollow placeholder (contains only forbidden words)"
    exit 1
  fi
  ```
- `phase_summaries.analyze`: string of at least 20 UTF-8 characters, not a hollow placeholder (same machine-enforced validation as investigate)
- `phase_summaries.prevent`: string of at least 20 UTF-8 characters, not a hollow placeholder (same machine-enforced validation as investigate)
- `investigate`: object with ALL required keys from phase-investigate.md output format. Not "at least one" — ALL keys
  must be present. Required keys for investigate (MANDATORY to validate EACH of these): `event_sequence`, `documents_read`, `priming_analysis`, `session_id`.
  If any key is missing, stop with an error naming each missing key.
- `analyze`: object with ALL required keys. Required keys (MANDATORY to validate EACH using exhaustive field checks):
  `mistake_description`, `root_cause`, `cause_signature`, `rca_depth_verified`, `rca_depth_check`, `rca_depth_evidence`,
  `category`. If any key is missing, stop with an error naming each missing key.
- `prevent`: object with ALL required keys. Required keys (MANDATORY to validate EACH using exhaustive field checks): `prevention_implemented`, `prevention_commit_hash`, `prevention_path` (when hash is non-null), `issue_creation_info` (when prevention_implemented is false). If any key is missing, stop with an error naming each missing key.
- `prevent.prevention_implemented`: boolean (true or false — must be explicitly present)
- `prevent.prevention_commit_hash`: string or null (must be explicitly present)
- `prevent.prevention_path`: non-empty string (must be explicitly present when `prevention_commit_hash` is non-null)
- **Cross-field rule (bidirectional, MANDATORY enforcement):** Enforce ALL of the following in code:
  - If `prevention_implemented` is `true`, then `prevention_commit_hash` MUST be non-null.
  - If `prevention_commit_hash` is non-null, then `prevention_implemented` MUST be `true`.
  - If `prevention_implemented` is false, then `issue_creation_info` MUST be non-empty AND `prevention_commit_hash` MUST be null.
  - If `prevention_implemented` is true, then `issue_creation_info` MUST be absent or explicitly empty/null AND `prevention_commit_hash` MUST be non-null.

  Machine-enforced validation example:
  ```bash
  IMPLEMENTED=$(echo "$PREVENT_JSON" | grep -o '"prevention_implemented"[[:space:]]*:[[:space:]]*[^,}]*' | grep -o '[^:]*$' | tr -d ' ')
  HASH=$(echo "$PREVENT_JSON" | grep -o '"prevention_commit_hash"[[:space:]]*:[[:space:]]*[^,}]*' | grep -o '[^:]*$' | tr -d ' ')
  ISSUE_INFO=$(echo "$PREVENT_JSON" | grep -o '"issue_creation_info"[[:space:]]*:[[:space:]]*[^}]*' | sed 's/.*issue_creation_info[[:space:]]*:[[:space:]]*//g')

  # Both direction validation
  if [[ "$IMPLEMENTED" == "true" ]] && [[ "$HASH" == "null" ]]; then exit 1; fi
  if [[ "$HASH" != "null" ]] && [[ "$IMPLEMENTED" != "true" ]]; then exit 1; fi
  if [[ "$IMPLEMENTED" == "false" ]] && [[ -z "$ISSUE_INFO" ]]; then exit 1; fi
  if [[ "$IMPLEMENTED" == "true" ]] && [[ -n "$ISSUE_INFO" ]]; then exit 1; fi
  ```

**Validation order:** Check ALL fields in Steps 4a and 4b BEFORE proceeding to Step 4c. Use explicit boolean checkpoint markers (see Step 4c) to enforce this in code. If multiple fields are missing or invalid, report ALL of them in the error message, not just the first one found.

**Error display format:**
```
ERROR: Subagent output is missing required fields. Learning NOT recorded.
Missing/invalid fields: [list each missing or invalid field with reason]
Resolution: Re-run the learn skill or inspect subagent output for fabricated values.
Raw subagent output: {raw output}
```

### Step 4b: Verify prevention commit hash

If `prevent.prevention_commit_hash` is non-null, perform ALL three checks below in order before proceeding.
Any failing check stops the process with the associated error — do NOT proceed to Step 4c.

**Check 1 — Hash exists in git:**
```bash
COMMIT_HASH="{prevent.prevention_commit_hash from subagent JSON}"
# Validate hash is hexadecimal (7-64 chars) before passing to git — prevents shell metacharacter injection
if ! [[ "$COMMIT_HASH" =~ ^[0-9a-fA-F]{7,64}$ ]]; then
  echo "ERROR: prevention_commit_hash '${COMMIT_HASH}' is not a valid hex hash. Learning NOT recorded."
  exit 1
fi
if ! git cat-file -e "${COMMIT_HASH}^{commit}" 2>/dev/null; then
  echo "ERROR: prevention_commit_hash '${COMMIT_HASH}' does not exist in git. Learning NOT recorded."
  echo "The subagent may have fabricated a commit hash. Verify prevention was actually committed."
  exit 1
fi
```

**Check 2 — Commit timestamp is after subagent spawn AND is on current branch:**
Use the `SPAWN_EPOCH` value recorded in Step 3 immediately before the Task tool call. Commit timestamp MUST be
strictly greater than spawn_epoch. Additionally, the commit MUST be reachable from HEAD (not a commit from a different branch reused in cross-session attack).

```bash
COMMIT_TIME=$(git log -1 --format="%ct" "${COMMIT_HASH}")
# Validate COMMIT_TIME is numeric before arithmetic comparison — empty or non-numeric values cause bash errors
if ! [[ "$COMMIT_TIME" =~ ^[0-9]+$ ]]; then
  echo "ERROR: Could not retrieve commit timestamp for '${COMMIT_HASH}' (got: '${COMMIT_TIME}'). Learning NOT recorded."
  exit 1
fi
# Use strictly greater than (not >=) to prevent same-second timestamp exploits
if [[ "$COMMIT_TIME" -le "$SPAWN_EPOCH" ]]; then
  echo "ERROR: prevention_commit_hash '${COMMIT_HASH}' does not postdate subagent spawn (commit: ${COMMIT_TIME}, spawn: ${SPAWN_EPOCH})."
  echo "Commit timestamp must be strictly greater than spawn time. The subagent may have reused a pre-existing commit or exploited second-level granularity."
  exit 1
fi
# Verify commit is a descendant of current HEAD (not a pre-existing commit from another branch)
if ! git merge-base --is-ancestor "${COMMIT_HASH}" HEAD 2>/dev/null; then
  echo "ERROR: prevention_commit_hash '${COMMIT_HASH}' is not a descendant of current HEAD. Learning NOT recorded."
  echo "Commit may have been reused from a different branch or session. Only commits created on this branch are valid."
  exit 1
fi
```

**Check 3 — Commit touches file AND change implements prevention:**

`prevention_path` is guaranteed non-empty here (validated in Step 4a when `prevention_commit_hash` is non-null).

```bash
PREVENTION_PATH="{prevent.prevention_path from subagent JSON}"
# Strict validation: path must not contain "..", symlinks, or escapes (reject before normalization)
if [[ "$PREVENTION_PATH" == *..* ]] || [[ "$PREVENTION_PATH" =~ \$\{ ]]; then
  echo "ERROR: prevention_path contains invalid patterns (.. or variable reference): '${PREVENTION_PATH}'. Learning NOT recorded."
  exit 1
fi

# Trim leading/trailing whitespace from JSON extraction (JSON values may have captured spaces)
PREVENTION_PATH=$(echo "$PREVENTION_PATH" | sed -e 's/^[[:space:]]*//; s/[[:space:]]*$//')

GIT_ROOT=$(git rev-parse --show-toplevel)

# Normalize path: use realpath if file exists, otherwise construct absolute path manually
if [[ -e "$PREVENTION_PATH" ]]; then
  ABSOLUTE_PATH=$(realpath "$PREVENTION_PATH" 2>/dev/null)
  if [[ -z "$ABSOLUTE_PATH" ]]; then
    echo "ERROR: Failed to resolve path: '${PREVENTION_PATH}'. Learning NOT recorded."
    exit 1
  fi
else
  # File does not exist yet; construct absolute path directly (no symlink resolution needed for new files)
  if [[ "$PREVENTION_PATH" == /* ]]; then
    ABSOLUTE_PATH="$PREVENTION_PATH"
  else
    ABSOLUTE_PATH="${GIT_ROOT}/${PREVENTION_PATH}"
  fi
fi

# Verify normalized path is within git root (prevent directory traversal escapes)
# Use substring match with strict prefix to avoid loose matches
if [[ ! "$ABSOLUTE_PATH/" =~ ^"${GIT_ROOT}"/ ]] && [[ "$ABSOLUTE_PATH" != "$GIT_ROOT" ]]; then
  echo "ERROR: prevention_path resolves outside git root: '${ABSOLUTE_PATH}'. Learning NOT recorded."
  exit 1
fi

# Calculate relative path for git diff comparison
RELATIVE_PATH="${ABSOLUTE_PATH#${GIT_ROOT}/}"

# Final validation: reject any relative paths that still contain ".." after normalization
if [[ "$RELATIVE_PATH" == *..* ]] || [[ "$RELATIVE_PATH" == /* ]]; then
  echo "ERROR: prevention_path contains directory traversal: '${RELATIVE_PATH}'. Learning NOT recorded."
  exit 1
fi

# Allow extended character set: alphanumeric, /, ., -, _, @, spaces, parentheses, colons, commas (valid in git paths)
# Reject only control characters and problematic shell metacharacters
if [[ "$RELATIVE_PATH" =~ [^a-zA-Z0-9./_\-@[:space:]\(\):,] ]]; then
  echo "ERROR: prevention_path contains invalid characters: '${RELATIVE_PATH}'. Learning NOT recorded."
  exit 1
fi

CHANGED_FILES=$(git diff-tree --no-commit-id -r --name-only "${COMMIT_HASH}")
if ! echo "$CHANGED_FILES" | grep -qxF "$RELATIVE_PATH"; then
  echo "ERROR: Commit '${COMMIT_HASH}' does not touch '${RELATIVE_PATH}'."
  echo "Changed files in commit: ${CHANGED_FILES}"
  echo "The prevention_commit_hash does not correspond to the claimed prevention_path. Learning NOT recorded."
  exit 1
fi

# ADDITIONAL: Verify the change to prevention_path includes non-comment code changes
# This prevents subagents from claiming credit for documentation-only or comment-only changes
# Extract only the diff content lines (lines prefixed with + or -)
CHANGE_DIFF=$(git show "${COMMIT_HASH}" -- "$RELATIVE_PATH" 2>/dev/null || echo "")
if [[ -z "$CHANGE_DIFF" ]]; then
  echo "ERROR: No diff found for prevention_path '${RELATIVE_PATH}' in commit '${COMMIT_HASH}'."
  echo "The claimed prevention path may have been deleted or the file was not modified in this commit. Learning NOT recorded."
  exit 1
fi

# Count ONLY actual code change lines (added/removed lines that are NOT blank or pure comments)
# Exclude: diff metadata, blank lines, and lines that contain ONLY comments with no code
CODE_CHANGES=$(echo "$CHANGE_DIFF" | grep -E '^[\+\-][^\+\-]' | grep -v '^[[:space:]]*[+\-][[:space:]]*$' | \
  awk 'BEGIN{count=0} /^[\+\-].*[^[:space:]#]/{count++} END{print count}')

if [[ $CODE_CHANGES -eq 0 ]]; then
  echo "ERROR: Commit '${COMMIT_HASH}' contains only whitespace, blank lines, or comment-only changes to '${RELATIVE_PATH}'."
  echo "Prevention must include meaningful code changes (not comments, documentation, or formatting). Learning NOT recorded."
  exit 1
fi
```

### Step 4c: Run record-learning CLI

**MANDATORY:** Steps 4a and 4b MUST complete successfully before proceeding to Step 4c. Do NOT skip validation.
In background mode, when the notification arrives, re-execute Steps 4a and 4b before calling record-learning.
The orchestrator MUST NOT bypass validation based on assumption that "the background task completed, so JSON is valid."

**MANDATORY checkpoint enforcement:** Before invoking record-learning, add an explicit checkpoint that verifies Steps 4a and 4b both completed:
```bash
STEP_4A_COMPLETED=false
STEP_4B_COMPLETED=false
# ... after Step 4a validation passes ...
STEP_4A_COMPLETED=true
# ... after Step 4b validation passes ...
STEP_4B_COMPLETED=true
# Before proceeding to record-learning, verify both completed
if [[ "$STEP_4A_COMPLETED" != "true" ]] || [[ "$STEP_4B_COMPLETED" != "true" ]]; then
  echo "ERROR: Step 4a validation not performed before Step 4c (4a=$STEP_4A_COMPLETED, 4b=$STEP_4B_COMPLETED). Learning NOT recorded."
  exit 1
fi
```
This checkpoint MUST be enforced by the orchestrator code (not just advisory text).

Write the Phase 3 JSON to a temp file using a shell variable — never use a heredoc or single-quoted literal here,
because JSON content may contain any string on a standalone line (including any heredoc delimiter), which would
silently truncate the input. Using a variable protects against both heredoc injection and shell escaping issues.

**IMPORTANT:** The `PHASE3_JSON` variable MUST be assigned using double-quoted syntax (`PHASE3_JSON="..."`) to prevent
word splitting and glob expansion. Never use `eval`, backtick substitution, or unquoted assignment with this value.

```bash
# Store Phase 3 output in a shell variable (JSON from subagent result)
# MUST use double-quoted assignment: PHASE3_JSON="..." — unquoted or eval'd assignment risks injection
PHASE3_JSON="{prevent phase JSON output from subagent}"

# Write Phase 3 output to temp file using a variable to avoid injection
# mktemp generates a unique path per invocation via random suffix (XXXXXX), ensuring
# multi-instance safety even though /tmp is a shared directory.
PHASE3_TMP=$(mktemp /tmp/phase3-output.XXXXXX.json)
printf '%s' "$PHASE3_JSON" > "$PHASE3_TMP"

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
| `phases_executed` array length not exactly 3 (Step 4a) | `ERROR: phases_executed array must contain exactly 3 elements, found {count}. Learning NOT recorded.`, stop |
| `phases_executed` missing required value (Step 4a) | `ERROR: phases_executed must contain ["investigate", "analyze", "prevent"]. Missing: {missing values}. Learning NOT recorded.`, stop |
| `phases_executed` contains duplicates or unrecognized values (Step 4a) | `ERROR: phases_executed contains invalid value(s): {invalid values}. Learning NOT recorded.`, stop |
| Phase summary < 20 chars or is hollow placeholder (Step 4a) | `ERROR: phase_summaries.{phase} must be ≥20 UTF-8 chars and contain meaningful content (not only forbidden words). Found: "{value}". Learning NOT recorded.`, stop |
| Phase object missing required keys (Step 4a) | `ERROR: {phase} object is missing required keys: {missing keys}. Learning NOT recorded.` + list required keys, stop |
| `prevention_implemented=true` but `prevention_commit_hash=null` (Step 4a) | `ERROR: prevention_implemented is true but prevention_commit_hash is null. Learning NOT recorded.` + resolution guidance, stop |
| `prevention_implemented=false` but `prevention_commit_hash` is non-null (Step 4a) | `ERROR: prevention_commit_hash is non-null but prevention_implemented is false. These fields must be in agreement. Learning NOT recorded.`, stop |
| `prevention_implemented=true` but `issue_creation_info` is populated (Step 4a) | `ERROR: prevention_implemented is true but issue_creation_info is populated. These must not both be present. Learning NOT recorded.`, stop |
| Commit hash not valid hex format (Step 4b Check 1) | `ERROR: prevention_commit_hash '{hash}' is not a valid hex hash. Learning NOT recorded.`, stop |
| Commit hash not found in git (Step 4b Check 1) | `ERROR: prevention_commit_hash '{hash}' does not exist in git. Learning NOT recorded.` + verification guidance, stop |
| Commit timestamp not numeric (Step 4b Check 2) | `ERROR: Could not retrieve commit timestamp for '{hash}'. Learning NOT recorded.`, stop |
| Commit predates or equals subagent spawn (Step 4b Check 2) | `ERROR: prevention_commit_hash '{hash}' does not strictly postdate subagent spawn (commit: {time}, spawn: {spawn}). Learning NOT recorded.` + guidance, stop |
| Commit not descendant of HEAD (Step 4b Check 2) | `ERROR: prevention_commit_hash '{hash}' is not a descendant of current HEAD. This may indicate a reused commit from another branch. Learning NOT recorded.`, stop |
| Prevention path contains unsafe characters or invalid patterns (Step 4b Check 3) | `ERROR: prevention_path contains invalid characters, directory traversal, or empty value: '{path}'. Learning NOT recorded.`, stop |
| Prevention path resolves outside git root (Step 4b Check 3) | `ERROR: prevention_path resolves outside git root: '{resolved_path}'. Learning NOT recorded.`, stop |
| Commit does not touch prevention_path (Step 4b Check 3) | `ERROR: Commit '{hash}' does not touch '{path}'. Learning NOT recorded.` + changed files list, stop |
| Diff contains only whitespace/comment changes (Step 4b Check 3) | `ERROR: Commit '{hash}' contains only whitespace, blank lines, or comment-only changes to '{path}'. Prevention must include meaningful code changes. Learning NOT recorded.`, stop |
| Step 4a or 4b validation not performed before Step 4c (Step 4c) | `ERROR: Step 4a or 4b validation not completed (4a={status}, 4b={status}). Learning NOT recorded.` + guidance to re-run steps in sequence, stop |
| `record-learning` exits non-zero | `ERROR: record-learning failed (exit {code}): {output}`, stop |
| Output is not valid JSON | `ERROR: record-learning output is not valid JSON. Learning NOT recorded.` + raw output, stop |

## Step 5: Display Phase Summaries

After Phase 4 completes, parse the results and display each phase summary to the user:

```
Phase: Investigate
{phase_summaries.investigate}

Phase: Analyze
{phase_summaries.analyze}

Phase: Prevent
{phase_summaries.prevent}

Phase: Record
Learning {learning_id} recorded. {counter_status.count}/{counter_status.threshold} mistakes since last retrospective.
```

**Error handling:**

| Condition | Action |
|-----------|--------|
| Subagent returns no JSON | `ERROR: Subagent returned no JSON output. Learning NOT recorded.` + raw output + resolution guidance, stop |
| Phase status is ERROR | `ERROR: Phase '{phase}' reported an error: {error message}. Learning NOT recorded.`, stop |

*Note: JSON validation errors (missing fields, invalid values) are handled in the Step 4 error table above.*

## Step 6: Create Follow-up Issue

**MANDATORY when `prevent.prevention_implemented` is false. Skip this step entirely if
`prevent.prevention_implemented` is true.**

When `prevention_implemented` is false, the subagent could not commit prevention because the current branch is
protected. A follow-up issue must be created so the prevention is not lost.

1. **Validate `issue_creation_info` before proceeding (MANDATORY enforcement).** Verify that:
   - `issue_creation_info` is present and non-empty in the prevent phase output
   - `issue_creation_info.suggested_title` is a non-empty string (must contain at least one non-whitespace character)
   - `issue_creation_info.suggested_description` is a non-empty string (must contain at least one non-whitespace character)
   - `issue_creation_info.suggested_acceptance_criteria` is a non-empty array (must have at least one element)

   Enforce this check with machine-enforced code (not advisory):
   ```bash
   # Extract and validate each required field from prevent.issue_creation_info
   SUGGESTED_TITLE=$(echo "$PREVENT_JSON" | grep -o '"suggested_title"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"suggested_title"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
   SUGGESTED_DESCRIPTION=$(echo "$PREVENT_JSON" | grep -o '"suggested_description"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"suggested_description"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
   SUGGESTED_ACCEPTANCE_CRITERIA=$(echo "$PREVENT_JSON" | grep -o '"suggested_acceptance_criteria"[[:space:]]*:\[^]]*\]' | sed 's/.*"suggested_acceptance_criteria"[[:space:]]*://g')

   # Validate title (non-empty string)
   if [[ -z "$SUGGESTED_TITLE" ]]; then
     echo "ERROR: issue_creation_info.suggested_title is empty or missing"
     exit 1
   fi

   # Validate description (non-empty string)
   if [[ -z "$SUGGESTED_DESCRIPTION" ]]; then
     echo "ERROR: issue_creation_info.suggested_description is empty or missing"
     exit 1
   fi

   # Validate acceptance criteria is a JSON array with at least one element
   # Array must start with [ and end with ], and contain at least one quoted string
   if ! echo "$SUGGESTED_ACCEPTANCE_CRITERIA" | grep -q '^\[.*".*".*\]$'; then
     echo "ERROR: issue_creation_info.suggested_acceptance_criteria is not a valid JSON array with at least one element"
     exit 1
   fi
   ```

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

**Full vocabulary, canonical examples, and selection process:** See `rca-method.md § Cause Signature Vocabulary`.

## Examples

**For context analysis examples:** Read `examples.md` for context-related, non-context, and ambiguous cases.

## Anti-Patterns

**For common mistakes when using this skill:** Read `anti-patterns.md`.

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
