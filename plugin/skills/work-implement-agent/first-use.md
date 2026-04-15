<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Implement

> See `${CLAUDE_PLUGIN_ROOT}/concepts/work-decomposition.md` for the full execution model and parallelism rules.

Implement phase for `/cat:work-agent`. Displays preparing/implementing banners, verifies lock ownership,
and orchestrates subagent execution of the implementation plan.

## Arguments Format

```
<cat_agent_id> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <caution>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | cat_agent_id | agent ID passed through from parent |
| 2 | issue_id | `2.1-issue-name` |
| 3 | issue_path | `${WORKTREE_PATH}/.cat/issues/v2/v2.1/issue-name` |
| 4 | worktree_path | `${HOME}/.cat/worktrees/2.1-issue-name` |
| 5 | issue_branch | `2.1-issue-name` |
| 6 | target_branch | `v2.1` |
| 7 | estimated_tokens | `45000` |
| 8 | trust | `medium` |
| 9 | caution | `changed` |

## Output Contract

Return JSON when complete:

```json
{
  "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
  "jobs_count": 1,
  "commits": [
    {"hash": "abc123", "message": "feature: description", "type": "feature"}
  ],
  "files_changed": 5,
  "tokens_used": 12000,
  "compaction_events": 0
}
```

`jobs_count` MUST be the integer value printed by the canonical detection command (`echo "JOBS_COUNT=${JOBS_COUNT}"`).
It may NOT be derived by reading plan.md into context and counting `### Job ` occurrences mentally or via any method
other than running that Bash command. If the command was not run (e.g., because plan.md was already in context),
run it now before returning.

**Relay prohibition:** `jobs_count` (even when correctly grep-derived) MUST NOT be embedded into any subagent
prompt text. Do NOT write "there are N jobs" or "JOBS_COUNT=N" or any equivalent into a subagent prompt.
Each subagent determines its own job count by reading `PLAN_MD_PATH` directly.

## Configuration

Extract configuration from the positional ARGUMENTS string. The arguments are space-separated.
Since some paths may theoretically contain spaces but in practice CAT paths never do,
split on whitespace. Also display the preparing banner in a chained call:

```bash
# Parse positional arguments, set PLAN_MD path, and display preparing banner
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ESTIMATED_TOKENS TRUST CAUTION <<< "$ARGUMENTS" && \
PLAN_MD="${ISSUE_PATH}/plan.md" && \
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" "${ISSUE_ID}" --phase preparing
```

## Step 1: Display Preparing Banner

Capture the exit code and stdout separately:

```bash
mkdir -p .cat/work/tmp
BANNER_STDERR_FILE=$(mktemp -p .cat/work/tmp)
BANNER_OUT=$("${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" "${ISSUE_ID}" --phase preparing 2>"${BANNER_STDERR_FILE}")
BANNER_EXIT=$?
```

**If the binary exited non-zero**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'preparing' (exit code <BANNER_EXIT>).
stderr: <contents of ${BANNER_STDERR_FILE}>
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```

**If the binary exited 0 but stdout appears whitespace-only**, proceed — assume the banner uses
invisible or terminal-control rendering that is not captured in this context. Do NOT treat a
whitespace-only stdout at exit 0 as a failure or use it as grounds to abort.

Do NOT skip the banner step itself; always run the binary.

```bash
rm -f "${BANNER_STDERR_FILE}"
```

This indicates Phase 1 (prepare) has completed and work phases are starting.

## Step 2: Verify Lock Ownership

**Before any execution, verify the lock for this issue belongs to the current session.**

```bash
source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"
LOCK_FILE="${LOCKS_DIR}/${ISSUE_ID}.lock"

if [[ -z "${CLAUDE_SESSION_ID:-}" ]]; then
  echo "ERROR: CLAUDE_SESSION_ID environment variable is not set"
  exit 1
fi

if [[ ! -f "$LOCK_FILE" ]]; then
  echo "ERROR: No lock file found for ${ISSUE_ID}. Issue was not properly prepared."
  exit 1
fi

# Extract session_id value from the lock JSON using grep/sed (no jq available)
LOCK_SESSION=$(grep -o '"session_id"[[:space:]]*:[[:space:]]*"[^"]*"' "$LOCK_FILE" | \
  head -1 | sed 's/"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

if [[ "$LOCK_SESSION" == "$CLAUDE_SESSION_ID" ]]; then
  echo "OK: Lock verified for current session"
else
  echo "ERROR: Lock for ${ISSUE_ID} belongs to session ${LOCK_SESSION}, not ${CLAUDE_SESSION_ID}"
  exit 1
fi
```

If lock ownership verification fails, STOP immediately and return FAILED status. Do NOT proceed
to execution — another session owns this issue.

## Step 3: Implement Phase

Display the **Implementing phase** banner by running:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" "${ISSUE_ID}" --phase implementing
```

Capture the exit code and stdout separately:

```bash
mkdir -p .cat/work/tmp
BANNER_STDERR_FILE=$(mktemp -p .cat/work/tmp)
BANNER_OUT=$("${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" "${ISSUE_ID}" --phase implementing 2>"${BANNER_STDERR_FILE}")
BANNER_EXIT=$?
```

**If the binary exited non-zero**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'implementing' (exit code <BANNER_EXIT>).
stderr: <contents of ${BANNER_STDERR_FILE}>
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```

**If the binary exited 0 but stdout appears whitespace-only**, proceed — assume the banner uses
invisible or terminal-control rendering that is not captured in this context. Do NOT treat a
whitespace-only stdout at exit 0 as a failure or use it as grounds to abort.

Do NOT skip the banner step itself; always run the binary.

```bash
rm -f "${BANNER_STDERR_FILE}"
```

## Step 4: Pre-Implementation Approval Gate (trust=low only)

If `TRUST != "low"`, skip this step entirely and proceed to Step 5.

Read the Goal and Post-conditions from plan.md:

```bash
ISSUE_GOAL=$(sed -n '/^## Goal/{n;:loop;/^## /b;p;n;b loop}' "${PLAN_MD}" | sed '/^[[:space:]]*$/d' | head -20)
POST_CONDITIONS=$(sed -n '/^## Post-conditions/{n;:loop;/^## /b;p;n;b loop}' "${PLAN_MD}" | sed '/^[[:space:]]*$/d' | head -30)
```

If either `ISSUE_GOAL` or `POST_CONDITIONS` is empty, STOP immediately:
```
ERROR: plan.md is missing required sections (Goal or Post-conditions).
Cannot present pre-implementation gate without these sections.
Fix plan.md and retry /cat:work-agent.
```

Present the pre-implementation review gate:

```
AskUserQuestion:
  header: "${ISSUE_ID} — Pre-Implementation Review"
  question: |
    **Goal:**
    ${ISSUE_GOAL}

    **Post-conditions:**
    ${POST_CONDITIONS}

    **Estimated token cost:** ${ESTIMATED_TOKENS}

    Approve to start implementation, or request changes to the plan first.
  options:
    - "Approve and start"
    - "Request changes"
    - "Abort"
```

Gate result handling:

- **"Approve and start"**: proceed to Step 5.
- **"Request changes"**: release lock:
  ```bash
  "${CLAUDE_PLUGIN_ROOT}/client/bin/issue-lock" release "${ISSUE_ID}" "${CLAUDE_SESSION_ID}"
  ```
  Return:
  ```json
  {"status": "BLOCKED", "message": "User requested changes to the plan before implementation. Edit plan.md and re-invoke /cat:work-agent."}
  ```
- **"Abort"**: release lock (same command as above). Return:
  ```json
  {"status": "BLOCKED", "message": "User aborted before implementation started."}
  ```
- **Gate rejected (empty or non-matching answer)**: Re-present the full gate. Max 3 attempts. If still
  not answered after 3 attempts, treat as "Abort".

## Step 5: Generate Implementation Steps

Before reading Main Agent Jobs, check whether plan.md already contains implementation steps:

```bash
PLAN_MD="${ISSUE_PATH}/plan.md" && \
grep -qE '^## (Jobs|Execution Steps)' "${PLAN_MD}" && \
echo "hasSteps=true" || echo "hasSteps=false"
```

**If `hasSteps=false`** (lightweight plan created by `/cat:add-agent`): invoke `cat:plan-builder-agent` in revise mode to
generate full implementation steps before spawning the implementation subagent:

1. Read CURIOSITY from config:

```bash
CONFIG=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective)
if [[ $? -ne 0 ]]; then
    echo "ERROR: Failed to read effective config" >&2
    exit 1
fi
CURIOSITY=$(echo "$CONFIG" | grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

2. Invoke plan-builder-agent to add implementation steps:

```
Skill tool:
  skill: "cat:plan-builder-agent"
  args: "${CAT_AGENT_ID} ${CURIOSITY} revise ${ISSUE_PATH} Generate full implementation steps for this lightweight
plan. Add Jobs or Execution Steps section with detailed step-by-step implementation guidance."
```

3. After plan-builder-agent returns, commit the updated plan.md:

```bash
cd "${WORKTREE_PATH}" && git add "${ISSUE_PATH}/plan.md" && git commit -m "planning: generate implementation steps for ${ISSUE_ID}"
```

4. Re-read the updated plan.md in subsequent steps.

**If `hasSteps=true`** (full plan with implementation steps): skip plan-builder-agent invocation. Proceed directly
to "Read plan.md and Invoke Main Agent Jobs".

### Read plan.md and Invoke Main Agent Jobs

Read the `## Main Agent Jobs` section from plan.md:

```bash
PLAN_MD="${ISSUE_PATH}/plan.md" && MAIN_AGENT_JOBS=$(sed -n '/^## Main Agent Jobs/,/^## /p' "$PLAN_MD" | head -n -1)
```

**If `## Main Agent Jobs` is present and non-empty:** extract each bullet item
(`- /cat:skill-name args`) and invoke the corresponding skill NOW at the main agent level
using the Skill tool.

Example: If `## Main Agent Jobs` contains `- /cat:example-skill path/to/file.md`, then:

```
Skill tool:
  skill: "cat:example-skill-agent"
  args: "path/to/file.md"
```

**Complete each skill fully before delegation.** Pre-invoked skills may have built-in
iteration loops, validation gates, or multi-step workflows. Run each skill to its documented
completion state before passing results to the implementation subagent. Do NOT pass intermediate
or failed results to the subagent for manual fixing — that bypasses the skill's quality gates.

Capture the output from these skills - the implementation subagent will need the results.

**Pre-Invoked Skill Results content restriction:** When including pre-invoked skill output in the
subagent prompt, include only the direct functional output (e.g., transformed file contents, metrics,
validation results). Do NOT include any skill output that describes, summarizes, quotes, or paraphrases
plan.md content — even if that description originates from the skill rather than from the agent directly.
A skill that reads plan.md and echoes its goal or steps back as part of its output does not make that
content eligible for relay. If skill output cannot be cleanly separated from plan.md paraphrase,
omit the plan.md-describing portion and include only the non-plan.md functional results.

**plan.md paraphrase detection:** Any text in skill output that restates, summarizes, or quotes
the issue goal, step names, acceptance criteria, or job structure from plan.md is plan.md paraphrase,
regardless of whether the text was produced by the skill or copied from plan.md by the skill. The
origin of the text (skill vs. agent) is irrelevant — only the content matters. When in doubt, omit.
Permitted metric examples: "Reduced file size from 5000 to 3000 tokens", "Validation passed: 0 errors".
Prohibited metric examples: "Compressed plan.md. Goal: add user authentication" — the goal text is
plan.md paraphrase embedded in a metric and must be stripped before inclusion.

### Detect Parallel Execution Jobs

**CRITICAL: `JOBS_COUNT` MUST be derived exclusively from the Bash canonical command output below.
It MUST NOT be derived from in-context plan.md content, mental counting, or any other method —
even if plan.md has already been read into context for other purposes. Reading plan.md into context
does not make its content a permitted source for the job count. The canonical Bash command is the
only permitted source.**

Read plan.md directly to count `### Job N` subsections using the canonical command:

```bash
JOBS_COUNT=$(grep -c '^### Job ' "$PLAN_MD") && echo "JOBS_COUNT=${JOBS_COUNT}"
```

Use this exact command — do NOT substitute alternative counting logic or Bash constructs. Any other method is
prohibited. The `echo` is part of the same command; do not split detection and printing into separate Bash
calls. No further Bash calls are permitted after this step before the prepare phase begins.

**If jobs are empty or only one job is present (`JOBS_COUNT` is 0 or 1):** proceed to single-subagent execution
(see below). Parse execution items from `## Jobs` / `### Job 1`.

**If two or more jobs are present (`JOBS_COUNT` >= 2):** use parallel execution (see Parallel Subagent Execution
below). The last job for index.json ownership is `### Job ${JOBS_COUNT}` (the highest numbered job).

### Mid-Work plan.md Revision

If requirements change, read `CURIOSITY` from config:

```bash
CONFIG=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective)
if [[ $? -ne 0 ]]; then
    echo "ERROR: Failed to read effective config" >&2
    exit 1
fi
CURIOSITY=$(echo "$CONFIG" | grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
```

Then invoke:
`Skill("cat:plan-builder-agent", "${CAT_AGENT_ID} ${CURIOSITY} revise ${ISSUE_PATH} <description of what changed>")`

After revision, re-read the updated plan.md and adjust remaining execution accordingly.

### Delegation Prompt Construction

**Subagents read plan.md directly — do NOT relay its content into prompts.**

Pass `PLAN_MD_PATH` so the subagent can read the Goal and Jobs/Steps sections itself.
Do NOT extract and paste those sections into the prompt — that is the content relay anti-pattern.

**Why:** Subagents that receive a `PLAN_MD_PATH` and read plan.md themselves always see the authoritative
content, preserving plan.md's structure exactly (distinct steps remain distinct, no re-summarization).
Pasting content into prompts creates a stale copy that can diverge, wastes tokens, and risks
interpretive distortion.

**Pattern:**
- ✅ Pass `PLAN_MD_PATH: ${PLAN_MD}` and instruct the subagent to read Goal and Execution sections itself
- ✅ Trust plan.md structure — subagents read it directly, no relay needed
- ❌ Do NOT inline `${ISSUE_GOAL}` or Jobs content into the prompt
- ❌ Do NOT add interpretive summaries or aggregate instructions that restate plan.md differently

### Commit-Before-Spawn Requirement

**BLOCKING:** Before spawning ANY implementation subagent (single or parallel), commit all pending changes in
the worktree. This is enforced by the `EnforceCommitBeforeSubagentSpawn` hook, which blocks Task spawning of
`cat:work-execute` when the worktree is dirty.

**Why:** Each subagent is spawned with `isolation: "worktree"`, creating a separate git worktree branched from
the current HEAD of the issue branch. Uncommitted changes in the main agent's worktree are NOT visible in the
subagent's worktree. All changes must be committed before spawning so the subagent sees the complete state.

```bash
cd "${WORKTREE_PATH}" && git status --porcelain  # Must be empty before spawning
```

**CRITICAL: index.json must NOT be committed here.** Before committing, run:

```bash
cd "${WORKTREE_PATH}" && git status --porcelain | awk '{print $NF}' | \
  while IFS= read -r filepath; do
    basename=$(basename "$filepath")
    if echo "$basename" | grep -Eqi '^index\.json$'; then
      echo "BLOCKED: dirty planning file detected: $filepath"
      exit 1
    fi
  done
```

If this check prints any `BLOCKED:` line, STOP immediately and return FAILED status.
Do NOT stage, commit, or otherwise alter index.json before spawning.

Note: `-E` (extended regex) is required so that `|` acts as alternation, not a literal pipe
character. Without `-E`, the pattern would only match a filename literally containing a pipe.

If the worktree is dirty with other files (non-index.json), stage only those specific files by
**explicitly listing each file path** and commit:

```bash
cd "${WORKTREE_PATH}" && git add <explicit-file-path-1> <explicit-file-path-2> ... && git commit -m "planning: update before delegation"
```

**Prohibited staging commands** — the following forms MUST NOT be used, as they may accidentally stage
plan.md or index.json:
- `git add -A`
- `git add .`
- `git add -u`
- `git add --update`
- `git add --all`
- Any glob or wildcard pattern (e.g., `git add *.java`, `git add src/`)

Each file to be staged must be named explicitly by its full path relative to the worktree root.

### Schema Migration Coverage Check

After all pending changes are committed and before spawning any subagent, run a schema migration coverage check.
This check warns when a schema-relevant file was modified in the current commit set but other unmodified source files
still contain its filename as a literal string — indicating those files may also need updating (e.g., a skill that
hardcodes a filename that was renamed, or a migration script referencing an old path).

A file is schema-relevant if its basename matches any of the following names (case-insensitive):
`index.json` or `plan.md`.

**What "references" means:** a file references a schema-relevant basename when that exact string appears as a literal
substring in the file's content (e.g., `grep -F "plan.md"` matches). Files that merely happen to be named similarly
are not counted — only files whose content contains the basename string.

**Scope:** The search covers `*.md`, `*.sh`, and `*.json` files under the worktree, excluding:
- `.git/` — git internals
- `.cat/` — issue tracking, config, and runtime data (not source files)
- `target/` — build artifacts

```bash
{
  CHANGED_SCHEMA_FILES=$(git -C "${WORKTREE_PATH}" diff --name-status "${TARGET_BRANCH}..HEAD" | \
    awk '{print $NF}' | while IFS= read -r f; do
      bn=$(basename "$f" | tr '[:upper:]' '[:lower:]')
      case "$bn" in
        index.json|plan.md) echo "$f" ;;
      esac
    done)

  if [[ -n "$CHANGED_SCHEMA_FILES" ]]; then
    CHANGED_FILES_LIST=$(git -C "${WORKTREE_PATH}" diff --name-only "${TARGET_BRANCH}..HEAD")

    while IFS= read -r schema_file; do
      [[ -z "$schema_file" ]] && continue
      schema_basename=$(basename "$schema_file")

      REFS=$(grep -r --include="*.md" --include="*.sh" --include="*.json" \
        -lF "$schema_basename" "${WORKTREE_PATH}" 2>/dev/null | \
        grep -v "/.git/" | \
        grep -v "/.cat/" | \
        grep -v "/target/" | \
        while IFS= read -r ref_file; do
          rel_ref="${ref_file#${WORKTREE_PATH}/}"
          if ! echo "$CHANGED_FILES_LIST" | grep -qxF "$rel_ref"; then
            echo "$ref_file"
          fi
        done)

      if [[ -n "$REFS" ]]; then
        echo "WARNING: Schema migration coverage — '${schema_basename}' was modified but the following" \
          "unmodified files still reference it and may need updating:"
        while IFS= read -r ref_file; do
          [[ -z "$ref_file" ]] && continue
          echo "  ${ref_file#${WORKTREE_PATH}/}"
          grep -nF "$schema_basename" "$ref_file" 2>/dev/null | sed 's/^/    /'
        done <<< "$REFS"
        echo "  Review each file above to determine whether it needs updating."
      fi
    done <<< "$CHANGED_SCHEMA_FILES"
  fi
} || true
```

This check is advisory only — it WARNS but does not BLOCK. Continue to subagent delegation regardless of output.

### Single-Subagent Execution (no groups or only one group)

Spawn a subagent to implement the issue:

```
Task tool:
  description: "Execute: implement ${ISSUE_ID}"
  subagent_type: "cat:work-execute"
  isolation: "worktree"
  prompt: |
    Execute the implementation for issue ${ISSUE_ID}.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}
    PLAN_MD_PATH: ${PLAN_MD}

    Read the Goal section and Jobs (or Execution Steps) from PLAN_MD_PATH directly.
    Do NOT ask the main agent to provide this content — it is authoritative in plan.md.

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## Critical Requirements
    - You are working in an isolated worktree. Your changes will be merged back to the issue branch.
    - Follow execution steps from plan.md EXACTLY
    - If steps say to invoke a skill that was pre-invoked above, use the provided results
    - Update index.json in the SAME commit as implementation (status: closed, progress: 100%)
    - Run tests if applicable
    - **Honest test result reporting:** If empirical test execution (e.g., via `cat:empirical-test-agent`)
      cannot be completed, you MUST explicitly report the failure with the specific reason rather than
      fabricating output values. Acceptable failure reasons: runtime unavailable (e.g., Java not on PATH,
      missing dependency), test framework error (e.g., TestNG configuration failure, missing test class),
      config incompatibility (e.g., unsupported OS, missing environment variable), or any other concrete
      blocker. Never invent pass/fail counts, scores, or compliance verdicts — fabricated results create
      false confidence and prevent detection of real compliance issues.
    - Commit your changes using the commit type from plan.md (e.g., `feature:`, `bugfix:`, `docs:`). The commit message must follow the format: `<type>: <descriptive summary>`. Example: `feature: add user authentication with JWT tokens`. Do NOT use generic messages like 'squash commit' or 'fix'.

    ## Return Format
    Return JSON when complete:
    ```json
    {
      "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
      "tokens_used": <actual>,
      "percent_of_context": <actual>,
      "compaction_events": 0,
      "commits": [
        {"hash": "abc123", "message": "feature: description", "type": "feature"}
      ],
      "files_changed": <actual>,
      "issue_metrics": {},
      "discovered_issues": [],
      "verification": {
        "build_passed": true,
        "tests_passed": true,
        "test_count": 15
      }
    }
    ```

    If you encounter a blocker, return:
    ```json
    {
      "status": "BLOCKED",
      "message": "Description of blocker",
      "blocker": "What needs to be resolved"
    }
    ```

    CRITICAL: You are the implementation agent - implement directly, do NOT spawn another subagent.
```

Immediately after the subagent returns its Task tool result, invoke `cat:collect-results-agent`.
The `EnforceCollectAfterAgent` hook blocks all Skill and Task calls until collect-results-agent is invoked.

```
Skill tool:
  skill: "cat:collect-results-agent"
  args: "${CAT_AGENT_ID}/subagents/${SUBAGENT_RAW_ID} ${ISSUE_PATH} <subagentCommitsJsonPath>"
```

Where `SUBAGENT_RAW_ID` is the `agentId:` value from the Task tool result footer, and
`subagentCommitsJsonPath` is a temp file path to write the collected commits JSON.

**SUBAGENT_RAW_ID validation:** Before constructing the compound ID, verify that `SUBAGENT_RAW_ID` is
non-empty and contains only alphanumeric characters, hyphens, and underscores (no slashes, dots, or
path traversal sequences). If validation fails, STOP and return FAILED status — do NOT pass an empty
or malformed ID to collect-results-agent.

The cat_agent_id format for a subagent is `{session_id}/subagents/{agent_id}` — do NOT omit the
`/subagents/` segment or the call will be rejected with a format validation error.

Then validate and merge its commits back into the issue branch:

```bash
SUBAGENT_BRANCH="<branch name from Task tool result metadata>"

# Validate BRANCH is non-empty before using it in prefix checks
if [[ -z "${BRANCH}" ]]; then
  echo "ERROR: BRANCH variable is empty — cannot validate subagent branch prefix."
  exit 1
fi

# Validate branch name: alphanumeric, hyphens, underscores only — no slashes, dots, or path separators
if [[ ! "${SUBAGENT_BRANCH}" =~ ^[a-zA-Z0-9_-]+$ ]]; then
  echo "ERROR: Subagent branch name contains invalid characters: ${SUBAGENT_BRANCH}"
  exit 1
fi

# Validate prefix: must start with BRANCH followed by exactly a hyphen and at least one more character
if [[ ! "${SUBAGENT_BRANCH}" =~ ^[a-zA-Z0-9_-]+-[a-zA-Z0-9_-]+$ ]] || \
   [[ "${SUBAGENT_BRANCH}" != "${BRANCH}-"* ]]; then
  echo "ERROR: Subagent branch ${SUBAGENT_BRANCH} does not have expected prefix ${BRANCH}-"
  exit 1
fi

# Verify the branch actually exists in git before merging
if ! git -C "${WORKTREE_PATH}" rev-parse --verify "refs/heads/${SUBAGENT_BRANCH}" >/dev/null 2>&1; then
  echo "ERROR: Subagent branch ${SUBAGENT_BRANCH} does not exist in git. Cannot merge."
  exit 1
fi

cd "${WORKTREE_PATH}" && git merge --ff-only "${SUBAGENT_BRANCH}"
if [[ $? -ne 0 ]]; then
  echo "ERROR: Fast-forward merge of ${SUBAGENT_BRANCH} failed. The subagent branch has diverged."
  echo "Use /cat:git-merge-linear-agent to resolve the diverged history."
  exit 1
fi
```

The subagent branch name and worktree path are returned in the Task tool result when `isolation: "worktree"` is
used. Use that branch name in the merge command above.

### Parallel Subagent Execution (two or more jobs)

When plan.md contains two or more jobs, spawn one subagent per job simultaneously.
Each subagent is spawned with `isolation: "worktree"` — it gets its own isolated git worktree branched from
the issue branch HEAD. Subagents execute concurrently without shared disk state. The last job's subagent
updates index.json; other jobs skip it.

**IMPORTANT:** Each parallel subagent commits to its own isolated worktree branch. After all subagents
complete, the main agent merges each subagent branch back into the issue branch in ascending job order
(Job 1 first, then Job 2, etc.).
Only the last job subagent updates index.json.

**CRITICAL: Parallel means one API response — not "start Job 1, then start Job 2".**
When `JOBS_COUNT` >= 2, spawn ALL job subagents in a SINGLE assistant API response by making multiple
Task tool calls in that same response. An "API response" is one assistant message turn: everything between
receiving the user/tool input and sending back the next assistant message. Do NOT spawn Job 1, await its
result, then spawn Job 2 in a separate API response — that is sequential execution masquerading as parallel.

**Violation test (observable and unambiguous):** If you issued a Task call and received any tool result
before all Task calls were issued, you violated the rule — regardless of intent or turn-boundary awareness.

**No tool calls are permitted between Task spawns.** After issuing the first Task call for Job 1, the very
next tool call in that same assistant message must be the Task call for Job 2 (and Job 3, if present, and
so on). No Bash, Read, Grep, Write, Skill, or any other tool call may appear between the Task calls.
Safety checks, verification reads, status queries, and skill invocations between Task spawns are all
prohibited.

**All job prompts are constructable from plan.md alone.** plan.md is the single authoritative source for
job contents. Each job prompt contains only: the issue configuration variables (already known before
reading plan.md), the `PLAN_MD_PATH` reference, and the `ASSIGNED_JOB` number. No job prompt requires
output from another job in order to be constructed. Therefore, all prompts can and must be fully drafted
before any Task call is issued.

**`JOBS_COUNT` must NOT appear in any subagent prompt.** Do not embed the numeric job count (e.g.,
"JOBS_COUNT=3", "there are 3 jobs", or any equivalent phrasing) into a subagent prompt. Including it
would relay structural plan.md metadata, which is prohibited. Each subagent reads `PLAN_MD_PATH` directly
and determines job structure itself.

**Prepare ALL prompts before issuing ANY tool call in the spawn phase.**

Two-phase execution:

1. **Prepare phase (written in response text, no tool calls):** Write out every job prompt in the
   assistant response text, derived from already-in-context variables and plan.md content. The prepare
   phase text must include the complete text of each job's prompt — it is not complete without the actual
   prompts. This phase is observable: the prompts appear as text in the response before any Task call.
   Do NOT issue any tool call (Bash, Read, Grep, Write, Task, Skill, or any other) during this phase.

   **Permitted pre-prepare tool calls (strictly bounded):** Before entering the prepare phase, you may
   issue tool calls only to satisfy two concrete prerequisites: (a) read plan.md into context if it is
   not already there, and (b) run the canonical `JOBS_COUNT` detection command. No other tool
   calls are permitted before the prepare phase. Once both prerequisites are satisfied, the very next
   assistant action must be the prepare phase text (containing the full job prompts) followed immediately
   by the spawn phase.

   **`JOBS_COUNT` routing is determined by the canonical Bash command result only.** Even after reading
   plan.md into context, the agent MUST NOT use the in-context plan.md content to decide whether to use
   single-subagent or parallel execution. The routing decision (single vs. parallel) MUST wait for and
   use the integer value printed by the canonical detection command. If the canonical command has not yet
   been run, run it before making any routing decision.

   **BLOCKING GATE — Write a job spawn checklist before issuing the first Task call.** After writing all
   job prompts in the response text, write this checklist as plain prose (not in a code block):

   Job spawn checklist (JOBS_COUNT = N):
   - Job 1 prompt: written above ✓
   - Job 2 prompt: written above ✓
   ... (one entry per job, from 1 to JOBS_COUNT)
   All N job prompts written. Spawning all N Task calls now.

   Every job from 1 to JOBS_COUNT must have a corresponding checklist entry. If any job prompt is
   missing from the response text above the checklist, write it before completing the entry. Only after
   the checklist is complete may the spawn phase begin. This gate makes it structurally visible when a
   job prompt was omitted — the gap appears in the checklist before any Task call is issued.

2. **Spawn phase (one API response, all Task calls together, nothing else):** Issue ALL Task tool calls
   simultaneously in a single assistant message, immediately following the prepare-phase text and the
   job spawn checklist. The spawn phase contains ONLY Task tool calls — no other tool calls before,
   between, or after the Task calls within this phase.

Correct pattern (one message, two Task calls, nothing between them):

```
Task tool call: Job 1 subagent
Task tool call: Job 2 subagent
```

For each job (example for Job 1 with steps 1, 2, 3):

```
Task tool:
  description: "Execute: implement ${ISSUE_ID} job 1 (steps 1, 2, 3)"
  subagent_type: "cat:work-execute"
  isolation: "worktree"
  prompt: |
    Execute the implementation for issue ${ISSUE_ID}, job 1 only.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}
    PLAN_MD_PATH: ${PLAN_MD}
    ASSIGNED_JOB: 1

    Read the Goal section from PLAN_MD_PATH. Then read ONLY the `### Job 1` section from
    PLAN_MD_PATH for your execution items. Do NOT read or execute items from other job sections.
    Do NOT ask the main agent to provide this content — it is authoritative in plan.md.

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## Critical Requirements
    - You are working in an isolated worktree. Your changes will be merged back to the issue branch.
    - Execute ONLY the items assigned to your job (ASSIGNED_JOB above, read from plan.md)
    - Do NOT execute items from other jobs
    - **index.json ownership:** You are [DETERMINED AUTOMATICALLY: if job is the last one, "the index.json owner"
      else "NOT the index.json owner"]. [If owner: "Update index.json in your final commit: status: closed,
      progress: 100%." Else: "Do NOT modify index.json in any commit."]
    - Run tests if applicable
    - **Honest test result reporting:** If empirical test execution (e.g., via `cat:empirical-test-agent`)
      cannot be completed, you MUST explicitly report the failure with the specific reason rather than
      fabricating output values. Acceptable failure reasons: runtime unavailable (e.g., Java not on PATH,
      missing dependency), test framework error (e.g., TestNG configuration failure, missing test class),
      config incompatibility (e.g., unsupported OS, missing environment variable), or any other concrete
      blocker. Never invent pass/fail counts, scores, or compliance verdicts — fabricated results create
      false confidence and prevent detection of real compliance issues.
    - Commit your changes using the commit type from plan.md (e.g., `feature:`, `bugfix:`, `docs:`). The commit
      message must follow the format: `<type>: <descriptive summary>`. Do NOT use generic messages.

    ## Return Format
    Return JSON when complete:
    ```json
    {
      "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
      "job": 1,
      "tokens_used": <actual>,
      "percent_of_context": <actual>,
      "compaction_events": 0,
      "commits": [
        {"hash": "abc123", "message": "feature: description", "type": "feature"}
      ],
      "files_changed": <actual>,
      "issue_metrics": {},
      "discovered_issues": [],
      "verification": {
        "build_passed": true,
        "tests_passed": true,
        "test_count": 15
      }
    }
    ```

    The `"job"` field MUST equal the numeric `ASSIGNED_JOB` value from the Issue Configuration above.
    Do NOT use a letter-based group identifier — use the integer job number.

    If you encounter a blocker, return:
    ```json
    {
      "status": "BLOCKED",
      "job": 1,
      "message": "Description of blocker",
      "blocker": "What needs to be resolved"
    }
    ```

    CRITICAL: You are the implementation agent - implement directly, do NOT spawn another subagent.
```

**Wait for ALL job subagents to complete before invoking collect-results-agent for ANY of them.**
The `EnforceCollectAfterAgent` hook blocks all Skill and Task calls until collect-results-agent is invoked
for each completed Agent tool result.

**CRITICAL: Do NOT call collect-results-agent for Job N until EVERY job from 1 to JOBS_COUNT has returned
a Task tool result.** Calling collect-results for a subset of jobs while others are still running is a
protocol violation — it leaves the implementation in a partial state.

**Parallel job completion protocol — wait for ALL jobs before calling collect-results-agent for ANY:**
1. Issue all N Task calls simultaneously (spawn phase, single API response)
2. Receive Task tool results — each result arrives as it completes
3. Track which jobs have returned: a job is complete only when its Task tool result appears in context
4. **WAIT** — do not act on any result until ALL N jobs have returned Task tool results
5. When ALL N jobs have returned Task tool results, initialize a counter: `NEXT_COLLECT=1`
6. Call collect-results-agent for job `NEXT_COLLECT`. Before each call, verify `NEXT_COLLECT` equals
   the expected job number. After the call succeeds, increment: `NEXT_COLLECT=$((NEXT_COLLECT + 1))`.
   Repeat until `NEXT_COLLECT > JOBS_COUNT`. Do NOT skip ahead or reorder — each collect-results call
   MUST process jobs in strict ascending order (1, 2, 3, ..., N).
7. After all collect-results-agent calls complete, initialize a counter: `NEXT_MERGE=1`. Merge each
   subagent branch in strict ascending order: merge job `NEXT_MERGE`, then increment
   `NEXT_MERGE=$((NEXT_MERGE + 1))`. Repeat until `NEXT_MERGE > JOBS_COUNT`. Do NOT merge out of order.

NEVER proceed to collect-results after only a subset of jobs have returned.

**Why ascending order matters:** Merging and collecting results in ascending job order ensures metrics
(tokens_used, files_changed, compaction_events) are aggregated consistently across runs, making output
reproducible and comparable regardless of which job happened to complete first.

**Handling large output / ambiguous completion:** If a job's Task output is truncated or too large to read
directly, the job is still complete when its Task tool result appears — the result size does not affect
completeness. Do NOT attempt to read the output file directly or use TaskOutput on Agent tasks. Wait for the
system notification of the Task tool result, then proceed.

**Truncated metadata recovery:** The `agentId` and branch name are essential for collect-results and merge
steps. If truncation removes these fields from the visible Task tool result, do NOT fabricate or guess
values. Instead, use `TaskGet` with the task ID to retrieve the full result metadata including the `agentId`
and branch name. If `TaskGet` also fails to provide the metadata, STOP and return FAILED status with message
"Unable to retrieve agentId or branch name for job N after truncation".

For each completed subagent, call collect-results-agent:

```
Skill tool:
  skill: "cat:collect-results-agent"
  args: "${CAT_AGENT_ID}/subagents/${SUBAGENT_RAW_ID} ${ISSUE_PATH} <subagentCommitsJsonPath>"
```

Where `SUBAGENT_RAW_ID` is the `agentId:` value from that job's Task tool result footer.
Apply the same SUBAGENT_RAW_ID validation as described in the single-subagent section: verify it is
non-empty and contains only alphanumeric characters, hyphens, and underscores before constructing
the compound ID. If validation fails, STOP and return FAILED status.

The cat_agent_id format for a subagent is `{session_id}/subagents/{agent_id}` — do NOT omit the
`/subagents/` segment or the call will be rejected with a format validation error.

Then validate and merge each subagent branch back into the issue branch using the `NEXT_MERGE` counter
from the parallel job completion protocol (step 7). Process job `NEXT_MERGE`, then increment. Do NOT
merge any job out of ascending order.

For each job's branch name received from the Task tool result, validate before merging:

```bash
SUBAGENT_BRANCH="<branch name from Task tool result metadata for this job>"

# Validate BRANCH is non-empty before using it in prefix checks
if [[ -z "${BRANCH}" ]]; then
  echo "ERROR: BRANCH variable is empty — cannot validate subagent branch prefix."
  exit 1
fi

# Validate branch name: alphanumeric, hyphens, underscores only — no slashes, dots, or path separators
if [[ ! "${SUBAGENT_BRANCH}" =~ ^[a-zA-Z0-9_-]+$ ]]; then
  echo "ERROR: Subagent branch name contains invalid characters: ${SUBAGENT_BRANCH}"
  exit 1
fi

# Validate prefix: must start with BRANCH followed by exactly a hyphen and at least one more character
if [[ ! "${SUBAGENT_BRANCH}" =~ ^[a-zA-Z0-9_-]+-[a-zA-Z0-9_-]+$ ]] || \
   [[ "${SUBAGENT_BRANCH}" != "${BRANCH}-"* ]]; then
  echo "ERROR: Subagent branch ${SUBAGENT_BRANCH} does not have expected prefix ${BRANCH}-"
  exit 1
fi

# Verify the branch actually exists in git before merging
if ! git -C "${WORKTREE_PATH}" rev-parse --verify "refs/heads/${SUBAGENT_BRANCH}" >/dev/null 2>&1; then
  echo "ERROR: Subagent branch ${SUBAGENT_BRANCH} does not exist in git. Cannot merge."
  exit 1
fi

cd "${WORKTREE_PATH}" && git merge --ff-only "${SUBAGENT_BRANCH}"
if [[ $? -ne 0 ]]; then
  echo "ERROR: Fast-forward merge of ${SUBAGENT_BRANCH} failed (job ${NEXT_MERGE}). The branch has diverged."
  echo "Use /cat:git-merge-linear-agent to resolve the diverged history before merging the next job."
  exit 1
fi
# ... repeat for each job in ascending order using NEXT_MERGE counter
```

The subagent branch name and worktree path for each job are returned in the Task tool result when
`isolation: "worktree"` is used.

- Collect commits from all jobs into a single combined list
- If any job returns FAILED or BLOCKED, stop and report failure
- Aggregate `files_changed`, `tokens_used`, and `compaction_events` across all jobs

### Reactive Job Re-Splitting

After collecting the result from a completed job subagent, check `percent_of_context` before spawning
the next job:

**If `percent_of_context > 40`** (high context usage):

1. Read the remaining unsplit jobs from plan.md (those not yet spawned).
2. For each remaining job, split it in half:
   - Move the second half of its bullet items into a new `### Job N+k` inserted immediately after it.
   - If a job has only one bullet item, it cannot be split — leave it as-is.
3. Renumber all subsequent job headers to maintain a gapless sequence (Job 1, 2, 3, ...).
4. Write the updated plan.md back to disk.
5. Re-run the canonical `JOBS_COUNT` detection command to get the updated count before spawning.

**If `percent_of_context <= 40`**: proceed without modifying plan.md.

This check applies whether using single-subagent or parallel execution — always check `percent_of_context`
from the most recently completed job's result before starting the next job. See
`plugin/concepts/token-warning.md` for compaction-event handling that interacts with this flow.

### Handle Execution Result

Parse the subagent result(s):

- **SUCCESS/PARTIAL** (all groups): Merge commits, aggregate metrics, proceed to verification
- **FAILED** (any group): Return FAILED status with error details from that group
- **BLOCKED** (any group): Return FAILED with blocker info from that group

### Verify Commit Messages

After execution completes, verify that the subagent used the correct commit messages and amend any mismatches before
proceeding to stakeholder review.

**Note the expected commit message before spawning the subagent:**

The delegation prompt specifies the commit message format the subagent should use. The expected commit type is
determined per-commit based on what the orchestrator specified in the delegation prompt. Issues may produce multiple
commit types (e.g., `feature:` for implementation + `docs:` for documentation). Each commit's type prefix should match
what the orchestrator instructed for that specific deliverable.

**Get actual commit messages from git:**

```bash
cd "${WORKTREE_PATH}" && git log --format="%H %s" ${TARGET_BRANCH}..HEAD
```

This returns lines of: `<commit-hash> <commit-subject>`.

**Error handling:** If git log fails (non-zero exit code), log a warning and skip verification. Verification failures
should not block the workflow.

**Compare against subagent-reported messages:**

1. Check if the execution result's `commits[]` array is empty. If empty, skip verification.
2. Check if git log returned no commits. If no commits, skip verification.
3. For each commit in the `commits[]` array:
   - Extract the reported `hash` and `message` values
   - Find the corresponding line in git log output by matching the hash
   - If hash not found in git log output, treat as HIGH severity (subagent reporting error)
   - If found, compare the reported message against the actual commit subject from git log
   - Verify the commit message uses the expected type prefix specified in the delegation prompt

**If commit count mismatch detected:**

If the number of commits in `commits[]` differs from the number of lines in git log output:
- Extra commits in git log (not in reported array): Log WARNING - note them but do not amend (not actionable)
- Missing commits (in reported array but not in git log): Log HIGH severity - indicates subagent reporting error

**If message mismatch detected:**

When a mismatch is detected, the orchestrator MUST amend the commit(s) to use the correct message using
`cat:git-amend-agent`. Do NOT use `git commit --amend` directly.

**For a single mismatched commit:**

```
Skill tool:
  skill: "cat:git-amend-agent"
  args: "<correct commit message>"
```

**For multiple mismatched commits:** Use `cat:git-amend-agent` for each commit that needs correction,
working from **oldest to newest** (i.e., starting with the commit farthest from HEAD). After amending
the oldest commit, re-run `git log --format="%H %s" ${TARGET_BRANCH}..HEAD` to get the updated hashes
before amending the next commit, since amending rewrites all descendant commit hashes.

**Prohibited amend methods** — the following MUST NOT be used:
- `git commit --amend` (use `cat:git-amend-agent` instead)
- `git rebase -i` (prohibited by CLAUDE.md; interactive rebase requires the `-i` flag which is blocked)
- Any other interactive git command

Track all amendments and include in the approval gate summary:

```
## Commit Message Verification
⚠ Mismatches detected and corrected:
  - af069982: "<placeholder>" → "feature: add verification step"
  - b1234abc: "<placeholder>" → "bugfix: correct parameter validation"
```

**If all messages match:**
- Continue silently to return
