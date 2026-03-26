<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Decompose Issue

See `${CLAUDE_PLUGIN_ROOT}/concepts/version-paths.md` for version path conventions used throughout this skill.
See `${CLAUDE_PLUGIN_ROOT}/concepts/execution-model.md` for the full execution hierarchy, wave definitions, and
sub-issue decomposition context.

## Purpose

Break down a issue that is too large for a single context window into smaller, manageable sub-issues.
This is essential for CAT's proactive context management, allowing work to continue efficiently
when a issue exceeds safe context bounds.

## When to Use

- Token report shows issue approaching 40% threshold (80K tokens)
- Subagent has experienced compaction events
- plan.md analysis reveals issue is larger than expected
- Partial collection indicates significant remaining work
- Pre-emptive decomposition during planning phase

## Naming Convention

**CRITICAL: All sub-issue references must use fully-qualified names.**

A fully-qualified issue name matches the pattern `\d+\.\d+[a-z]?-\S+` (e.g., `2.1-rename-config-java-core`).

When decomposing parent issue `{VERSION_PREFIX}{parent-bare-name}` (e.g., `2.1-rename-cat-config-files`):
- The version prefix is `2.1-` (extracted from the parent issue ID)
- Sub-issue qualified names = `{VERSION_PREFIX}{sub-issue-bare-name}` (e.g., `2.1-rename-config-java-core`)
- Directory names = `{sub-issue-bare-name}` (e.g., `rename-config-java-core`)

**NEVER use bare names in the "Decomposed Into" section.** Bare names (e.g., `rename-config-java-core`) cause
`allSubissuesClosed()` to silently skip the entry and incorrectly conclude all sub-issues are closed.

## Workflow

### 1. Analyze Current Issue Scope

**MANDATORY: Verify issue exists. FAIL immediately if not found.**

```bash
ISSUE_DIR=".cat/issues/v${MAJOR}/v${MAJOR}.${MINOR}/${ISSUE_NAME}"

# FAIL immediately if issue directory missing
if [ ! -d "$ISSUE_DIR" ]; then
  echo "ERROR: Issue directory not found at $ISSUE_DIR"
  echo "Cannot decompose a issue that doesn't exist."
  echo "FAIL immediately - do NOT attempt workarounds."
  exit 1
fi

# FAIL immediately if plan.md missing
if [ ! -f "${ISSUE_DIR}/plan.md" ]; then
  echo "ERROR: plan.md not found at ${ISSUE_DIR}/plan.md"
  echo "Cannot decompose a issue without a plan - create plan.md first."
  echo "FAIL immediately - do NOT attempt workarounds."
  exit 1
fi

# Read current plan.md
cat "${ISSUE_DIR}/plan.md"

# Read index.json for progress
cat "${ISSUE_DIR}/index.json"

# If subagent exists, check its progress
source "${CLAUDE_PLUGIN_ROOT}/scripts/cat-env.sh"
if [ -d "${WORKTREES_DIR}/${ISSUE}-sub-${UUID}" ]; then
  # Review commits made
  (cd "${WORKTREES_DIR}/${ISSUE}-sub-${UUID}" && git log --oneline origin/HEAD..HEAD)
fi
```

### 2. Identify Logical Split Points

Analyze plan.md for natural boundaries:

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
mkdir -p ".cat/issues/v1/v1.2/parser-lexer"
mkdir -p ".cat/issues/v1/v1.2/parser-ast"
mkdir -p ".cat/issues/v1/v1.2/parser-semantic"
```

### 4. Create plan.md for Each New Issue

Each new issue gets its own focused plan.md:

```yaml
# parser-lexer/plan.md  (directory name is bare; qualified ID is 1.2-parser-lexer)
---
issue: 1.2-parser-lexer
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
# Dependency graph (use fully-qualified names: VERSION_PREFIX + bare-name)
dependencies:
  1.2-parser-lexer: []  # No dependencies
  1.2-parser-ast:
    - 1.2-parser-lexer  # Depends on lexer
  1.2-parser-semantic:
    - 1.2-parser-ast    # Depends on AST
```

### 6. Update index.json Files

**Parent Issue Status Lifecycle:**

When a issue is decomposed, the parent issue status follows this lifecycle:
1. `open` → `in-progress` (when decomposition starts)
2. Remains `in-progress` while sub-issues execute
3. `in-progress` → `closed` (only when ALL sub-issues are implemented and tested AND parent acceptance criteria are met)

**Closing Decomposed Parents:**

When /cat:work selects a decomposed parent (all sub-issues closed), verify parent acceptance criteria before closure:

1. **Read parent plan.md** - Review all acceptance criteria listed
2. **Verify each criterion** - Check codebase/tests to confirm each criterion is actually satisfied
3. **If all criteria met** - Update parent index.json to closed/100%
4. **If criteria NOT met** - Create new sub-issue for remaining work, keep parent open

**Why sub-issues implemented and tested ≠ parent complete:** Sub-issues implement their individual scopes, but may not
cover everything in parent acceptance criteria. Example: sub-issues create Java equivalents, but none remove Python
originals.

**INVALID:** Using `status: decomposed` - this is NOT a valid status value.
Valid values are: `open`, `in-progress`, `closed`, `blocked`.

Original issue index.json:

```markdown
# 1.2-implement-parser/index.json

- **Status:** in-progress
- **Progress:** 0%
- **Decomposed:** true
- **Decomposed At:** 2026-01-10T16:00:00Z
- **Reason:** Issue exceeded context threshold (85K tokens used)

## Decomposed Into

<!-- IMPORTANT: Use fully-qualified names (VERSION_PREFIX + bare-name). -->
<!-- For parent "1.2-implement-parser", VERSION_PREFIX="1.2-", so entries are: -->
- 1.2-parser-lexer
- 1.2-parser-ast
- 1.2-parser-semantic

## Progress Preserved
- Lexer implementation 80% complete in subagent work
- Will be merged to 1.2-parser-lexer branch
```

**Note:** Parent stays `in-progress` until ALL sub-issues are implemented and tested. Progress is calculated from sub-issue
completion (e.g., 1/3 sub-issues = 33%).

New issue index.json:

```markdown
# parser-lexer/index.json  (directory name is bare; qualified ID is 1.2-parser-lexer)

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
git checkout "1.2-parser-lexer"
git merge "${SUBAGENT_BRANCH}" -m "Inherit partial progress from decomposed parent"
```

### 8. Generate Parallel Execution Plan

**MANDATORY: Analyze dependencies and create wave-based execution plan.**

After decomposition, determine which sub-issues can run concurrently and organize them into waves. A wave is a
dependency-ordered group of sub-issues that can execute in parallel. All sub-issues in Wave N must complete before any
sub-issue in Wave N+1 can begin.

```yaml
# Dependency analysis (use fully-qualified names: VERSION_PREFIX + bare-name)
sub-issues:
  - id: 1.2-parser-lexer
    dependencies: []
    estimated_tokens: 25000
  - id: 1.2-parser-ast
    dependencies: [1.2-parser-lexer]
    estimated_tokens: 30000
  - id: 1.2-parser-tests
    dependencies: []
    estimated_tokens: 20000

# Wave-based parallel plan
parallel_execution_plan:
  wave_1:
    # Issues with no dependencies - can run concurrently
    issues: [1.2-parser-lexer, 1.2-parser-tests]
    max_concurrent: 2
    reason: "Both have no dependencies, can execute in parallel"

  wave_2:
    # Issues that depend on wave_1 completion
    issues: [1.2-parser-ast]
    depends_on: [wave_1]
    reason: "Depends on 1.2-parser-lexer from wave_1"

execution_order:
  1. Spawn subagents for wave_1 issues (parallel)
  2. Monitor and collect wave_1 results
  3. Merge wave_1 branches
  4. Spawn subagents for wave_2 issues (parallel)
  5. Monitor and collect wave_2 results
  6. Merge wave_2 branches
```

**Output parallel plan to index.json:**

```markdown
## Parallel Execution Plan

### Wave 1 (Concurrent)
| Issue | Est. Tokens | Dependencies |
|------|-------------|--------------|
| 1.2-parser-lexer | 25K | None |
| 1.2-parser-tests | 20K | None |

### Wave 2 (After Wave 1)
| Issue | Est. Tokens | Dependencies |
|------|-------------|--------------|
| 1.2-parser-ast | 30K | 1.2-parser-lexer |

**Total sub-issues:** 3
**Max concurrent subagents:** 2 (in wave 1)
```

**Conflict detection for parallel issues:**

Ensure no parallel issues within the same wave modify the same files:

```yaml
conflict_check:
  issue_1: 1.2-parser-lexer
    files: [src/parser/Lexer.java, test/parser/LexerTest.java]
  issue_2: 1.2-parser-tests
    files: [test/parser/ParserIntegrationTest.java]

  overlap: []  # No conflicts - safe to parallelize in same wave

  # If overlap exists:
  conflict_resolution:
    move_conflicting_issue_to_next_wave: true
```

### 9. Update Original Issue for Decomposition

**index.json:** Keep status as `in-progress` (NOT `decomposed` - invalid status value per M263).

**plan.md:** Add decomposition metadata:

```bash
# Update original plan.md with decomposition info
# IMPORTANT: Use fully-qualified names (VERSION_PREFIX + bare-name), not bare names
echo "---
decomposed: true
decomposedInto: [1.2-parser-lexer, 1.2-parser-ast, 1.2-parser-semantic]
parallel_plan: wave_1=[1.2-parser-lexer, 1.2-parser-semantic], wave_2=[1.2-parser-ast]
---" >> "${ISSUE_DIR}/plan.md"

# Update index.json - status stays in-progress, add Decomposed field
# Parent transitions to 'closed' only when ALL sub-issues are implemented and tested
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
# (qualified names = parent version prefix + bare name, e.g., 1.5- + auth-user-model)
decompose_to:
  - 1.5-auth-user-model
  - 1.5-auth-password-service
  - 1.5-auth-jwt-tokens
  - 1.5-auth-endpoints
  - 1.5-auth-sessions
  - 1.5-auth-password-reset
  - 1.5-auth-email-verify
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
  - issue: 1.3-formatter-core
    inherits: subagent work
    status: nearly_complete
  - issue: 1.3-formatter-wrapping
    status: ready
  - issue: 1.3-formatter-comments
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
1.2-part-one: "Lines 1-100 of Parser.java"
1.2-part-two: "Lines 101-200 of Parser.java"

# ✅ Split at logical boundaries
1.2-lexer: "Lexer component"
1.2-ast-builder: "AST builder component"
```

### Model actual dependencies accurately

```yaml
# ❌ Treating all sub-issues as independent
1.2-parser-lexer: []
1.2-parser-ast: []    # Actually needs lexer!
1.2-parser-semantic: []  # Actually needs AST!

# ✅ Model actual dependencies (use fully-qualified names in dependency lists)
1.2-parser-lexer: []
1.2-parser-ast: [1.2-parser-lexer]
1.2-parser-semantic: [1.2-parser-ast]
```

### Preserve partial progress when decomposing

```yaml
# ❌ Starting fresh after decomposition
decompose_issue "1.2-parser"
# Subagent work discarded!

# ✅ Preserve progress
collect_results "${SUBAGENT}"
decompose_issue "1.2-parser"
merge_to_appropriate_sub-issue "${SUBAGENT_WORK}"
```

### Create meaningful chunks (avoid over-decomposition)

```yaml
# ❌ Too granular
1.2-define-token: "Define Token class"
1.2-define-tokentype: "Define TokenType enum"
1.2-implement-next-token: "Implement nextToken method"
1.2-implement-peek: "Implement peek method"
# ...20 more tiny issues

# ✅ Meaningful chunks
1.2-lexer: "Implement Lexer (tokens, types, core methods)"
1.2-parser: "Implement Parser (AST, expressions, statements)"
```

### Always update orchestration when decomposing

```yaml
# ❌ Create sub-issues, forget to track
mkdir parser-lexer parser-ast parser-semantic
# Parent doesn't know about them! Also used bare names in index.json!

# ✅ Full state update (use qualified names: VERSION_PREFIX + bare-name)
create_sub-issues "parser-lexer" "parser-ast" "parser-semantic"
# In parent index.json "Decomposed Into", list qualified names:
# - 1.2-parser-lexer
# - 1.2-parser-ast
# - 1.2-parser-semantic
update_orchestration_plan
```

### Distinguish runtime dependencies from extraction dependencies

For code extraction/refactoring issues, runtime method calls are NOT issue dependencies.

```yaml
# ❌ Confusing runtime calls with issue dependencies
# "parseUnary calls parsePostfix, so extract-unary must run before extract-postfix"
sub-issues:
  1.2-extract-unary: []
  1.2-extract-postfix: [1.2-extract-unary]  # Wrong! Just copying code, not executing it

# ✅ Extraction issues that write to different sections can run in parallel
# Methods call each other at RUNTIME, but extraction is just copying text
sub-issues:
  1.2-extract-unary: [1.2-setup-interface]      # Both depend on interface setup
  1.2-extract-postfix: [1.2-setup-interface]    # Both can run concurrently

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
- `cat:collect-results-agent` - Preserves progress before decomposition
- `cat:spawn-subagent` - Launches work on decomposed issues
- `cat:parallel-execute` - Can run independent sub-issues concurrently
