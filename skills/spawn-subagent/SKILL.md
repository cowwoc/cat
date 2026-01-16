---
name: cat:spawn-subagent
description: Launch subagent with task context in isolated worktree with token tracking
---

# Spawn Subagent

## Purpose

Launch a Claude Code subagent in an isolated git worktree to execute a specific task. The subagent
operates independently with its own context window while the parent agent continues coordinating.

## Critical: Unsupervised Execution

**Subagents run without user supervision.** Claude Code does not provide a way for users to view
subagent output while it runs. Users cannot:

- See what the subagent is doing
- Correct mistakes in real-time
- Answer questions or clarify requirements
- Provide feedback during execution

**All decision-making MUST happen in the main agent before spawning.** The subagent prompt must be
comprehensive enough that execution is purely mechanical - following explicit instructions without
judgment calls.

## Hook Inheritance (A008)

**Subagents inherit project hooks automatically** when running in the same project directory.
However, subagents may not follow hook guidance if not explicitly reminded.

**MANDATORY: Include key prohibitions in subagent prompt:**

```
CRITICAL PROHIBITIONS (enforced by hooks):
- NEVER add PMD suppression annotations - decompose code instead
- NEVER use git merge --no-ff - use --ff-only for linear history
- NEVER use git filter-branch - use git-filter-repo instead
- NEVER delete .git/refs/original without explicit user request
- Tests for bugfixes belong in SAME commit as the fix, not separate

These are ABSOLUTE rules. Violation will be detected and blocked.
```

**Why explicit in prompt:** Hooks can block commands, but subagents may try alternatives. Stating
prohibitions in the prompt prevents wasted effort on blocked approaches.

## Prerequisites

**SESSION_ID Required**: Get the session ID from the SessionStart system-reminder in conversation context.
Look for `Session ID: {uuid}` and extract the UUID. Substitute this value into all bash commands below
that reference `${SESSION_ID}`.

## When to Use

- Task has a well-defined PLAN.md ready for execution
- **All ambiguities resolved** - main agent has made all decisions
- Task is independent enough to execute in isolation
- Parent agent needs to continue with other work
- Context window management requires task isolation

## Concurrent Execution Safety

This skill respects task-level locking. Before spawning, verify the parent agent holds the task lock.
The lock should have been acquired by `/cat:execute-task`. Subagents inherit lock ownership through
their worktree association (recorded in the lock file).

## Prompt Requirements: Zero Decision Delegation

**MANDATORY**: Before spawning, ensure the prompt contains everything needed for mechanical execution.

### What the Prompt MUST Include

| Element | Why Required |
|---------|--------------|
| Clear task type | "Explore and report" OR "Execute these steps" - never both |
| Fail-fast conditions | When to stop and report BLOCKED |
| Exact file paths | For implementation tasks |
| Specific code changes | Before/after examples, not descriptions |
| Test verification steps | Explicit commands to run, expected outcomes |
| Edge cases to handle | Subagent won't discover these independently |
| Commit message format | Exact text, not guidelines |

### Fail-Fast Requirements

**CRITICAL**: Every prompt must include fail-fast conditions.

```bash
# Always include:
FAIL-FAST CONDITIONS:
- If [specific condition], report "BLOCKED: [reason]" and stop
- Do NOT attempt workarounds or fallback approaches
- Do NOT make decisions about how to proceed
- Return findings/status for main agent to decide next steps
```

Subagents must never use fallback behaviors - those involve decisions that users can't supervise.

### Main Agent Responsibilities (BEFORE Spawning)

1. **Read all relevant code** - Don't delegate exploration
2. **Make architectural decisions** - Which pattern, which API, which approach
3. **Resolve ambiguities** - If PLAN.md says "handle errors appropriately", decide HOW
4. **Identify edge cases** - Subagent executes happy path unless told otherwise
5. **Write explicit examples** - Code snippets, not prose descriptions
6. **Specify verification** - Exact test commands and expected output

### Prompt Completeness Checklist

Before spawning, verify your prompt answers:

- [ ] Is this exploration/research OR implementation? (never both)
- [ ] What are the fail-fast conditions? (when to stop and report BLOCKED)
- [ ] What files to create/modify? (exact paths, for implementation)
- [ ] What code to write? (actual code, not descriptions)
- [ ] What tests to run? (exact commands)
- [ ] What does success look like? (specific criteria)
- [ ] What if the build fails? (fail-fast, not recovery)
- [ ] What commit message to use? (exact text, for implementation)

### Example: Exploration Task (gather info, no action)

**❌ WRONG (explores AND decides):**
```
Find the best place to add caching and implement it.
```

**✅ CORRECT (explores, returns findings):**
```
Find all database query methods in src/repository/.

Return for each method:
- File path and line number
- Method signature
- Estimated call frequency (from usages)
- Current caching status (none/exists)

FAIL-FAST:
- If src/repository/ doesn't exist, report BLOCKED
- If no query methods found in 10 minutes, report BLOCKED
- Do NOT implement caching - return findings only
```

### Example: Implementation Task (execute plan, no decisions)

**❌ WRONG (requires decisions):**
```
Implement the Parser class following PLAN.md.
Add appropriate error handling.
Write tests for the main functionality.
```

**✅ CORRECT (mechanical execution):**
```
Create src/parser/Parser.java with this implementation:

[Full code listing with all methods]

Create test/parser/ParserTest.java:

[Full test code with expected values]

Verification:
1. Run: ./gradlew test --tests ParserTest
2. Expected: BUILD SUCCESSFUL, 5 tests passed

FAIL-FAST:
- If tests fail, report BLOCKED with failure output
- Do NOT modify code to fix failures - report and stop

Commit:
  message: "feature: add Parser class for token processing"
  files: src/parser/Parser.java, test/parser/ParserTest.java
```

## Workflow

### 1. Generate Subagent Identifiers

```bash
# Generate UUID for uniqueness
UUID=$(uuidgen | cut -c1-8)

# Construct names from task context
# Format: {major}.{minor}-{task-name}-sub-{uuid}
WORKTREE_NAME="1.2-implement-parser-sub-a1b2c3d4"
BRANCH_NAME="1.2-implement-parser-sub-a1b2c3d4"
```

### 2. Create Worktree and Branch

```bash
# Create worktree directory
WORKTREE_PATH=".worktrees/${WORKTREE_NAME}"

# Verify task lock is held (should be acquired by execute-task)
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
LOCK_STATUS=$("${CLAUDE_PLUGIN_ROOT}/scripts/task-lock.sh" check "$TASK_ID" 2>/dev/null || echo '{}')
if ! echo "$LOCK_STATUS" | jq -e '.locked == true' > /dev/null 2>&1; then
  echo "WARNING: Task lock not held. Acquire lock via execute-task first."
fi

# Create branch and worktree together
git worktree add -b "${BRANCH_NAME}" "${WORKTREE_PATH}" HEAD
```

### 3. Prepare Task Context

Copy or reference the task's PLAN.md that the subagent will execute:

```bash
# Ensure PLAN.md exists in task directory
TASK_PLAN=".claude/cat/tasks/${MAJOR}.${MINOR}-${TASK_NAME}/PLAN.md"

# Subagent will read this directly - no copy needed
```

### 4. Configure Session Tracking

Create session tracking files:

```bash
# Write session ID to worktree for monitoring script
echo "${SESSION_ID}" > "${WORKTREE_PATH}/.session_id"

# Session file location (for reference)
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"
```

### 5. Launch Subagent

```bash
# Change to worktree and launch Claude Code
cd "${WORKTREE_PATH}"

# Launch with task context - MUST include completion marker requirement
claude --resume "${SESSION_ID}" \
  --prompt "Execute PLAN.md at ${TASK_PLAN}.

COMPLETION REQUIREMENT:
When finished, write completion marker to worktree:
  echo '{\"status\":\"success\",\"tokensUsed\":N,\"compactionEvents\":N,\"summary\":\"...\"}' > .completion.json

Calculate tokens: jq -s '[.[] | select(.type == \"assistant\") | .message.usage | (.input_tokens + .output_tokens)] | add' \"/home/node/.config/claude/projects/-workspace/\${SESSION_ID}.jsonl\"
Count compactions: jq -s '[.[] | select(.type == \"summary\")] | length' same-file"
```

**CRITICAL**: The `.completion.json` marker enables lightweight monitoring. Without it, the parent
must parse session files to detect completion.

### 6. Record Spawn in Parent STATE.md

Update parent's tracking:

```yaml
subagents:
  - id: a1b2c3d4
    task: 1.2-implement-parser
    worktree: .worktrees/1.2-implement-parser-sub-a1b2c3d4
    branch: 1.2-implement-parser-sub-a1b2c3d4
    session: ${SESSION_ID}
    spawned_at: 2026-01-10T14:30:00Z
    status: running
```

## Examples

### Basic Spawn

```bash
# Parent agent spawning subagent for parser task
TASK="1.2-implement-parser"
UUID="a1b2c3d4"
WORKTREE=".worktrees/${TASK}-sub-${UUID}"

git worktree add -b "${TASK}-sub-${UUID}" "${WORKTREE}" HEAD

# Write session ID for monitoring script
echo "${SESSION_ID}" > "${WORKTREE}/.session_id"

# Launch subagent with completion marker requirement
cd "${WORKTREE}"
claude --prompt "Execute task 1.2 PLAN.md.
On completion: echo '{\"status\":\"success\",\"tokensUsed\":N,\"compactionEvents\":N,\"summary\":\"...\"}' > .completion.json"
```

### Spawn with Token Budget

```bash
# Include token budget and completion marker requirement
claude --prompt "Execute task 1.2 PLAN.md. Token budget: 80,000 tokens.
On completion: echo '{\"status\":\"success\",\"tokensUsed\":N,\"compactionEvents\":N,\"summary\":\"...\"}' > .completion.json"
```

## Anti-Patterns

### Do NOT delegate decisions to subagents

```bash
# ❌ Requires subagent to make decisions
claude --prompt "Implement error handling for the parser. Choose appropriate exception types."

# ✅ All decisions made by main agent
claude --prompt "Add error handling to Parser.java:
- Line 45: wrap in try-catch, throw ParseException(\"Invalid token at position \" + pos)
- Line 72: add null check, throw IllegalArgumentException(\"Input cannot be null\")
- All exceptions must include position information for debugging"
```

### Do NOT spawn without PLAN.md

```bash
# ❌ Spawning with vague instructions
claude --prompt "Work on the parser"

# ✅ Spawning with concrete plan and completion marker requirement
claude --prompt "Execute PLAN.md at .claude/cat/tasks/1.2-implement-parser/PLAN.md.
On completion: echo '{\"status\":\"success\",...}' > .completion.json"
```

### Do NOT assume subagent will infer implementation details

When specific API usage or patterns are required, provide explicit before/after code examples:

```bash
# ❌ Vague instruction - subagent may find different solution
claude --prompt "Remove the unnecessary cast in LexerTest.java"

# ✅ Explicit code example showing expected change
claude --prompt "Change LexerTest.java line 625:
  FROM: requireThat(token.text() == token.decodedText(), \"sameInstance\").isTrue();
  TO:   requireThat(token.text(), \"token.text()\").isReferenceEqualTo(token.decodedText(), \"token.decodedText()\");"
```

**Why**: Subagents optimize for passing tests/builds. Without explicit examples, they may find alternative
solutions (e.g., @SuppressWarnings) that technically work but don't match the intended approach.

### Do NOT let subagent derive test expected values from actual output

For parser/test tasks, include manual derivation requirement in prompt:

```bash
# ❌ Missing test derivation guidance
claude --prompt "Add parser tests for new feature"

# ✅ Explicit manual derivation requirement
claude --prompt "Add parser tests for new feature.

CRITICAL: Test expected values MUST be manually derived:
1. Analyze source string character by character
2. Determine expected node types from Java grammar
3. Use (0, 0) placeholders for positions initially
4. VERIFY actual positions are correct before updating expected values
5. NEVER copy actual output as expected values without verification"
```

**Why**: Subagents may use placeholder technique incorrectly - copying actual output without verification
creates tests that pass but don't validate correctness.

### Do NOT let subagent create tests that only check isSuccess()

Parser tests MUST verify AST structure, not just parsing success:

```bash
# ❌ WRONG - Only checks parsing succeeded
claude --prompt "Add tests for new parser feature.
@Test
public void testNewFeature() {
    ParseResult result = parser.parse(source);
    requireThat(result.isSuccess(), ...).isTrue();  // INADEQUATE!
}"

# ✅ CORRECT - Verifies expected AST structure
claude --prompt "Add tests for new parser feature.
@Test
public void testNewFeature() {
    ParseResult result = parser.parse(source);
    requireThat(result.isSuccess(), ...).isTrue();
    NodeArena actual = result.arena();
    NodeArena expected = new NodeArena();
    // Build expected AST with exact node types and positions
    expected.allocateNode(NodeType.X, startPos, endPos);
    requireThat(actual, \"actual\").isEqualTo(expected);
}"
```

**Why (M062)**: Tests that only check `isSuccess()` provide false confidence - parsing can succeed
but produce incorrect AST. The `isEqualTo(expected)` pattern catches structural errors.

### Do NOT spawn dependent tasks simultaneously

```bash
# ❌ Spawning B that depends on A's output
spawn-subagent task-a
spawn-subagent task-b  # Depends on task-a!

# ✅ Wait for dependency
spawn-subagent task-a
# ... wait for completion ...
spawn-subagent task-b
```

### Do NOT forget worktree cleanup path

```bash
# ❌ No tracking of worktree location
git worktree add somewhere

# ✅ Track for later cleanup
WORKTREE=".worktrees/${TASK}-sub-${UUID}"
git worktree add "${WORKTREE}"
# Record in STATE.md for merge-subagent skill
```

### Do NOT spawn from within a subagent worktree

Subagents should not spawn further subagents. If a task needs decomposition, report back to the
parent agent.

### Do NOT let subagents make decisions based on exploration

Subagents CAN explore/research, but must return findings for main agent to decide - not act on them.

```bash
# ❌ Subagent explores AND decides what to do
claude --prompt "Find where authentication is handled and add rate limiting"

# ✅ Subagent explores, returns findings, main agent decides later
claude --prompt "Find all authentication entry points in the codebase.
Return a list of:
- File path and line number
- Method signature
- Current error handling approach

FAIL-FAST: If you cannot locate authentication code within 10 minutes,
report 'BLOCKED: Could not locate auth code' and stop.
Do NOT implement anything - return findings only."

# ✅ OR: Main agent already explored, provides exact instructions
claude --prompt "Add rate limiting to src/auth/AuthService.java:
In the authenticate() method at line 45, add before the password check:
  if (rateLimiter.isRateLimited(username)) {
    throw new RateLimitExceededException(username);
  }"
```

### Do NOT use vague success criteria

```bash
# ❌ Subagent must judge "working correctly"
claude --prompt "Make sure the parser handles all edge cases correctly"

# ✅ Explicit verification steps
claude --prompt "Verify parser handles edge cases:
1. Run: ./gradlew test --tests 'ParserEdgeCaseTest'
2. All 12 tests must pass
3. Run: ./scripts/parse-corpus.sh testdata/edge-cases/
4. Output must show: 'Processed 47 files, 0 errors'

FAIL-FAST: If any test fails, report BLOCKED with output. Do NOT fix."
```

### Do NOT allow fallback behaviors

```bash
# ❌ Fallback involves decisions
claude --prompt "Try to use the new API. If it doesn't work, fall back to the legacy API."

# ✅ Fail-fast, let main agent decide
claude --prompt "Use the new API at src/api/v1/Client.java.

FAIL-FAST:
- If new API returns errors, report BLOCKED with error details
- Do NOT fall back to legacy API
- Do NOT try alternative approaches
- Return status for main agent to decide next steps"
```

**Why**: Choosing between approaches is a decision. Decisions require user oversight.

## Related Skills

- `cat:monitor-subagents` - Check status of spawned subagents
- `cat:collect-results` - Gather results when subagent completes
- `cat:merge-subagent` - Merge subagent work back to task branch
- `cat:parallel-execute` - Spawn multiple subagents concurrently
