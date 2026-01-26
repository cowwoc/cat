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

**MANDATORY: Include key requirements in subagent prompt:**

```
CRITICAL REQUIREMENTS (enforced by hooks):
- Always decompose code instead of adding PMD suppression annotations
- Always use git merge --ff-only for linear history
- Always use git-filter-repo instead of git filter-branch
- Preserve .git/refs/original unless user explicitly requests deletion
- Include tests for bugfixes in the SAME commit as the fix

COMMIT SEPARATION (M089):
- .claude/rules/ updates → separate config: commit (not bundled with bugfix/feature)
- STATE.md updates → same commit as implementation (M076/M077)

These are ABSOLUTE rules. Violation will be detected and blocked.
```

**Why explicit in prompt:** Hooks can block commands, but subagents may try alternatives. Stating
prohibitions in the prompt prevents wasted effort on blocked approaches.

## When to Use

- Task has a well-defined PLAN.md ready for execution
- **All ambiguities resolved** - main agent has made all decisions
- Task is independent enough to execute in isolation
- Parent agent needs to continue with other work
- Context window management requires task isolation

## Subagent Types and Two-Stage Planning

**Planning Subagent (two stages for token efficiency):**

| Stage | Purpose | Output | Tokens |
|-------|---------|--------|--------|
| Stage 1 | High-level approach outlines | 3 brief options + agent_id | ~5K |
| Stage 2 | Detailed implementation spec | Full PLAN.md for selected approach | ~20K |

**Stage 1 prompt template:**
```
Analyze the task and produce HIGH-LEVEL outlines (1-2 sentences each) for:
- Conservative approach: [minimal scope, low risk]
- Balanced approach: [moderate scope, medium risk]
- Aggressive approach: [comprehensive, high risk]

Do NOT produce detailed execution steps yet. Keep outlines brief.
Return your agent_id for later resumption.
```

**Stage 2 prompt (using Task tool with `resume` parameter):**
```
resume: {agent_id from Stage 1}
prompt: "User selected [approach]. Now produce the DETAILED spec with:
- Specific files to modify
- Exact code changes
- Step-by-step execution
- Verification commands"
```

**Implementation Subagent:** Receives completed PLAN.md, executes mechanically.

## Concurrent Execution Safety

This skill respects task-level locking. Before spawning, verify the parent agent holds the task lock.
The lock should have been acquired by `/cat:work`. Subagents inherit lock ownership through
their worktree association (recorded in the lock file).

**MANDATORY: Verify Lock Ownership (M082)**

After any lock acquisition attempt, verify ownership by reading the actual lock file:

```bash
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
LOCK_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/locks/${TASK_ID}.lock"

# Verify lock file exists and we own it
if [[ ! -f "$LOCK_FILE" ]]; then
  echo "ERROR: Lock file does not exist at $LOCK_FILE"
  echo "Lock was NOT acquired. Another session may own this task."
  exit 1
fi

# Verify session_id matches current session
LOCK_SESSION=$(grep "^session_id=" "$LOCK_FILE" | cut -d= -f2)
if [[ "$LOCK_SESSION" != "$SESSION_ID" ]]; then
  echo "ERROR: Lock owned by different session: $LOCK_SESSION"
  echo "Current session: $SESSION_ID"
  echo "Do NOT proceed - another Claude instance is working on this task."
  exit 1
fi

echo "Lock verified: $LOCK_FILE owned by current session"
```

**Anti-pattern (M082):** Trusting lock script return value without verifying the actual lock file.
Lock directory must be `.claude/cat/locks/` (NOT `/tmp/cat-locks/` or other paths).

**After session restart (M083):** Re-run lock verification commands. Always re-verify state after
restart - the filesystem may have changed while the session was inactive.

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
| **STATE.md update** | Task STATE.md must be updated to completed IN THE SAME COMMIT |

### Fail-Fast Requirements

**CRITICAL**: Every prompt must include fail-fast conditions.

```bash
# Always include:
FAIL-FAST CONDITIONS:
- If [specific condition], report "BLOCKED: [reason]" and stop
- Report status and return to main agent for decisions
- Main agent handles all workarounds and fallback choices
```

Subagents use fail-fast behavior: report BLOCKED and stop. Fallback decisions require user oversight.

### Main Agent Responsibilities (BEFORE Spawning)

1. **Read all relevant code** - Complete exploration before spawning
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
- [ ] **Does prompt include STATE.md update?** (MUST be in same commit as implementation - M076/M077)
- [ ] **Does prompt include PLAN.md acceptance criteria?** (M260: validation commands, expected scores, verification requirements)
- [ ] **If task uses a skill, does prompt require invoking it?** (M261: "Use /cat:shrink-doc for each file" not "compress each file")

### Mandatory Subagent Prompt Checklist (A013)

**CRITICAL: Cross-reference recent learnings before spawning.**

Every subagent prompt MUST include these items based on past mistakes:

**STATE.md Requirements (M076, M077, M085, M087, M092):**
```
STATE.md UPDATE (required in SAME commit as implementation):
- Path: .claude/cat/issues/v{major}/v{major}.{minor}/{task-name}/STATE.md
- Set: Status: completed
- Set: Progress: 100%
- Set: Resolution: implemented (MANDATORY - not optional)
- Set: Completed: {YYYY-MM-DD HH:MM}
- Set: Tokens Used: {tokensUsed from .completion.json}
- Include STATE.md in git add before commit
```

**Trust Setting (from cat-config.json `trust` preference, for PLANNING subagents):**
```bash
TRUST_PREF=$(jq -r '.trust // "medium"' .claude/cat/cat-config.json)
```

| Value | Include in Planning Prompt |
|-------|---------------------------|
| `short` | "Present multiple options for the user to choose from. Include conservative, balanced, and comprehensive approaches." |
| `medium` | "Present options for meaningful trade-offs. For routine decisions, proceed with the balanced approach." |
| `long` | "Make autonomous decisions. Only present options when the choice has significant architectural implications." |

**Curiosity Setting (from cat-config.json `curiosity` preference, for IMPLEMENTATION subagents):**
```bash
CURIOSITY_PREF=$(jq -r '.curiosity // "low"' .claude/cat/cat-config.json)
```

| Value | Include in Implementation Prompt |
|-------|----------------------------------|
| `low` | "Focus ONLY on the assigned task. Report only task-related issues." |
| `medium` | "While working, NOTE obvious issues in files you touch (same function/class). Report them in .completion.json. Fixing is handled by main agent." |
| `high` | "Actively look for code quality issues, patterns, and improvement opportunities in files you touch. Report ALL findings in .completion.json. Fixing is handled by main agent." |

**IMPORTANT:** The implementor subagent reports discovered issues in `.completion.json`. Main agent
handles fixes based on the patience setting.

**Patience Setting — Main Agent Uses This (NOT passed to subagent):**

The patience setting determines what the MAIN AGENT does with issues returned from subagents.
Do NOT include patience instructions in implementation subagent prompts.

| Value | Main Agent Action on Returned Issues |
|-------|--------------------------------------|
| `low` | Resume PLANNER subagent to update plan with fixes, then continue execution |
| `medium` | Create tasks for discovered issues in CURRENT version backlog |
| `high` | Create tasks for discovered issues in FUTURE version backlog (prioritized by benefit/cost) |

**Issues Return Format (subagent writes to .completion.json):**
```json
{
  "status": "success",
  "tokensUsed": 65000,
  "inputTokens": 45000,
  "outputTokens": 20000,
  "compactionEvents": 0,
  "summary": "Implemented parser with full test coverage",
  "discoveredIssues": [
    {
      "file": "src/parser/Lexer.java",
      "line": 142,
      "type": "code-quality",
      "severity": "medium",
      "description": "Duplicate token validation logic could be extracted",
      "benefitCost": 2.5
    }
  ]
}
```

**discoveredIssues**: Only populated if curiosity is medium or high. Empty array if curiosity is low.

**Parser Test Requirements (M079, for parser tasks only):**
```
PARSER TEST STYLE:
- Use text blocks which are self-documenting (skip position comments)
- Use isEqualTo(expected) for assertions
- Derive expected values manually from source analysis
```

**Code Style Requirements:**
```
CODE STYLE:
- Decompose code instead of adding @SuppressWarnings annotations
- Rethrow caught exceptions as AssertionError (ensure visibility)
- Include tests for bugfixes in the SAME commit as the fix
```

**Token Tracking Requirements (A017 - CRITICAL):**

**MAIN AGENT MUST include session ID in prompt** - subagents cannot measure tokens without it.

Include this block in EVERY subagent prompt:
```
TOKEN MEASUREMENT (required):
Session ID: {paste actual session ID from your CAT SESSION INSTRUCTIONS}
Session file: /home/node/.config/claude/projects/-workspace/{SESSION_ID}.jsonl

On completion, measure tokens:
TOKENS=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
  select(. != null) | (.input_tokens + .output_tokens)] | add // 0' "$SESSION_FILE")
```

**Why explicit session ID?** Subagents don't receive CAT SESSION INSTRUCTIONS automatically.
Without the session ID, token measurement fails and reports show "NOT MEASURED" (M099, M109, M123).

**Token tracking requirements:**
- Track cumulative token usage across the ENTIRE session
- If context compaction occurs, PRESERVE pre-compaction token count
- Write TOTAL tokens (pre-compaction + post-compaction) to .completion.json
- Include: inputTokens, outputTokens, tokensUsed (total), compactionEvents count

**On completion**, write .completion.json with cumulative totals:
```json
{
  "status": "success|partial|failed",
  "tokensUsed": {CUMULATIVE_TOTAL},
  "inputTokens": {CUMULATIVE_INPUT},
  "outputTokens": {CUMULATIVE_OUTPUT},
  "compactionEvents": {COUNT},
  "summary": "..."
}
```

If compaction occurred, the pre-compaction tokens are NOT lost - they must be
preserved and added to post-compaction usage for accurate reporting.

**Context Limit Enforcement (A018):**

**MANDATORY: Validate task size BEFORE spawning subagent.**

```bash
# Values from agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=...
SOFT_TARGET_PCT=...
HARD_LIMIT_PCT=...
SOFT_TARGET=$((CONTEXT_LIMIT * SOFT_TARGET_PCT / 100))
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))
```

**Limit Hierarchy:**

| Limit | Percentage | Tokens (200K) | Purpose |
|-------|------------|---------------|---------|
| Soft target | 40% | 80,000 | Recommended task size for optimal quality |
| Hard limit | 80% | 160,000 | Maximum allowed - MANDATORY decomposition above this |
| Context limit | 100% | 200,000 | Absolute ceiling - compaction occurs |

**Pre-Spawn Validation Requirement:**

BEFORE spawning ANY subagent, the main agent MUST:

1. Calculate estimated tokens for the task (from analyze_task_size)
2. Use fixed HARD_LIMIT: 160,000 tokens (80% of 200K)
3. Compare estimate against hard limit:
   - If estimate >= HARD_LIMIT: **MANDATORY decomposition** (do NOT spawn)
   - If estimate > soft target but < hard limit: Recommend decomposition (optional)
   - If estimate <= soft target: Proceed with spawn

```bash
# Pre-spawn validation
# Values from agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=...
HARD_LIMIT_PCT=...
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))
SOFT_TARGET=$((CONTEXT_LIMIT * 40 / 100))

if [ "${ESTIMATED_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "ERROR: Task estimate (${ESTIMATED_TOKENS}) exceeds hard limit (${HARD_LIMIT})"
  echo "MANDATORY: Decompose task before spawning. Use /cat:decompose-task"
  exit 1
fi
```

**Post-Execution Limit Check:**

After subagent completes (in collect_results), verify actual usage:

```bash
ACTUAL_TOKENS={from .completion.json}
if [ "${ACTUAL_TOKENS}" -ge "${HARD_LIMIT}" ]; then
  echo "EXCEEDED: Subagent used ${ACTUAL_TOKENS} tokens (hard limit: ${HARD_LIMIT})"
  # Trigger learn-from-mistakes with A018 reference
fi
```

**Skill Delegation Requirement (M264):**

When delegating a skill to a subagent, the subagent MUST:
1. Invoke the exact same skill (not apply its "principles" manually)
2. Return the skill's outputs back to the parent agent

```
# ❌ WRONG - Describes principles instead of invoking skill
"Apply the skill's principles: [manual steps]..."

# ✅ CORRECT - Requires skill invocation and output reporting
"Invoke /cat:{skill-name} and return its output to the parent agent."
```

**Why:** Skills contain validation workflows and produce specific outputs. Manual application
bypasses validation and loses outputs the parent agent needs to verify success.

**Skill Postcondition Reporting (M258):**

When delegating a skill (like /cat:shrink-doc) that has postconditions, the prompt MUST require
reporting those postconditions. The main agent cannot verify postconditions were met without
explicit reporting.

**MANDATORY for skill delegation:**
```
POSTCONDITION REPORTING (required for skills with validation):
When executing /cat:{skill-name}, you MUST report:
- The validation score returned by the skill PER FILE (not aggregated)
- Whether the postcondition was met (e.g., score = 1.0)
- If postcondition failed, what was the actual value

FOR BATCH OPERATIONS (multiple files):
Return a table with per-file scores - do NOT summarize as "all files passed":

| File | Score | Status |
|------|-------|--------|
| path/to/file1.md | 1.0 | PASS |
| path/to/file2.md | 1.0 | PASS |

Example for /cat:shrink-doc (single file):
  Report: execution_equivalence_score=1.0, met_threshold=true

Example for /cat:shrink-doc (batch):
  | File | execution_equivalence_score | Status |
  |------|----------------------------|--------|
  | commands/add.md | 1.0 | PASS |
  | commands/work.md | 1.0 | PASS |
```

**Why per-file reporting?** Aggregate statements like "all files scored 1.0" are unverifiable.
Per-file tables provide evidence that each file was individually validated.

**Parent Agent Responsibility (M265):**
When presenting approval gate after batch skill delegation, the main agent MUST:
1. Include the per-file postcondition table from subagent output
2. NOT summarize as "all passed" - show each file's score
3. If subagent returned aggregate only, note this as incomplete reporting

**Why explicit reporting?** Without requiring postcondition values in the response, the main agent
has no evidence that the skill's validation workflow actually ran. Claims like "all files maintain
execution equivalence (score 1.0)" become unverifiable assertions.

| Skill | Postcondition | Required Report |
|-------|---------------|-----------------|
| /cat:shrink-doc | execution_equivalence_score = 1.0 | Per-file score table (batch) or single score |
| /cat:compare-docs | semantic comparison complete | Score and component breakdown |
| /cat:stakeholder-review | all stakeholders approve | Individual stakeholder verdicts |

**Verification:**

Before invoking Task tool, confirm:

| Checklist Item | Required For | Mistake Ref |
|----------------|--------------|-------------|
| STATE.md path specified | All implementation tasks | M076, M085 |
| Resolution field mentioned | All implementation tasks | M092 |
| CRITICAL PROHIBITIONS block | All tasks | A008 |
| Parser test style notes | Parser tasks | M079 |
| Exact code examples | Non-trivial changes | M062 |
| Fail-fast conditions | All tasks | spawn-subagent core |
| **Session ID in prompt** | All tasks | A017, M099, M109, M123 |
| Token measurement instructions | All tasks | spawn-subagent core |
| **Pre-spawn limit validation** | All tasks | A018 |
| **Skill postcondition reporting** | Skill delegation tasks | M258 |
| **Skill invocation (not principles)** | Skill delegation tasks | M264 |
| **Per-file scores for batch ops** | Batch skill delegation | M265 |

**Anti-pattern:** Spawning subagent without reviewing this checklist against your prompt.

**Anti-pattern (M265):** Summarizing batch postconditions as "all passed" instead of per-file table.

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

**Progress Output (MANDATORY):**

Display spawning progress using visible feedback symbols:

**At spawn start:**
```
◆ Spawning subagent: {task-id}...
  → Worktree: {worktree-path}
  → Branch: {branch-name}
```

**On successful launch:**
```
✓ Subagent launched: {subagent-id}
  → Session: {session-id}
  → Estimated tokens: {N}K
```

**On failure:**
```
✗ Spawn failed: {error-reason}
  → {specific error details}
```

These symbols match the phase-based progress format used in `/cat:work`.

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

# Verify task lock is held (should be acquired by work)
TASK_ID="${MAJOR}.${MINOR}-${TASK_NAME}"
LOCK_STATUS=$("${CLAUDE_PLUGIN_ROOT}/scripts/issue-lock.sh" check "${CLAUDE_PROJECT_DIR}" "$TASK_ID" 2>/dev/null || echo '{}')
if ! echo "$LOCK_STATUS" | jq -e '.locked == true' > /dev/null 2>&1; then
  echo "WARNING: Task lock not held. Acquire lock via work first."
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
echo "${CLAUDE_SESSION_ID}" > "${WORKTREE_PATH}/.session_id"

# Session file location (for reference)
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"
```

### 5. Launch Subagent

**Use the Task tool** to spawn subagents - do NOT use raw CLI commands:

```
Task tool invocation:
  description: "Execute parser task"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute PLAN.md at ${TASK_PLAN}.

    WORKING DIRECTORY: ${WORKTREE_PATH}

    CRITICAL REQUIREMENTS (enforced by hooks):
    - Decompose code instead of adding PMD suppression annotations
    - Use git merge --ff-only for linear history
    - Include tests for bugfixes in SAME commit as the fix

    VERIFICATION:
    1. Run tests: ./mvnw test -pl parser
    2. Run style checks: ./mvnw checkstyle:check pmd:check -pl parser
    3. All must pass

    FAIL-FAST CONDITIONS:
    - If build fails, report BLOCKED with error
    - Do NOT attempt workarounds

    COMMIT:
    After verification passes:
    git add <files>
    git commit -m "feature: description"

    ON COMPLETION: Report summary of changes made.
```

**Why Task tool instead of CLI:**
- Task tool manages subagent lifecycle automatically
- Provides proper context inheritance
- Returns results directly to parent agent
- No need for .completion.json markers or session file parsing

### 6. Record Spawn in Parent STATE.md

Update parent's tracking:

```yaml
subagents:
  - id: a1b2c3d4
    task: 1.2-implement-parser
    worktree: .worktrees/1.2-implement-parser-sub-a1b2c3d4
    branch: 1.2-implement-parser-sub-a1b2c3d4
    session: ${CLAUDE_SESSION_ID}
    spawned_at: 2026-01-10T14:30:00Z
    status: running
```

## Examples

### Basic Spawn

```bash
# Parent agent: First create worktree
TASK="1.2-implement-parser"
UUID="a1b2c3d4"
WORKTREE=".worktrees/${TASK}-sub-${UUID}"

git worktree add -b "${TASK}-sub-${UUID}" "${WORKTREE}" HEAD
```

Then use the Task tool to launch the subagent:

```
Task tool invocation:
  description: "Execute parser task"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Execute task 1.2 PLAN.md.

    WORKING DIRECTORY: .worktrees/1.2-implement-parser-sub-a1b2c3d4

    VERIFICATION:
    1. Run tests
    2. Run style checks

    ON COMPLETION: Report summary.
```

### Spawn for Complex Implementation

```
Task tool invocation:
  description: "Implement feature X"
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Implement feature X following PLAN.md.

    WORKING DIRECTORY: ${WORKTREE_PATH}

    CRITICAL REQUIREMENTS:
    - Decompose code instead of adding PMD suppression annotations
    - Include tests in SAME commit as implementation

    EXACT CHANGES:
    1. Create src/Feature.java with: [code listing]
    2. Create test/FeatureTest.java with: [test code]

    VERIFICATION:
    1. ./mvnw test -Dtest=FeatureTest
    2. ./mvnw checkstyle:check pmd:check

    FAIL-FAST: If any check fails, report BLOCKED

    COMMIT: git commit -m "feature: add X"
```

## Anti-Patterns

### Main agent makes all decisions before spawning

```
# ❌ WRONG - Requires subagent to make decisions
Task prompt: "Implement error handling for the parser. Choose appropriate exception types."

# ✅ CORRECT - All decisions made by main agent
Task prompt: |
  Add error handling to Parser.java:
  - Line 45: wrap in try-catch, throw ParseException("Invalid token at position " + pos)
  - Line 72: add null check, throw IllegalArgumentException("Input cannot be null")
  - All exceptions must include position information for debugging
```

### Always provide concrete PLAN.md reference

```
# ❌ WRONG - Vague instructions
Task prompt: "Work on the parser"

# ✅ CORRECT - Concrete plan reference
Task prompt: |
  Execute PLAN.md at .claude/cat/tasks/1.2-implement-parser/PLAN.md.
  WORKING DIRECTORY: .worktrees/1.2-implement-parser-sub-a1b2c3d4
```

### Provide explicit code examples for required changes

When specific API usage or patterns are required, provide explicit before/after code examples:

```
# ❌ WRONG - Vague instruction, subagent may find different solution
Task prompt: "Remove the unnecessary cast in LexerTest.java"

# ✅ CORRECT - Explicit code example showing expected change
Task prompt: |
  Change LexerTest.java line 625:
    FROM: requireThat(token.text() == token.decodedText(), "sameInstance").isTrue();
    TO:   requireThat(token.text(), "token.text()").isReferenceEqualTo(token.decodedText(), "token.decodedText()");
```

**Why**: Subagents optimize for passing tests/builds. Without explicit examples, they may find alternative
solutions (e.g., @SuppressWarnings) that technically work but don't match the intended approach.

### Require manual derivation of test expected values

For parser/test tasks, include manual derivation requirement in prompt:

```
# ❌ WRONG - Missing test derivation guidance
Task prompt: "Add parser tests for new feature"

# ✅ CORRECT - Explicit manual derivation requirement
Task prompt: |
  Add parser tests for new feature.

  CRITICAL: Test expected values MUST be manually derived:
  1. Analyze source string character by character
  2. Determine expected node types from Java grammar
  3. Use (0, 0) placeholders for positions initially
  4. VERIFY actual positions are correct before updating expected values
  5. Always verify before copying actual output as expected values
```

**Why**: Subagents may use placeholder technique incorrectly - copying actual output without verification
creates tests that pass but don't validate correctness.

### Require parser tests to verify AST structure

Parser tests MUST verify AST structure, not just parsing success:

```
# ❌ WRONG - Only checks parsing succeeded
Task prompt: |
  Add tests for new parser feature.
  @Test
  public void testNewFeature()
  {
      try (Parser _ = parse(source))
      {
          // INADEQUATE - only checks parsing didn't throw
      }
  }

# ✅ CORRECT - Verifies expected AST structure
Task prompt: |
  Add tests for new parser feature.
  @Test
  public void testNewFeature()
  {
      try (Parser parser = parse(source);
          NodeArena expected = new NodeArena())
      {
          NodeArena actual = parser.getArena();
          // Build expected AST with exact node types and positions
          expected.allocateNode(NodeType.X, startPos, endPos);
          requireThat(actual, "actual").isEqualTo(expected);
      }
  }
```

**Why (M062)**: Tests that only check `isSuccess()` provide false confidence - parsing can succeed
but produce incorrect AST. The `isEqualTo(expected)` pattern catches structural errors.

### Sequence dependent tasks (wait for completion)

```
# ❌ WRONG - Spawning B that depends on A's output
Task tool: task-a
Task tool: task-b  # Depends on task-a!

# ✅ CORRECT - Wait for dependency
Task tool: task-a
# ... wait for task-a result ...
Task tool: task-b  # Now safe to spawn
```

### Track worktree location for cleanup

```bash
# ❌ WRONG - No tracking of worktree location
git worktree add somewhere

# ✅ CORRECT - Track for later cleanup
WORKTREE=".worktrees/${TASK}-sub-${UUID}"
git worktree add "${WORKTREE}"
# Record in STATE.md for merge-subagent skill
```

### Include STATE.md update in implementation commits (M076/M077)

Task STATE.md MUST be updated to `status: completed` in the SAME commit as implementation:

```
# ❌ WRONG - No STATE.md update in prompt
Task prompt: |
  Implement feature X.
  COMMIT: git commit -m "feature: add X"

# ✅ CORRECT - STATE.md update included in commit instructions
Task prompt: |
  Implement feature X.

  COMMIT (all in same commit):
  1. Update .claude/cat/issues/v0/v0.1/feature-x/STATE.md:
     - Set status: completed
     - Set progress: 100%
     - Add completed: {date}
  2. git add <implementation files> .claude/cat/issues/v0/v0.1/feature-x/STATE.md
  3. git commit -m "feature: add X"
```

**Why**: STATE.md tracks task lifecycle. Committing it separately creates incomplete history and
violates atomic task completion (M076). Main agent prompts must include explicit STATE.md instructions.

### Report back to parent agent for decomposition

Subagents should not spawn further subagents. If a task needs decomposition, report back to the
parent agent.

### Separate exploration tasks from implementation tasks

Subagents CAN explore/research, but must return findings for main agent to decide - not act on them.

```
# ❌ WRONG - Subagent explores AND decides what to do
Task prompt: "Find where authentication is handled and add rate limiting"

# ✅ CORRECT - Subagent explores, returns findings, main agent decides later
Task prompt: |
  Find all authentication entry points in the codebase.
  Return a list of:
  - File path and line number
  - Method signature
  - Current error handling approach

  FAIL-FAST: If you cannot locate authentication code within 10 minutes,
  report 'BLOCKED: Could not locate auth code' and stop.
  Do NOT implement anything - return findings only.

# ✅ ALSO CORRECT - Main agent already explored, provides exact instructions
Task prompt: |
  Add rate limiting to src/auth/AuthService.java:
  In the authenticate() method at line 45, add before the password check:
    if (rateLimiter.isRateLimited(username)) {
      throw new RateLimitExceededException(username);
    }
```

### Provide explicit verification steps with expected output

```
# ❌ WRONG - Subagent must judge "working correctly"
Task prompt: "Make sure the parser handles all edge cases correctly"

# ✅ CORRECT - Explicit verification steps
Task prompt: |
  Verify parser handles edge cases:
  1. Run: ./gradlew test --tests 'ParserEdgeCaseTest'
  2. All 12 tests must pass
  3. Run: ./scripts/parse-corpus.sh testdata/edge-cases/
  4. Output must show: 'Processed 47 files, 0 errors'

  FAIL-FAST: If any test fails, report BLOCKED with output. Do NOT fix.
```

### Use fail-fast (report BLOCKED, let main agent decide)

```
# ❌ WRONG - Fallback involves decisions
Task prompt: "Try to use the new API. If it doesn't work, fall back to the legacy API."

# ✅ CORRECT - Fail-fast, let main agent decide
Task prompt: |
  Use the new API at src/api/v1/Client.java.

  FAIL-FAST:
  - If new API returns errors, report BLOCKED with error details
  - Do NOT fall back to legacy API
  - Do NOT try alternative approaches
  - Return status for main agent to decide next steps
```

**Why**: Choosing between approaches is a decision. Decisions require user oversight.

### Verify subagent findings through delegation, not direct investigation (M147)

When a subagent returns findings (especially exploration results like "DUPLICATE" or "NOT FOUND"),
the main agent must NOT investigate directly:

```
# ❌ WRONG - Main agent investigates subagent findings directly
Subagent returns: "DUPLICATE: fix already exists in commit c2da15e"
Main agent: "Let me verify by reading ExpressionParser.java..."
Main agent: "Let me run the test to confirm..."

# ✅ CORRECT - Spawn verification subagent if uncertain
Subagent returns: "DUPLICATE: fix already exists in commit c2da15e"
Main agent options:
  1. ACCEPT finding and proceed with appropriate workflow (e.g., mark task as duplicate)
  2. SPAWN a verification subagent with specific questions:
     Task prompt: |
       Verify that fix for lambda arrow in parenthesized context exists:
       1. Check commit c2da15e7 - does it modify ExpressionParser.java?
       2. Run test: ./mvnw test -Dtest="LambdaArrowEdgeCaseParserTest#shouldParseLambdaAfterMethodReferenceWithTrailingComments"
       3. Report: VERIFIED or NOT_VERIFIED with evidence
```

**Why this matters:**
- Reading files and running tests to verify subagent findings violates delegation boundaries
- If you distrust subagent findings, the correct response is structured re-verification, not ad-hoc investigation
- Direct investigation pattern ("Let me verify by reading the code myself") bypasses the subagent architecture

**Decision tree for subagent findings:**
1. Is the finding clear and actionable? → Accept and proceed with appropriate workflow
2. Is there uncertainty about accuracy? → Spawn verification subagent with specific verification steps
3. NEVER: Read code directly, run commands directly, or otherwise investigate outside the subagent framework

## Expanded Exploration Subagent

The exploration subagent handles three phases internally, hiding noisy tool calls from the user
and returning a structured JSON result for clean display.

### Three Phases

| Phase | Responsibilities | Output |
|-------|------------------|--------|
| **Preparation** | Read PLAN.md, analyze task size, create worktree | `preparation` object with estimate and worktree path |
| **Exploration** | Search codebase, find relevant code, check duplicates | `findings` object with locations and patterns |
| **Verification** | Verify findings exist, run preliminary tests, confirm state | `verification` object with validation results |

### Phase Details

**Phase 1: Preparation**

The exploration subagent performs task setup that would otherwise expose Bash/Read calls to user:

1. Read task PLAN.md to understand scope
2. Analyze task size using the estimation factors:
   - Files to create: 5K tokens each
   - Files to modify: 3K tokens each
   - Test files: 4K tokens each
   - Steps in PLAN.md: 2K tokens each
   - Exploration needed: +10K tokens if uncertain
3. Compare estimate against context threshold (contextLimit × targetContextUsage)
4. Create worktree and branch if task proceeds

**Phase 2: Exploration**

Standard exploration responsibilities:

1. Search codebase for relevant patterns
2. Identify files to modify and their locations
3. Check for duplicate functionality
4. Map dependencies and impacts
5. Note any blockers or concerns

**Phase 3: Verification**

Post-exploration validation before returning to main agent:

1. Verify all reported file paths exist
2. Confirm code patterns mentioned are accurate
3. Run any preliminary checks (syntax, imports)
4. Validate that findings are complete (no gaps)

### Structured JSON Return Format

The exploration subagent MUST return structured JSON for clean main agent display:

```json
{
  "status": "READY|OVERSIZED|DUPLICATE|BLOCKED",
  "preparation": {
    "estimatedTokens": 45000,
    // See agent-architecture.md § Context Limit Constants for context limits
    "percentOfThreshold": 56,
    "worktreePath": "/workspace/.worktrees/1.0-parse-lambdas",
    "branch": "1.0-parse-lambdas"
  },
  "findings": {
    "filesToModify": [
      {"path": "src/Parser.java", "lines": "145-200", "reason": "Add lambda parsing"},
      {"path": "src/Lexer.java", "lines": "50-60", "reason": "Add arrow token"}
    ],
    "filesToCreate": [
      {"path": "src/LambdaNode.java", "reason": "AST node for lambdas"}
    ],
    "patterns": ["Visitor pattern for AST", "Recursive descent parsing"],
    "duplicateCheck": "NOT_DUPLICATE",
    "blockers": []
  },
  "verification": {
    "allPathsExist": true,
    "patternsConfirmed": true,
    "preliminaryChecks": "PASSED",
    "notes": []
  }
}
```

### Status Values

| Status | Meaning | Main Agent Action |
|--------|---------|-------------------|
| `READY` | Task within threshold, ready to proceed | Continue to approach selection |
| `OVERSIZED` | Estimated tokens exceed threshold | Trigger decomposition |
| `DUPLICATE` | Task already implemented elsewhere | Mark as duplicate, skip |
| `BLOCKED` | Cannot proceed (missing deps, etc.) | Present blocker to user |

### Anti-Patterns

```
# ❌ WRONG - Main agent does preparation work inline
Main agent: "Let me read the PLAN.md..."
Main agent: "Now let me calculate the task size..."
Main agent: "Creating worktree with git worktree add..."
# User sees all this noisy output!

# ✅ CORRECT - Exploration subagent handles it internally
Main agent: "Spawning exploration subagent..."
[Subagent runs preparation + exploration + verification internally]
Main agent receives JSON: {"status": "READY", "preparation": {...}, ...}
Main agent displays: "✓ Task size OK: ~45K tokens (56% of threshold)"
# User sees clean summary only!
```

```
# ❌ WRONG - Main agent investigates after exploration subagent returns
Exploration subagent returns: {"filesToModify": ["Parser.java"]}
Main agent: "Let me read Parser.java to understand the structure..."
# Violates M088 - main agent should NOT read source files!

# ✅ CORRECT - Trust exploration findings, pass to implementation
Exploration subagent returns: {"filesToModify": ["Parser.java", lines: "145-200"]}
Main agent: "Exploration complete. Spawning implementation subagent..."
# Main agent trusts findings, doesn't re-investigate
```

## Related Skills

- `cat:monitor-subagents` - Check status of spawned subagents
- `cat:collect-results` - Gather results when subagent completes
- `cat:merge-subagent` - Merge subagent work back to task branch
- `cat:parallel-execute` - Spawn multiple subagents concurrently
