<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Work Phase: Implement

Implement phase for `/cat:work`. Displays preparing/implementing banners, verifies lock ownership,
and orchestrates subagent execution of the implementation plan.

## Arguments Format

```
<catAgentId> <issue_id> <issue_path> <worktree_path> <issue_branch> <target_branch> <estimated_tokens> <trust> <verify>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | catAgentId | agent ID passed through from parent |
| 2 | issue_id | `2.1-issue-name` |
| 3 | issue_path | `/workspace/.cat/issues/v2/v2.1/issue-name` |
| 4 | worktree_path | `${CLAUDE_PROJECT_DIR}/.cat/work/worktrees/2.1-issue-name` |
| 5 | issue_branch | `2.1-issue-name` |
| 6 | target_branch | `v2.1` |
| 7 | estimated_tokens | `45000` |
| 8 | trust | `medium` |
| 9 | verify | `changed` |

## Output Contract

Return JSON when complete:

```json
{
  "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
  "waves_count": 1,
  "commits": [
    {"hash": "abc123", "message": "feature: description", "type": "feature"}
  ],
  "files_changed": 5,
  "tokens_used": 12000,
  "compaction_events": 0
}
```

`waves_count` MUST be the integer value printed by the canonical detection command (`echo "WAVES_COUNT=${WAVES_COUNT}"`).
It may NOT be derived by reading PLAN.md into context and counting `### Wave ` occurrences mentally or via any method
other than running that Bash command. If the command was not run (e.g., because PLAN.md was already in context),
run it now before returning.

**Relay prohibition:** `waves_count` (even when correctly grep-derived) MUST NOT be embedded into any subagent
prompt text. Do NOT write "there are N waves" or "WAVES_COUNT=N" or any equivalent into a subagent prompt.
Each subagent determines its own wave count by reading `PLAN_MD_PATH` directly.

## Configuration

Extract configuration from the positional ARGUMENTS string. The arguments are space-separated.
Since some paths may theoretically contain spaces but in practice CAT paths never do,
split on whitespace. Also display the preparing banner in a chained call:

```bash
# Parse positional arguments, set PLAN_MD path, and display preparing banner
read CAT_AGENT_ID ISSUE_ID ISSUE_PATH WORKTREE_PATH BRANCH TARGET_BRANCH ESTIMATED_TOKENS TRUST VERIFY <<< "$ARGUMENTS" && \
PLAN_MD="${ISSUE_PATH}/PLAN.md" && \
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase preparing
```

## Step 1: Display Preparing Banner

Capture the exit code and stdout separately:

```bash
BANNER_OUT=$("${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase preparing 2>/tmp/banner-stderr.txt)
BANNER_EXIT=$?
```

**If the binary exited non-zero**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'preparing' (exit code <BANNER_EXIT>).
stderr: <contents of /tmp/banner-stderr.txt>
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```

**If the binary exited 0 but stdout appears whitespace-only**, proceed — assume the banner uses
invisible or terminal-control rendering that is not captured in this context. Do NOT treat a
whitespace-only stdout at exit 0 as a failure or use it as grounds to abort.

Do NOT skip the banner step itself; always run the binary.

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
"${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase implementing
```

Capture the exit code and stdout separately:

```bash
BANNER_OUT=$("${CLAUDE_PLUGIN_ROOT}/client/bin/progress-banner" ${ISSUE_ID} --phase implementing 2>/tmp/banner-stderr.txt)
BANNER_EXIT=$?
```

**If the binary exited non-zero**, STOP immediately:
```
FAIL: progress-banner launcher failed for phase 'implementing' (exit code <BANNER_EXIT>).
stderr: <contents of /tmp/banner-stderr.txt>
The jlink image may not be built. Run: mvn -f hooks/pom.xml verify
```

**If the binary exited 0 but stdout appears whitespace-only**, proceed — assume the banner uses
invisible or terminal-control rendering that is not captured in this context. Do NOT treat a
whitespace-only stdout at exit 0 as a failure or use it as grounds to abort.

Do NOT skip the banner step itself; always run the binary.

### Read PLAN.md and Invoke Main Agent Waves

Read the `## Main Agent Waves` section from PLAN.md:

```bash
PLAN_MD="${ISSUE_PATH}/PLAN.md" && MAIN_AGENT_WAVES=$(sed -n '/^## Main Agent Waves/,/^## /p' "$PLAN_MD" | head -n -1)
```

**If `## Main Agent Waves` is present and non-empty:** extract each bullet item
(`- /cat:skill-name args`) and invoke the corresponding skill NOW at the main agent level
using the Skill tool.

Example: If `## Main Agent Waves` contains `- /cat:example-skill path/to/file.md`, then:

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
PLAN.md content — even if that description originates from the skill rather than from the agent directly.
A skill that reads PLAN.md and echoes its goal or steps back as part of its output does not make that
content eligible for relay. If skill output cannot be cleanly separated from PLAN.md paraphrase,
omit the PLAN.md-describing portion and include only the non-PLAN.md functional results.

**PLAN.md paraphrase detection:** Any text in skill output that restates, summarizes, or quotes
the issue goal, step names, acceptance criteria, or wave structure from PLAN.md is PLAN.md paraphrase,
regardless of whether the text was produced by the skill or copied from PLAN.md by the skill. The
origin of the text (skill vs. agent) is irrelevant — only the content matters. When in doubt, omit.
Permitted metric examples: "Reduced file size from 5000 to 3000 tokens", "Validation passed: 0 errors".
Prohibited metric examples: "Compressed PLAN.md. Goal: add user authentication" — the goal text is
PLAN.md paraphrase embedded in a metric and must be stripped before inclusion.

### Detect Parallel Execution Waves

**CRITICAL: `WAVES_COUNT` MUST be derived exclusively from the Bash canonical command output below.
It MUST NOT be derived from in-context PLAN.md content, mental counting, or any other method —
even if PLAN.md has already been read into context for other purposes. Reading PLAN.md into context
does not make its content a permitted source for the wave count. The canonical Bash command is the
only permitted source.**

Read PLAN.md directly to count `### Wave N` subsections using the canonical command:

```bash
WAVES_COUNT=$(grep -c '^### Wave ' "$PLAN_MD") && echo "WAVES_COUNT=${WAVES_COUNT}"
```

Use this exact command — do NOT substitute alternative counting logic or Bash constructs. Any other method is
prohibited. The `echo` is part of the same command; do not split detection and printing into separate Bash
calls. No further Bash calls are permitted after this step before the prepare phase begins.

**If waves are empty or only one wave is present (`WAVES_COUNT` is 0 or 1):** proceed to single-subagent execution
(see below). Parse execution items from `## Sub-Agent Waves` / `### Wave 1`.

**If two or more waves are present (`WAVES_COUNT` >= 2):** use parallel execution (see Parallel Subagent Execution
below). The last wave for STATE.md ownership is `### Wave ${WAVES_COUNT}` (the highest numbered wave).

### Mid-Work PLAN.md Revision

If requirements change, read `EFFORT` from `${CLAUDE_PROJECT_DIR}/.cat/config.json`, then invoke:
`Skill("cat:plan-builder-agent", "${CAT_AGENT_ID} ${EFFORT} revise ${ISSUE_PATH} <description of what changed>")`

After revision, re-read the updated PLAN.md and adjust remaining execution accordingly.

### Delegation Prompt Construction

**Subagents read PLAN.md directly — do NOT relay its content into prompts.**

Pass `PLAN_MD_PATH` so the subagent can read the Goal and Sub-Agent Waves/Steps sections itself.
Do NOT extract and paste those sections into the prompt — that is the content relay anti-pattern.

**Why:** Subagents that receive a `PLAN_MD_PATH` and read PLAN.md themselves always see the authoritative
content, preserving PLAN.md's structure exactly (distinct steps remain distinct, no re-summarization).
Pasting content into prompts creates a stale copy that can diverge, wastes tokens, and risks
interpretive distortion.

**Pattern:**
- ✅ Pass `PLAN_MD_PATH: ${PLAN_MD}` and instruct the subagent to read Goal and Execution sections itself
- ✅ Trust PLAN.md structure — subagents read it directly, no relay needed
- ❌ Do NOT inline `${ISSUE_GOAL}` or Sub-Agent Waves content into the prompt
- ❌ Do NOT add interpretive summaries or aggregate instructions that restate PLAN.md differently

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

**CRITICAL: PLAN.md and STATE.md must NOT be committed here.** Before committing, run:

```bash
cd "${WORKTREE_PATH}" && git status --porcelain | awk '{print $NF}' | \
  while IFS= read -r filepath; do
    basename=$(basename "$filepath")
    if echo "$basename" | grep -Eqi '^plan\.md$|^state\.md$'; then
      echo "BLOCKED: dirty planning file detected: $filepath"
      exit 1
    fi
  done
```

If this check prints any `BLOCKED:` line, STOP immediately and return FAILED status.
Do NOT stage, commit, or otherwise alter PLAN.md or STATE.md before spawning.

Note: `-E` (extended regex) is required so that `|` acts as alternation, not a literal pipe
character. Without `-E`, the pattern would only match a filename literally containing a pipe.

If the worktree is dirty with other files (non-PLAN.md, non-STATE.md), stage only those specific files by
**explicitly listing each file path** and commit:

```bash
cd "${WORKTREE_PATH}" && git add <explicit-file-path-1> <explicit-file-path-2> ... && git commit -m "planning: update before delegation"
```

**Prohibited staging commands** — the following forms MUST NOT be used, as they may accidentally stage
PLAN.md or STATE.md:
- `git add -A`
- `git add .`
- `git add -u`
- `git add --update`
- `git add --all`
- Any glob or wildcard pattern (e.g., `git add *.java`, `git add src/`)

Each file to be staged must be named explicitly by its full path relative to the worktree root.

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

    Read the Goal section and Sub-Agent Waves (or Execution Steps) from PLAN_MD_PATH directly.
    Do NOT ask the main agent to provide this content — it is authoritative in PLAN.md.

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## Critical Requirements
    - You are working in an isolated worktree. Your changes will be merged back to the issue branch.
    - Follow execution steps from PLAN.md EXACTLY
    - If steps say to invoke a skill that was pre-invoked above, use the provided results
    - Update STATE.md in the SAME commit as implementation (status: closed, progress: 100%)
    - Run tests if applicable
    - Commit your changes using the commit type from PLAN.md (e.g., `feature:`, `bugfix:`, `docs:`). The commit message must follow the format: `<type>: <descriptive summary>`. Example: `feature: add user authentication with JWT tokens`. Do NOT use generic messages like 'squash commit' or 'fix'.

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

**After the subagent returns**, invoke `cat:collect-results-agent` before any further Skill or Task tool calls.
The `EnforceCollectAfterAgent` hook blocks all Skill and Task calls until collect-results-agent is invoked.

```
Skill tool:
  skill: "cat:collect-results-agent"
  args: "${CAT_AGENT_ID}/${SUBAGENT_RAW_ID} ${ISSUE_PATH} <subagent_commits_json_path>"
```

Where `SUBAGENT_RAW_ID` is the `agentId:` value from the Task tool result footer, and
`subagent_commits_json_path` is a temp file path to write the collected commits JSON.

Then validate and merge its commits back into the issue branch:

```bash
SUBAGENT_BRANCH="<branch name from Task tool result metadata>"

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
```

The subagent branch name and worktree path are returned in the Task tool result when `isolation: "worktree"` is
used. Use that branch name in the merge command above.

### Parallel Subagent Execution (two or more groups)

When PLAN.md contains two or more execution groups, spawn one subagent per group simultaneously.
Each subagent is spawned with `isolation: "worktree"` — it gets its own isolated git worktree branched from
the issue branch HEAD. Subagents execute concurrently without shared disk state. The last group's subagent
updates STATE.md; other groups skip it.

**IMPORTANT:** Each parallel subagent commits to its own isolated worktree branch. After all subagents
complete, the main agent merges each subagent branch back into the issue branch in order (A, B, C, ...).
Only the last wave subagent updates STATE.md.

**CRITICAL: Parallel means one API response — not "start Wave 1, then start Wave 2".**
When `WAVES_COUNT` >= 2, spawn ALL wave subagents in a SINGLE assistant API response by making multiple
Task tool calls in that same response. An "API response" is one assistant message turn: everything between
receiving the user/tool input and sending back the next assistant message. Do NOT spawn Wave 1, await its
result, then spawn Wave 2 in a separate API response — that is sequential execution masquerading as parallel.

**Violation test (observable and unambiguous):** If you issued a Task call and received any tool result
before all Task calls were issued, you violated the rule — regardless of intent or turn-boundary awareness.

**No tool calls are permitted between Task spawns.** After issuing the first Task call for Wave 1, the very
next tool call in that same assistant message must be the Task call for Wave 2 (and Wave 3, if present, and
so on). No Bash, Read, Grep, Write, Skill, or any other tool call may appear between the Task calls.
Safety checks, verification reads, status queries, and skill invocations between Task spawns are all
prohibited.

**All wave prompts are constructable from PLAN.md alone.** PLAN.md is the single authoritative source for
wave contents. Each wave prompt contains only: the issue configuration variables (already known before
reading PLAN.md), the `PLAN_MD_PATH` reference, and the `ASSIGNED_WAVE` number. No wave prompt requires
output from another wave in order to be constructed. Therefore, all prompts can and must be fully drafted
before any Task call is issued.

**`WAVES_COUNT` must NOT appear in any subagent prompt.** Do not embed the numeric wave count (e.g.,
"WAVES_COUNT=3", "there are 3 waves", or any equivalent phrasing) into a subagent prompt. Including it
would relay structural PLAN.md metadata, which is prohibited. Each subagent reads `PLAN_MD_PATH` directly
and determines wave structure itself.

**Prepare ALL prompts before issuing ANY tool call in the spawn phase.**

Two-phase execution:

1. **Prepare phase (written in response text, no tool calls):** Write out every wave prompt in the
   assistant response text, derived from already-in-context variables and PLAN.md content. The prepare
   phase text must include the complete text of each wave's prompt — it is not complete without the actual
   prompts. This phase is observable: the prompts appear as text in the response before any Task call.
   Do NOT issue any tool call (Bash, Read, Grep, Write, Task, Skill, or any other) during this phase.

   **Permitted pre-prepare tool calls (strictly bounded):** Before entering the prepare phase, you may
   issue tool calls only to satisfy two concrete prerequisites: (a) read PLAN.md into context if it is
   not already there, and (b) run the canonical `WAVES_COUNT` detection command. No other tool
   calls are permitted before the prepare phase. Once both prerequisites are satisfied, the very next
   assistant action must be the prepare phase text (containing the full wave prompts) followed immediately
   by the spawn phase.

   **`WAVES_COUNT` routing is determined by the canonical Bash command result only.** Even after reading
   PLAN.md into context, the agent MUST NOT use the in-context PLAN.md content to decide whether to use
   single-subagent or parallel execution. The routing decision (single vs. parallel) MUST wait for and
   use the integer value printed by the canonical detection command. If the canonical command has not yet
   been run, run it before making any routing decision.

   **BLOCKING GATE — Write a wave spawn checklist before issuing the first Task call.** After writing all
   wave prompts in the response text, write this checklist as plain prose (not in a code block):

   Wave spawn checklist (WAVES_COUNT = N):
   - Wave 1 prompt: written above ✓
   - Wave 2 prompt: written above ✓
   ... (one entry per wave, from 1 to WAVES_COUNT)
   All N wave prompts written. Spawning all N Task calls now.

   Every wave from 1 to WAVES_COUNT must have a corresponding checklist entry. If any wave prompt is
   missing from the response text above the checklist, write it before completing the entry. Only after
   the checklist is complete may the spawn phase begin. This gate makes it structurally visible when a
   wave prompt was omitted — the gap appears in the checklist before any Task call is issued.

2. **Spawn phase (one API response, all Task calls together, nothing else):** Issue ALL Task tool calls
   simultaneously in a single assistant message, immediately following the prepare-phase text and the
   wave spawn checklist. The spawn phase contains ONLY Task tool calls — no other tool calls before,
   between, or after the Task calls within this phase.

Correct pattern (one message, two Task calls, nothing between them):

```
Task tool call: Wave 1 subagent
Task tool call: Wave 2 subagent
```

For each group (example for group A with steps 1, 2, 3):

```
Task tool:
  description: "Execute: implement ${ISSUE_ID} group A (steps 1, 2, 3)"
  subagent_type: "cat:work-execute"
  isolation: "worktree"
  prompt: |
    Execute the implementation for issue ${ISSUE_ID}, group A only.

    ## Issue Configuration
    ISSUE_ID: ${ISSUE_ID}
    ISSUE_PATH: ${ISSUE_PATH}
    WORKTREE_PATH: ${WORKTREE_PATH}
    BRANCH: ${BRANCH}
    TARGET_BRANCH: ${TARGET_BRANCH}
    ESTIMATED_TOKENS: ${ESTIMATED_TOKENS}
    TRUST_LEVEL: ${TRUST}
    PLAN_MD_PATH: ${PLAN_MD}
    ASSIGNED_WAVE: 1

    Read the Goal section from PLAN_MD_PATH. Then read ONLY the `### Wave 1` section from
    PLAN_MD_PATH for your execution items. Do NOT read or execute items from other wave sections.
    Do NOT ask the main agent to provide this content — it is authoritative in PLAN.md.

    ## Pre-Invoked Skill Results
    [If skills were pre-invoked above, include their output here]

    ## Critical Requirements
    - You are working in an isolated worktree. Your changes will be merged back to the issue branch.
    - Execute ONLY the items assigned to your wave (ASSIGNED_WAVE above, read from PLAN.md)
    - Do NOT execute items from other waves
    - **STATE.md ownership:** You are [DETERMINED AUTOMATICALLY: if wave is the last one, "the STATE.md owner"
      else "NOT the STATE.md owner"]. [If owner: "Update STATE.md in your final commit: status: closed,
      progress: 100%." Else: "Do NOT modify STATE.md in any commit."]
    - Run tests if applicable
    - Commit your changes using the commit type from PLAN.md (e.g., `feature:`, `bugfix:`, `docs:`). The commit
      message must follow the format: `<type>: <descriptive summary>`. Do NOT use generic messages.

    ## Return Format
    Return JSON when complete:
    ```json
    {
      "status": "SUCCESS|PARTIAL|FAILED|BLOCKED",
      "group": "A",
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
      "group": "A",
      "message": "Description of blocker",
      "blocker": "What needs to be resolved"
    }
    ```

    CRITICAL: You are the implementation agent - implement directly, do NOT spawn another subagent.
```

**Wait for all group subagents to complete. Then invoke `cat:collect-results-agent` for each subagent
before any further Skill or Task tool calls. The `EnforceCollectAfterAgent` hook blocks all Skill and Task
calls until collect-results-agent is invoked for each completed Agent tool result.**

For each completed subagent, call collect-results-agent:

```
Skill tool:
  skill: "cat:collect-results-agent"
  args: "${CAT_AGENT_ID}/${SUBAGENT_RAW_ID} ${ISSUE_PATH} <subagent_commits_json_path>"
```

Where `SUBAGENT_RAW_ID` is the `agentId:` value from that group's Task tool result footer.

Then validate and merge each subagent branch back into the issue branch in alphabetical order
(A first, then B, C, ...):

For each group's branch name received from the Task tool result, validate before merging:

```bash
SUBAGENT_BRANCH="<branch name from Task tool result metadata for this group>"

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
# For subsequent groups where ff-only fails (diverged history), use /cat:git-merge-linear-agent
# ... repeat for each group
```

The subagent branch name and worktree path for each group are returned in the Task tool result when
`isolation: "worktree"` is used.

- Collect commits from all groups into a single combined list
- If any group returns FAILED or BLOCKED, stop and report failure
- Aggregate `files_changed`, `tokens_used`, and `compaction_events` across all groups

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
