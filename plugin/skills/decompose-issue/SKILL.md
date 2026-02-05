---
description: Split oversized issue into smaller issues with proper dependency management
user-invocable: false
---

<execution_context>

@${CLAUDE_PLUGIN_ROOT}/concepts/version-paths.md

</execution_context>

# Decompose Issue

## Purpose

Break down a issue that is too large for a single context window into smaller, manageable subtasks.
This is essential for CAT's proactive context management, allowing work to continue efficiently
when a issue exceeds safe context bounds.

## When to Use

- Token report shows issue approaching 40% threshold (80K tokens)
- Subagent has experienced compaction events
- PLAN.md analysis reveals issue is larger than expected
- Partial collection indicates significant remaining work
- Pre-emptive decomposition during planning phase

## Workflow

### 1. Analyze Current Issue Scope

**MANDATORY: Verify issue exists. FAIL immediately if not found.**

```bash
TASK_DIR=".claude/cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${TASK_NAME}"

# FAIL immediately if issue directory missing
if [ ! -d "$TASK_DIR" ]; then
  echo "ERROR: Issue directory not found at $TASK_DIR"
  echo "Cannot decompose a issue that doesn't exist."
  echo "FAIL immediately - do NOT attempt workarounds."
  exit 1
fi

# FAIL immediately if PLAN.md missing
if [ ! -f "${TASK_DIR}/PLAN.md" ]; then
  echo "ERROR: PLAN.md not found at ${TASK_DIR}/PLAN.md"
  echo "Cannot decompose a issue without a plan - create PLAN.md first."
  echo "FAIL immediately - do NOT attempt workarounds."
  exit 1
fi

# Read current PLAN.md
cat "${TASK_DIR}/PLAN.md"

# Read STATE.md for progress
cat "${TASK_DIR}/STATE.md"

# If subagent exists, check its progress
if [ -d ".worktrees/${ISSUE}-sub-${UUID}" ]; then
  # Review commits made
  cd ".worktrees/${ISSUE}-sub-${UUID}"
  git log --oneline origin/HEAD..HEAD
fi
```

### 2. Identify Logical Split Points

Analyze PLAN.md for natural boundaries:

**Good split points:**
- Between independent features
- Between layers (model, service, controller)
- Between read and write operations
- Between setup and implementation
- Between implementation and testing

**Poor split points:**
- Middle of a refactoring
- Between tightly coupled components
- In the middle of a transaction boundary

### 3. Create New Issue Directories

```bash
# Original issue: 1.2/implement-parser
# New issues: parser-lexer, parser-ast, parser-semantic (within same minor)

# Create directories for new issues
mkdir -p ".claude/cat/issues/v1/v1.2/parser-lexer"
mkdir -p ".claude/cat/issues/v1/v1.2/parser-ast"
mkdir -p ".claude/cat/issues/v1/v1.2/parser-semantic"
```

### 4. Create PLAN.md for Each New Issue

Each new issue gets its own focused PLAN.md:

```yaml
# 1.2a-parser-lexer/PLAN.md
---
issue: 1.2a-parser-lexer
parent: 1.2-implement-parser
sequence: 1 of 3
---

# Implement Parser Lexer

## Objective
Implement the lexical analysis phase of the parser.

## Scope
- Token definitions
- Lexer implementation
- Lexer unit tests

## Dependencies
- None (first in sequence)

## Deliverables
- src/parser/Token.java
- src/parser/Lexer.java
- test/parser/LexerTest.java
```

### 5. Define Dependencies Between New Issues

```yaml
# Dependency graph
dependencies:
  1.2a-parser-lexer: []  # No dependencies
  1.2b-parser-ast:
    - 1.2a-parser-lexer  # Depends on lexer
  1.2c-parser-semantic:
    - 1.2b-parser-ast    # Depends on AST
```

### 6. Update STATE.md Files

**Parent Issue Status Lifecycle (M263):**

When a issue is decomposed, the parent issue status follows this lifecycle:
1. `open` → `in-progress` (when decomposition starts)
2. Remains `in-progress` while subtasks execute
3. `in-progress` → `closed` (only when ALL subtasks are closed)

**INVALID:** Using `status: decomposed` - this is NOT a valid status value.
Valid values are: `open`, `in-progress`, `closed`, `blocked`.

Original issue STATE.md:

```markdown
# 1.2-implement-parser/STATE.md

- **Status:** in-progress
- **Progress:** 0%
- **Decomposed:** true
- **Decomposed At:** 2026-01-10T16:00:00Z
- **Reason:** Issue exceeded context threshold (85K tokens used)

## Decomposed Into
- 1.2a-parser-lexer
- 1.2b-parser-ast
- 1.2c-parser-semantic

## Progress Preserved
- Lexer implementation 80% complete in subagent work
- Will be merged to 1.2a branch
```

**Note:** Parent stays `in-progress` until ALL subtasks complete. Progress is calculated from subtask completion (e.g., 1/3 subtasks = 33%).

New issue STATE.md:

```markdown
# 1.2a-parser-lexer/STATE.md

- **Status:** open
- **Progress:** 0%
- **Created From:** 1.2-implement-parser
- **Inherits Progress:** true (will receive merge from parent subagent)
- **Dependencies:** []
```

### 7. Handle Existing Subagent Work

If decomposing due to subagent context limits:

```bash
# Collect partial results from subagent
collect-results "${SUBAGENT_ID}"

# Determine which new issue inherits the work
# Usually the first or most complete component

# Merge subagent work to appropriate new issue branch
git checkout "1.2a-parser-lexer"
git merge "${SUBAGENT_BRANCH}" -m "Inherit partial progress from decomposed parent"
```

### 8. Generate Parallel Execution Plan

**MANDATORY: Analyze dependencies and create sub-issue-based execution plan.**

After decomposition, determine which subtasks can run concurrently:

```yaml
# Dependency analysis
subtasks:
  - id: 1.2a-parser-lexer
    dependencies: []
    estimated_tokens: 25000
  - id: 1.2b-parser-ast
    dependencies: [1.2a-parser-lexer]
    estimated_tokens: 30000
  - id: 1.2c-parser-tests
    dependencies: []
    estimated_tokens: 20000

# Sub-issue-based parallel plan
parallel_execution_plan:
  sub_task_1:
    # Issues with no dependencies - can run concurrently
    issues: [1.2a-parser-lexer, 1.2c-parser-tests]
    max_concurrent: 2
    reason: "Both have no dependencies, can execute in parallel"

  sub_task_2:
    # Issues that depend on sub_task_1 completion
    issues: [1.2b-parser-ast]
    depends_on: [sub_task_1]
    reason: "Depends on 1.2a-parser-lexer from sub_task_1"

execution_order:
  1. Spawn subagents for sub_task_1 issues (parallel)
  2. Monitor and collect sub_task_1 results
  3. Merge sub_task_1 branches
  4. Spawn subagents for sub_task_2 issues (parallel)
  5. Monitor and collect sub_task_2 results
  6. Merge sub_task_2 branches
```

**Output parallel plan to STATE.md:**

```markdown
## Parallel Execution Plan

### Sub-issue 1 (Concurrent)
| Issue | Est. Tokens | Dependencies |
|------|-------------|--------------|
| 1.2a-parser-lexer | 25K | None |
| 1.2c-parser-tests | 20K | None |

### Sub-issue 2 (After Sub-issue 1)
| Issue | Est. Tokens | Dependencies |
|------|-------------|--------------|
| 1.2b-parser-ast | 30K | 1.2a-parser-lexer |

**Total sub-issues:** 2
**Max concurrent subagents:** 2
```

**Conflict detection for parallel issues:**

Ensure no parallel issues modify the same files:

```yaml
conflict_check:
  task_1: 1.2a-parser-lexer
    files: [src/parser/Lexer.java, test/parser/LexerTest.java]
  task_2: 1.2c-parser-tests
    files: [test/parser/ParserIntegrationTest.java]

  overlap: []  # No conflicts - safe to parallelize

  # If overlap exists:
  conflict_resolution:
    move_conflicting_task_to_next_sub_task: true
```

### 9. Update Original Issue for Decomposition

**STATE.md:** Keep status as `in-progress` (NOT `decomposed` - invalid status value per M263).

**PLAN.md:** Add decomposition metadata:

```bash
# Update original PLAN.md with decomposition info
echo "---
decomposed: true
decomposed_into: [1.2a, 1.2b, 1.2c]
parallel_plan: sub_task_1=[1.2a, 1.2c], sub_task_2=[1.2b]
---" >> "${TASK_DIR}/PLAN.md"

# Update STATE.md - status stays in-progress, add Decomposed field
# Parent transitions to 'closed' only when ALL subtasks complete
```

## Examples

### Pre-Planning Decomposition

When analyzing requirements reveals a issue is too large:

```yaml
# Original issue seemed manageable
issue: 1.5-implement-authentication

# Analysis reveals scope
components:
  - User model and repository
  - Password hashing service
  - JWT token generation
  - Login/logout endpoints
  - Session management
  - Password reset flow
  - Email verification

# Too many components - decompose before starting
decompose_to:
  - 1.5a-auth-user-model
  - 1.5b-auth-password-service
  - 1.5c-auth-jwt-tokens
  - 1.5d-auth-endpoints
  - 1.5e-auth-sessions
  - 1.5f-auth-password-reset
  - 1.5g-auth-email-verify
```

### Mid-Execution Decomposition

When subagent hits context limits:

```yaml
decomposition_trigger:
  issue: 1.3-implement-formatter
  subagent_tokens: 85000
  compaction_events: 1
  completed_work:
    - Basic formatter structure
    - Indentation handling
  remaining_work:
    - Line wrapping
    - Comment formatting
    - Multi-line string handling

decomposition_result:
  - issue: 1.3a-formatter-core
    inherits: subagent work
    status: nearly_complete
  - issue: 1.3b-formatter-wrapping
    status: ready
  - issue: 1.3c-formatter-comments
    status: ready
```

### Emergency Decomposition

When subagent is stuck or confused:

```yaml
emergency_decomposition:
  trigger: "Subagent making no progress for 30+ minutes"
  analysis: |
    Issue scope unclear, subagent attempting multiple
    approaches without success.

  action:
    - Collect any usable partial work
    - Re-analyze requirements
    - Create smaller, more specific issues
    - Add explicit acceptance criteria to each
```

## Anti-Patterns

### Split at logical boundaries

```yaml
# ❌ Splitting at arbitrary points
1.2a: "Lines 1-100 of Parser.java"
1.2b: "Lines 101-200 of Parser.java"

# ✅ Split at logical boundaries
1.2a: "Lexer component"
1.2b: "AST builder component"
```

### Model actual dependencies accurately

```yaml
# ❌ Treating all subtasks as independent
1.2a-parser-lexer: []
1.2b-parser-ast: []    # Actually needs lexer!
1.2c-parser-semantic: []  # Actually needs AST!

# ✅ Model actual dependencies
1.2a-parser-lexer: []
1.2b-parser-ast: [1.2a]
1.2c-parser-semantic: [1.2b]
```

### Preserve partial progress when decomposing

```yaml
# ❌ Starting fresh after decomposition
decompose_task "1.2-parser"
# Subagent work discarded!

# ✅ Preserve progress
collect_results "${SUBAGENT}"
decompose_task "1.2-parser"
merge_to_appropriate_subtask "${SUBAGENT_WORK}"
```

### Create meaningful chunks (avoid over-decomposition)

```yaml
# ❌ Too granular
1.2a: "Define Token class"
1.2b: "Define TokenType enum"
1.2c: "Implement nextToken method"
1.2d: "Implement peek method"
# ...20 more tiny issues

# ✅ Meaningful chunks
1.2a: "Implement Lexer (tokens, types, core methods)"
1.2b: "Implement Parser (AST, expressions, statements)"
```

### Always update orchestration when decomposing

```yaml
# ❌ Create subtasks, forget to track
mkdir 1.2a 1.2b 1.2c
# Parent doesn't know about them!

# ✅ Full state update
create_subtasks "1.2a" "1.2b" "1.2c"
update_parent_state "decomposed" "1.2a,1.2b,1.2c"
update_orchestration_plan
```

### Distinguish runtime dependencies from extraction dependencies

For code extraction/refactoring issues, runtime method calls are NOT issue dependencies.

```yaml
# ❌ Confusing runtime calls with issue dependencies
# "parseUnary calls parsePostfix, so extract-unary must run before extract-postfix"
subtasks:
  extract-unary: []
  extract-postfix: [extract-unary]  # Wrong! Just copying code, not executing it

# ✅ Extraction issues that write to different sections can run in parallel
# Methods call each other at RUNTIME, but extraction is just copying text
subtasks:
  extract-unary: [setup-interface]      # Both depend on interface setup
  extract-postfix: [setup-interface]    # Both can run concurrently

# Key insight: "Does method A call method B?" is irrelevant for extraction order.
# Ask instead: "Do both issues write to the same file section?"
# If writing to different sections of the same file → can parallelize
# Only the final integration issue depends on all extractions completing
```

**Dependency analysis questions:**
1. Does issue B need OUTPUT from issue A? (Real dependency)
2. Does issue B just reference CODE that issue A also references? (Not a dependency)
3. Are both issues copying different methods to the same target file? (Parallelizable with merge)

## Related Skills

- `cat:token-report` - Triggers decomposition decisions
- `cat:collect-results` - Preserves progress before decomposition
- `cat:spawn-subagent` - Launches work on decomposed issues
- `cat:parallel-execute` - Can run independent subtasks concurrently
