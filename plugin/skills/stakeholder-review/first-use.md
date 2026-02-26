<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill: stakeholder-review

Multi-perspective stakeholder review gate for implementation quality assurance.

## Invocation Restriction

**MAIN AGENT ONLY**: This skill spawns subagents internally. It CANNOT be invoked by
a subagent (subagents cannot spawn nested subagents or invoke skills).

If you need this skill's functionality within delegated work:
1. Main agent invokes this skill directly
2. Pass results to the implementation subagent
3. See: plugin/skills/delegate/SKILL.md § "Model Selection for Subagents"

## Purpose

Run parallel stakeholder reviews of implementation changes to identify concerns from multiple
perspectives (architecture, security, design, testing, performance, deployment) before user approval.

**Holistic Review Approach:** Stakeholders receive full file context (not just diffs) to evaluate how
changes affect the overall codebase. This enables reviewers to catch:
- Accumulated technical debt patterns
- Inconsistencies with existing code
- Architecture violations that only appear in full context
- Testing gaps relative to surrounding code

## Arguments Format

Positional space-separated arguments:

```
<issue_id> <worktree_path> <verify_level> <commits_compact>
```

| Position | Name | Example |
|----------|------|---------|
| 1 | issue_id | `2.1-issue-name` |
| 2 | worktree_path | `/workspace/.claude/cat/worktrees/2.1-issue-name` |
| 3 | verify_level | `quick` or `changed` or `all` |
| 4 | commits_compact | `hash:type,hash:type` (e.g., `abc123:bugfix,def456:test`) |

Parse from ARGUMENTS:
```bash
read ISSUE_ID WORKTREE_PATH VERIFY_LEVEL COMMITS_COMPACT <<< "$ARGUMENTS"
```

Commits can be expanded by splitting on `,` then `:` to get hash and type.
Commit messages are available via `git log` in the worktree if needed.

## When to Use

- After implementation phase completes in `/cat:work`
- Before the user approval gate
- When significant code changes need multi-perspective validation

## Stakeholders

| Stakeholder | Reference | Focus |
|-------------|-----------|-------|
| requirements | @agents/stakeholder-requirements.md | Requirement satisfaction verification |
| architecture | @agents/stakeholder-architecture.md | System design, module boundaries, APIs |
| security | @agents/stakeholder-security.md | Vulnerabilities, input validation |
| design | @agents/stakeholder-design.md | Code quality, complexity, duplication |
| testing | @agents/stakeholder-testing.md | Test coverage, edge cases |
| performance | @agents/stakeholder-performance.md | Efficiency, resource usage |
| deployment | @agents/stakeholder-deployment.md | CI/CD, build systems, release readiness |
| ux | @agents/stakeholder-ux.md | Usability, accessibility, interaction design |
| business | @agents/stakeholder-business.md | Customer value, competitive positioning, market readiness |
| legal | @agents/stakeholder-legal.md | Licensing, compliance, IP, data privacy |

## Progress Output

This skill orchestrates multiple stakeholder reviewers as subagents. Each reviewer's
internal tool calls are invisible - users see only the Task tool invocations and
aggregated results.

**On start:**
```
◆ Running stakeholder review...
```

**During execution:** Task tool invocations appear for each reviewer spawn, but their
internal file reads and analysis are invisible.

**On completion:**
```
✓ Review complete: {APPROVED|CONCERNS|REJECTED}
  → requirements: ✓
  → architecture: ✓
  → security: ⚠ 1 HIGH
  → testing: ✓
  → performance: ✓
```

The aggregated result provides all necessary information without exposing 50+ internal
tool calls from reviewers.

## Process

<step name="analyze_context">

**Context-Aware Stakeholder Selection**

Analyze issue context to determine which stakeholders are relevant, reducing token usage by skipping irrelevant
reviewers.

### Selection Algorithm

```
RESEARCH MODE (pre-implementation):
1. Start with base set: [requirements] (always included)
2. Detect issue type from PLAN.md or commit messages
3. Apply issue type inclusions/exclusions
4. Scan issue description/goal for keywords
5. Apply keyword inclusions
6. Check version PLAN.md for focus keywords
7. Apply version focus inclusions
8. Output: selected_stakeholders, skipped_with_reasons

REVIEW MODE (post-implementation):
1. Start with research mode selection
2. Get list of actually changed files
3. For each file-based override rule:
   - If condition matches, ADD stakeholder (even if context excluded it)
4. Output: final_stakeholders, skipped_with_reasons, overridden_stakeholders
```

### Issue Type Mappings

Detect issue type from PLAN.md `## Type` field or infer from commit messages/description:

| Issue Type | Include | Exclude |
|-----------|---------|---------|
| documentation | requirements | architecture, security, design, testing, performance, ux, business |
| refactor | architecture, design, testing | ux, business |
| bugfix | requirements, design, testing, security | business |
| performance | performance, architecture, testing | ux, business |

### Keyword Mappings

Scan issue description, goal, and PLAN.md for keywords:

| Keywords | Include |
|----------|---------|
| "license", "compliance", "legal" | legal |
| "UI", "frontend", "user interface" | ux |
| "API", "endpoint", "public" | architecture, security, business |
| "internal", "tooling", "CLI" | architecture, design (exclude ux, business) |
| "security", "auth", "permission" | security |
| "CI", "CD", "pipeline", "build", "deploy", "release", "migration" | deployment |

### Version Focus Mapping

Check version PLAN.md for strategic focus:

- If version PLAN.md mentions "commercialization" → include legal, business

### File-Based Overrides (Review Mode Only)

In review mode, file changes can override context exclusions:

| File Pattern | Add Stakeholder |
|--------------|-----------------|
| UI/frontend files (`**/ui/**`, `**/frontend/**`, `*.tsx`, `*.vue`) | ux |
| Security-sensitive files (`**/auth/**`, `**/permission/**`, `**/security/**`) | security |
| Test files (`*Test*`, `*Spec*`, `*_test*`) | testing |
| Algorithm-heavy files (sort, search, optimize, process) | performance |
| CI/CD files (`Dockerfile`, `*.yml` in `.github/`, `Jenkinsfile`, `*.yaml` pipeline) | deployment |
| Only .md files changed | requirements, design only |
| Only test files changed | testing, design only |

### User Override: Force Stakeholders

Users can force specific stakeholders by adding to issue PLAN.md:

```markdown
## Force Stakeholders
- ux
- legal
```

If `## Force Stakeholders` section exists, those stakeholders are ALWAYS included regardless of context analysis.

### Implementation

```bash
# Initialize base selection
SELECTED="requirements"
SKIPPED=""
OVERRIDDEN=""

# Read issue PLAN.md
ISSUE_PLAN=$(cat .claude/cat/issues/*/PLAN.md 2>/dev/null || echo "")

# Check for forced stakeholders
FORCED=$(echo "$ISSUE_PLAN" | sed -n '/## Force Stakeholders/,/^##/p' | grep '^ *-' | sed 's/^ *- *//')

# Detect issue type
ISSUE_TYPE=$(echo "$ISSUE_PLAN" | grep -E '^## Type' -A1 | tail -1 | tr '[:upper:]' '[:lower:]' || echo "")
if [[ -z "$ISSUE_TYPE" ]]; then
    # Infer from commit messages or issue name
    ISSUE_TYPE=$(git log -1 --pretty=%s 2>/dev/null | grep -oE '^(fix|feat|refactor|docs|perf)' | head -1)
    case "$ISSUE_TYPE" in
        docs) ISSUE_TYPE="documentation" ;;
        fix) ISSUE_TYPE="bugfix" ;;
        perf) ISSUE_TYPE="performance" ;;
    esac
fi

# Apply issue type mappings
case "$ISSUE_TYPE" in
    documentation)
        EXCLUDED="architecture security design testing performance ux business"
        ;;
    refactor)
        SELECTED="$SELECTED architecture design testing"
        EXCLUDED="ux business"
        ;;
    bugfix)
        SELECTED="$SELECTED design testing security"
        EXCLUDED="business"
        ;;
    performance)
        SELECTED="$SELECTED performance architecture testing"
        EXCLUDED="ux business"
        ;;
    *)
        # Default: include core technical reviewers
        SELECTED="$SELECTED architecture security design testing performance"
        EXCLUDED=""
        ;;
esac

# Scan for keywords in issue description
ISSUE_TEXT=$(echo "$ISSUE_PLAN" | tr '[:upper:]' '[:lower:]')

if echo "$ISSUE_TEXT" | grep -qE 'license|compliance|legal'; then
    SELECTED="$SELECTED legal"
fi
if echo "$ISSUE_TEXT" | grep -qE 'ui|frontend|user interface'; then
    SELECTED="$SELECTED ux"
fi
if echo "$ISSUE_TEXT" | grep -qE 'api|endpoint|public'; then
    SELECTED="$SELECTED architecture security business"
fi
if echo "$ISSUE_TEXT" | grep -qE 'internal|tooling|cli'; then
    SELECTED="$SELECTED architecture design"
    EXCLUDED="$EXCLUDED ux business"
fi
if echo "$ISSUE_TEXT" | grep -qE 'security|auth|permission'; then
    SELECTED="$SELECTED security"
fi
if echo "$ISSUE_TEXT" | grep -qE 'ci|cd|pipeline|build|deploy|release|migration'; then
    SELECTED="$SELECTED deployment"
fi

# Check version PLAN.md for focus
VERSION_PLAN=$(cat .claude/cat/versions/*/PLAN.md 2>/dev/null || echo "")
if echo "$VERSION_PLAN" | grep -qi 'commercialization'; then
    SELECTED="$SELECTED legal business"
fi

# Add forced stakeholders
for stakeholder in $FORCED; do
    SELECTED="$SELECTED $stakeholder"
done

# Deduplicate and finalize selection
SELECTED=$(echo "$SELECTED" | tr ' ' '
' | sort -u | tr '
' ' ')
```

### File-Based Override Logic (Review Mode)

```bash
# Get changed files from base branch to HEAD (captures all commits since worktree creation)
# Read base branch from cat-base metadata (fail-fast if missing - stakeholder-review always runs in worktrees)
WORKTREE_NAME=$(basename "$PWD" 2>/dev/null)
CAT_BASE_FILE="$(git rev-parse --git-common-dir)/worktrees/${WORKTREE_NAME}/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
    echo "ERROR: cat-base metadata not found: $CAT_BASE_FILE" >&2
    echo "Stakeholder review requires worktree context. Run via /cat:work." >&2
    exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

CHANGED_FILES=$(git diff --name-only "${BASE_BRANCH}..HEAD" 2>/dev/null || git diff --name-only --cached)

# Check for file-based overrides
if echo "$CHANGED_FILES" | grep -qE '(ui/|frontend/|\.tsx$|\.vue$)'; then
    if ! echo "$SELECTED" | grep -q 'ux'; then
        SELECTED="$SELECTED ux"
        OVERRIDDEN="$OVERRIDDEN ux:UI_file_changed"
    fi
fi

if echo "$CHANGED_FILES" | grep -qE '(auth/|permission/|security/)'; then
    if ! echo "$SELECTED" | grep -q 'security'; then
        SELECTED="$SELECTED security"
        OVERRIDDEN="$OVERRIDDEN security:security_file_changed"
    fi
fi

if echo "$CHANGED_FILES" | grep -qE '(Test|Spec|_test)\.'; then
    if ! echo "$SELECTED" | grep -q 'testing'; then
        SELECTED="$SELECTED testing"
        OVERRIDDEN="$OVERRIDDEN testing:test_file_changed"
    fi
fi

if echo "$CHANGED_FILES" | grep -qE '(sort|search|optimize|process|algorithm)'; then
    if ! echo "$SELECTED" | grep -q 'performance'; then
        SELECTED="$SELECTED performance"
        OVERRIDDEN="$OVERRIDDEN performance:algorithm_file_changed"
    fi
fi

if echo "$CHANGED_FILES" | grep -qE '(Dockerfile|Jenkinsfile|\.github/.*\.yml|\.gitlab-ci\.yml|docker-compose)'; then
    if ! echo "$SELECTED" | grep -q 'deployment'; then
        SELECTED="$SELECTED deployment"
        OVERRIDDEN="$OVERRIDDEN deployment:cicd_file_changed"
    fi
fi

# Special case: only .md files changed
if echo "$CHANGED_FILES" | grep -qvE '\.md$' | grep -q .; then
    : # Non-md files exist, continue normally
else
    # Only markdown files - limit to requirements and design
    SELECTED="requirements design"
    SKIPPED="$SKIPPED architecture:only_md_files security:only_md_files testing:only_md_files performance:only_md_files ux:only_md_files business:only_md_files legal:only_md_files"
fi

# Special case: only test files changed
NON_TEST_FILES=$(echo "$CHANGED_FILES" | grep -vE '(Test|Spec|_test)\.' || true)
if [[ -z "$NON_TEST_FILES" ]] && [[ -n "$CHANGED_FILES" ]]; then
    SELECTED="requirements testing design"
    SKIPPED="$SKIPPED architecture:only_test_files security:only_test_files performance:only_test_files ux:only_test_files business:only_test_files legal:only_test_files"
fi
```

### Output Format

After context analysis, generate the selection box by invoking:

**IMPORTANT:** `cat:stakeholder-selection-box` uses POSITIONAL space-separated arguments, NOT JSON.
Do NOT pass the skill's own arguments or a JSON object. Compute the values first, then pass them as:

```
Skill tool:
  skill: "cat:stakeholder-selection-box"
  args: "${SELECTED_COUNT} 10 ${SELECTED_LIST} ${SKIPPED_LIST}"
```

**Argument format (positional):**
- Arg 1: `${SELECTED_COUNT}` — integer count of selected stakeholders (e.g., `5`)
- Arg 2: `10` — integer total stakeholder count (always 10)
- Arg 3: `${SELECTED_LIST}` — comma-separated stakeholder names (e.g., `requirements,architecture,design`)
- Arg 4: `${SKIPPED_LIST}` — comma-separated `stakeholder:reason` pairs

**CRITICAL:** Args 1 and 2 MUST be integers. Passing stakeholder names as args 1 or 2 will cause
`selected-count must be an integer` error. Compute counts before invoking:

```bash
SELECTED_COUNT=$(echo "$SELECTED" | tr ' ' '
' | grep -c '.')
SELECTED_LIST=$(echo "$SELECTED" | tr ' ' ',')
SKIPPED_COUNT=$(echo "$SKIPPED" | tr ' ' '
' | grep -c '.' 2>/dev/null || echo 0)
SKIPPED_LIST=$(echo "$SKIPPED" | tr ' ' ',')
# Then invoke: args: "${SELECTED_COUNT} 10 ${SELECTED_LIST} ${SKIPPED_LIST}"
```

Copy the output verbatim.

If file-based overrides occurred, add an "Overrides (file-based):" section inside the box.

### Skip Reason Mapping

| Stakeholder | Skip Reason Examples |
|-------------|---------------------|
| ux | No UI/frontend changes detected |
| legal | No licensing/compliance keywords in issue |
| business | Internal tooling issue / No user-facing features |
| performance | No algorithm-heavy code changes |
| deployment | No CI/CD, build, or release changes detected |
| architecture | Documentation-only issue |
| security | Documentation-only issue / No source code changes |
| design | Documentation-only issue |
| testing | Documentation-only issue |

</step>

<step name="prepare">

**Prepare review context (Holistic Approach):**

Uses stakeholders selected by the `analyze_context` step. The `SELECTED` variable contains
the space-separated list of stakeholders to run.

1. Identify files changed in implementation
2. **Read full file content** (not just diffs) for holistic evaluation
3. Include diff summary as supplementary context
4. Use stakeholder selection from analyze_context step

**Worktree Isolation:** When reviewing work done in a worktree, ALL git commands and file reads
MUST execute from within the worktree directory. The `WORKTREE_PATH` from the skill arguments
specifies the correct location. Running git commands from `/workspace/` reads the base branch
(pre-implementation state), not the worktree branch (post-implementation state).

```bash
# CRITICAL: Set working directory to worktree if provided in arguments
# WORKTREE_PATH comes from the skill's positional arguments (e.g., /workspace/.claude/cat/worktrees/2.1-issue-name)
# All subsequent git commands and file reads MUST use this directory
if [[ -n "${WORKTREE_PATH}" ]]; then
    cd "${WORKTREE_PATH}" || { echo "ERROR: Cannot cd to worktree: ${WORKTREE_PATH}"; exit 1; }
fi

# Get changed files from base branch to HEAD (captures all commits since worktree creation)
# Read base branch from cat-base metadata (fail-fast if missing - stakeholder-review always runs in worktrees)
WORKTREE_NAME=$(basename "$PWD" 2>/dev/null)
CAT_BASE_FILE="$(git rev-parse --git-common-dir)/worktrees/${WORKTREE_NAME}/cat-base"
if [[ ! -f "$CAT_BASE_FILE" ]]; then
    echo "ERROR: cat-base metadata not found: $CAT_BASE_FILE" >&2
    echo "Stakeholder review requires worktree context. Run via /cat:work." >&2
    exit 1
fi
BASE_BRANCH=$(cat "$CAT_BASE_FILE")

CHANGED_FILES=$(git diff --name-only "${BASE_BRANCH}..HEAD" 2>/dev/null || git diff --name-only --cached)

# Detect primary language from file extensions
PRIMARY_LANG=$(echo "$CHANGED_FILES" | grep -oE '\.[a-z]+$' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}' | tr -d '.')
# Maps: java, py, ts, js, go, rs, etc.

# Categorize by type (language-agnostic patterns)
SOURCE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|py|ts|js|go|rs|c|cpp|cs)$' || true)
TEST_FILES=$(echo "$CHANGED_FILES" | grep -E '(Test|Spec|_test|_spec)\.' || true)
CONFIG_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(json|yaml|yml|xml|properties|toml)$' || true)

# Check for language supplement
LANG_SUPPLEMENT=""
if [[ -f "${CLAUDE_PLUGIN_ROOT}/lang/${PRIMARY_LANG}.md" ]]; then
    LANG_SUPPLEMENT=$(cat "${CLAUDE_PLUGIN_ROOT}/lang/${PRIMARY_LANG}.md")
fi

# SELECTED is populated by analyze_context step
# Contains: space-separated stakeholder names (e.g., "requirements architecture security design testing")
# SKIPPED contains: stakeholder:reason pairs for reporting
# OVERRIDDEN contains: stakeholder:reason pairs for file-based overrides

# Discover convention files and build per-stakeholder convention map
CONVENTION_MAP=""  # Will store "stakeholder:convention_path" entries
if [[ -d ".claude/cat/rules" ]]; then
    for convention_file in .claude/cat/rules/*.md; do
        if [[ -f "$convention_file" ]]; then
            # Parse YAML frontmatter for subAgents field
            # Extract lines between --- delimiters at start of file
            frontmatter=$(sed -n '1{/^---$/!q};1,/^---$/p' "$convention_file" | sed '1d;$d')

            # Extract subAgents array from frontmatter (format: subAgents: [] or [cat:stakeholder-design, ...])
            subagents_line=$(echo "$frontmatter" | grep '^subAgents:' || echo "")

            if [[ -n "$subagents_line" ]]; then
                # Extract array contents: strip "subAgents: [" prefix and "]" suffix
                subagents_raw=$(echo "$subagents_line" | sed 's/^subAgents:\s*\[//;s/\]\s*$//' | tr ',' '\n' | sed 's/^[ 	]*//;s/[ 	]*$//')

                # Handle [all]: convention applies to every selected stakeholder
                if echo "$subagents_raw" | grep -qx 'all'; then
                    for stakeholder in $SELECTED; do
                        CONVENTION_MAP="${CONVENTION_MAP}${stakeholder}:${convention_file} "
                    done
                else
                    # Handle specific types: extract stakeholder name by stripping cat:stakeholder- prefix
                    for agent_type in $subagents_raw; do
                        stakeholder="${agent_type#cat:stakeholder-}"
                        # Only add if the prefix was actually stripped (i.e., it was a stakeholder type)
                        if [[ "$stakeholder" != "$agent_type" ]]; then
                            CONVENTION_MAP="${CONVENTION_MAP}${stakeholder}:${convention_file} "
                        fi
                    done
                fi
            fi
        fi
    done
fi

# Prepare file content for holistic review
# For small files: include full content
# For large files: diff with extended context + file structure summary
MAX_FILE_SIZE=50000  # characters threshold for "large file" handling
FILE_CONTENTS=""
for file in $CHANGED_FILES; do
    if [[ -f "$file" ]]; then
        size=$(wc -c < "$file")
        if [[ $size -lt $MAX_FILE_SIZE ]]; then
            # Small file: include full content
            FILE_CONTENTS="${FILE_CONTENTS}

### File: ${file}
\`\`\`
$(cat "$file")
\`\`\`"
        else
            # Large file: structure summary + diff with extended context
            FILE_CONTENTS="${FILE_CONTENTS}

### File: ${file} (large file)
"

            # Extract file structure summary based on language
            ext="${file##*.}"
            FILE_CONTENTS="${FILE_CONTENTS}
#### Structure Summary:
\`\`\`
"
            case "$ext" in
                java)
                    # Java: package, imports, class/interface/enum declarations, method signatures
                    grep -nE '^package |^import |^(public |private |protected )?(abstract |static |final )*(class |interface |enum |record )|^\s+(public |private |protected )?(abstract |static |final |synchronized )*[a-zA-Z<>\[\]]+\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(' "$file" 2>/dev/null | head -100
                    ;;
                py)
                    # Python: imports, class definitions, function definitions
                    grep -nE '^import |^from .* import |^class |^def |^async def ' "$file" 2>/dev/null | head -100
                    ;;
                ts|js|tsx|jsx)
                    # TypeScript/JavaScript: imports, exports, class/function declarations
                    grep -nE '^import |^export |^(export )?(async )?(function |class |const |let |interface |type )' "$file" 2>/dev/null | head -100
                    ;;
                go)
                    # Go: package, imports, type/func declarations
                    grep -nE '^package |^import |^type |^func ' "$file" 2>/dev/null | head -100
                    ;;
                *)
                    # Generic: show first 20 lines (often contains headers/imports) + any function-like patterns
                    head -20 "$file"
                    echo "..."
                    grep -nE '^\s*(function|def|class|struct|enum|interface|impl|pub fn|fn |sub |proc )' "$file" 2>/dev/null | head -50
                    ;;
            esac
            FILE_CONTENTS="${FILE_CONTENTS}\`\`\`
"

            # Diff with 100 lines of context for this file
            FILE_CONTENTS="${FILE_CONTENTS}
#### Changes (with 100 lines context):
\`\`\`diff
"
            FILE_CONTENTS="${FILE_CONTENTS}$(git diff "${BASE_BRANCH}..HEAD" -U100 -- "$file" 2>/dev/null)
\`\`\`
"
        fi
    fi
done

# Also prepare full diff for supplementary context (smaller context for overview)
DIFF_SUMMARY=$(git diff "${BASE_BRANCH}..HEAD" -U3 2>/dev/null || git diff --cached -U3)

# Read effort level from cat-config.json to determine review scope
CONFIG_FILE="${CLAUDE_PROJECT_DIR}/.claude/cat/cat-config.json"
EFFORT="medium"  # default
if [[ -f "$CONFIG_FILE" ]]; then
    EFFORT=$(grep '"effort"' "$CONFIG_FILE" | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    EFFORT="${EFFORT:-medium}"
fi

case "$EFFORT" in
    low)    REVIEW_SCOPE="Review changed lines only. Flag obvious issues visible in the diff." ;;
    medium) REVIEW_SCOPE="Review changed lines and their surrounding context (functions, classes containing the change). Flag issues that arise from the interaction between new and existing code." ;;
    high)   REVIEW_SCOPE="Review the broader impact on surrounding code. Flag pre-existing issues in any file you read, not just the changed lines. Consider systemic effects across the codebase." ;;
    *)      REVIEW_SCOPE="Review changed lines only. Flag obvious issues visible in the diff." ;;
esac
```

**Holistic context enables:**
- Reviewing changes in context of surrounding code
- Detecting inconsistencies with existing patterns
- Evaluating test coverage relative to implementation complexity
- Identifying accumulated technical debt

**Large file handling:**
- Files under 50KB: full content included
- Files over 50KB: structure summary + diff with 100 lines context
  - Structure summary extracts: imports, class/function declarations, method signatures
  - Language-aware extraction for Java, Python, TypeScript/JavaScript, Go
  - Diff with extended context shows changes in their surrounding code

**Stakeholder selection is now context-aware:**

The `analyze_context` step determines which stakeholders run based on:
1. Issue type (documentation, refactor, bugfix, performance)
2. Keywords in issue description (license, UI, API, security, etc.)
3. Version focus (commercialization triggers legal/business)
4. File-based overrides (review mode only)
5. User-forced stakeholders via `## Force Stakeholders` in PLAN.md

See `analyze_context` step for full selection rules and skip reasons.

</step>

<step name="spawn_reviewers">

**Spawn stakeholder subagents in parallel:**

**Prompt Completeness Requirement (applies to ALL rounds including re-runs after fixes):**

Every reviewer prompt MUST include the complete set of sections below — including `## Files to Review
(Full Content)` with the actual `FILE_CONTENTS` embedded. Do NOT simplify prompts in subsequent
rounds by replacing file content with instructions like "Read InjectEnv.java and verify". Subagents
receiving simplified prompts will read from their default working directory (`/workspace/`), which
contains the base-branch code, not the worktree's implementation — producing false-positive rejections.

For each relevant stakeholder, spawn a subagent with:

```bash
# For each stakeholder in $SELECTED
stakeholder="architecture"  # example

# Collect conventions for this stakeholder
STAKEHOLDER_CONVENTIONS=""
for entry in $CONVENTION_MAP; do
    conv_stakeholder="${entry%%:*}"
    conv_path="${entry#*:}"
    if [[ "$conv_stakeholder" == "$stakeholder" ]]; then
        STAKEHOLDER_CONVENTIONS="${STAKEHOLDER_CONVENTIONS}
### Convention: ${conv_path}
$(cat "$conv_path")
"
    fi
done
```

Before spawning, pre-fetch the issue context files using absolute worktree paths:

```bash
# Pre-fetch context files using absolute paths so subagents receive content directly
# and do not need to read any files themselves
ISSUE_DIR=$(ls -d "${WORKTREE_PATH}/.claude/cat/issues/"*/ 2>/dev/null | head -1)
ISSUE_PLAN_CONTENT=""
VERSION_PLAN_CONTENT=""
if [[ -n "$ISSUE_DIR" && -f "${ISSUE_DIR}PLAN.md" ]]; then
    ISSUE_PLAN_CONTENT=$(cat "${ISSUE_DIR}PLAN.md")
    # Derive version PLAN.md path from issue directory name (e.g., v2.1-issue-name -> v2.1)
    ISSUE_NAME=$(basename "$ISSUE_DIR")
    VERSION_PATTERN=$(echo "$ISSUE_NAME" | grep -oE '^v[0-9]+\.[0-9]+')
    MAJOR_VERSION=$(echo "$VERSION_PATTERN" | grep -oE '^v[0-9]+')
    if [[ -n "$MAJOR_VERSION" && -n "$VERSION_PATTERN" ]]; then
        VERSION_PLAN="${WORKTREE_PATH}/.claude/cat/issues/${MAJOR_VERSION}/${VERSION_PATTERN}/PLAN.md"
        if [[ -f "$VERSION_PLAN" ]]; then
            VERSION_PLAN_CONTENT=$(cat "$VERSION_PLAN")
        fi
    fi
fi
```

**Domain Knowledge Injection:**

Before spawning stakeholders, collect domain knowledge relevant to the code being reviewed. This
is system-specific context that stakeholders need to identify better alternatives — knowledge that
is not visible in the code itself.

```bash
# Collect domain knowledge from PLAN.md "Domain Knowledge" section (if present)
DOMAIN_KNOWLEDGE=""
if [[ -n "$ISSUE_DIR" && -f "${ISSUE_DIR}PLAN.md" ]]; then
    DOMAIN_KNOWLEDGE=$(awk '/^## Domain Knowledge/,/^## /' "${ISSUE_DIR}PLAN.md" | \
        grep -v '^## ' | sed '/^$/d' | head -50)
fi
```

If the PLAN.md does not have a `## Domain Knowledge` section but the implementation touches
system-specific APIs or behaviors (e.g., CLI output formats, session mechanics, external service
contracts), synthesize the key facts and add them to the prompt as domain knowledge.

Spawn each stakeholder with this prompt:

```
You are the {stakeholder} stakeholder reviewing an implementation.

## Working Directory
All file reads, searches, and git commands must target this directory: {WORKTREE_PATH}
Use absolute paths prefixed with {WORKTREE_PATH}/ for any additional files you read.
Do NOT access files outside this directory.

## Issue Context (Pre-fetched)

The following context has been pre-fetched from the worktree. Use this content directly
rather than reading these files yourself.

### Issue PLAN.md ({ISSUE_DIR}PLAN.md)
{ISSUE_PLAN_CONTENT}

### Version PLAN.md
{VERSION_PLAN_CONTENT}

## Domain Knowledge

The following system-specific knowledge is relevant for reviewing this code. Use it when
evaluating whether there are better alternatives to the approaches chosen.

{DOMAIN_KNOWLEDGE if non-empty, otherwise "No domain-specific knowledge provided for this review."}

## Your Role
{content of agents/stakeholder-{stakeholder}.md}

## Language-Specific Patterns
{content of LANG_SUPPLEMENT if available, otherwise "No language supplement loaded."}

## Project Conventions
{STAKEHOLDER_CONVENTIONS if any match this stakeholder, otherwise "No project conventions assigned to this stakeholder."}

## Files to Review (Full Content)
{FILE_CONTENTS - full file content prepared in prepare step}

## What Changed (Diff Summary)
{DIFF_SUMMARY - git diff for reference}

## Holistic Review Instructions
You have access to the FULL content of changed files, not just diffs. Use this to:

1. **Evaluate impact on entire project** - How do these changes affect the codebase as a whole?
2. **Check for accumulated patterns** - Is this change contributing to technical debt?
3. **Verify consistency** - Does this follow existing patterns in the surrounding code?
4. **Assess completeness** - Are there related areas that should also be updated?

## Severity Definitions

Use this universal framework to assign severity consistently. Each level reflects urgency and impact,
not domain-specific importance:

| Severity | Definition | Examples |
|----------|-----------|----------|
| CRITICAL | Blocks release. Causes data loss, security breach, or breaks core functionality. Must fix before merge. | Exploitable vulnerability, data corruption, system crash |
| HIGH | Significant issue that should be fixed soon. Does not block release but degrades quality materially. | Unsanitized input, method duplication, missing test for critical path |
| MEDIUM | Improvement that would meaningfully benefit the codebase. Acceptable to defer. | High cyclomatic complexity, overly broad permissions, missing edge case test |
| LOW | Minor suggestion, stylistic preference, or nice-to-have. No material impact if deferred indefinitely. | Naming convention, micro-optimization, comment clarity |

**Calibration rule:** A CRITICAL from any stakeholder should have roughly equivalent urgency — e.g., a
CRITICAL security concern (exploitable vulnerability) and a CRITICAL architecture concern (breaks system
invariant) both warrant blocking the merge. If your concern wouldn't block a release, it's not CRITICAL.

## Review Scope
{REVIEW_SCOPE}

## Review Criteria
1. Review the implementation against your stakeholder criteria
2. Apply language-specific red flags from the supplement (if loaded)
3. Consider the change in context of the full file, not just the diff
4. Identify concerns using the severity definitions above
5. Return your assessment in the specified JSON format
6. Be specific about locations and recommendations

Return ONLY valid JSON matching the format in your stakeholder definition.
```

**Model Selection:** Before spawning each subagent, read the `model` field from the agent's frontmatter
(`plugin/agents/stakeholder-{stakeholder}.md`). Pass it as the `model` parameter to the Task tool when spawning.
If no model field is present in the frontmatter, omit the `model` parameter (inherits parent model).

```bash
# Extract model from agent frontmatter
AGENT_FILE="${CLAUDE_PLUGIN_ROOT}/agents/stakeholder-${stakeholder}.md"
STAKEHOLDER_MODEL=$(sed -n '/^---$/,/^---$/p' "$AGENT_FILE" | grep '^model:' | sed 's/^model:[[:space:]]*//' | head -1)
```

Use the Task tool for each stakeholder. If `$STAKEHOLDER_MODEL` is non-empty, pass `model: $STAKEHOLDER_MODEL`.
If empty, omit the `model` parameter entirely to inherit the parent model.

</step>

<step name="collect_reviews">

**Collect and parse stakeholder reviews:**

Wait for all stakeholder subagents to complete. Parse each response as JSON:

```json
{
  "stakeholder": "architecture|security|design|testing|performance",
  "approval": "APPROVED|CONCERNS|REJECTED",
  "concerns": [
    {
      "severity": "CRITICAL|HIGH|MEDIUM|LOW",
      "location": "file:line",
      "explanation": "Brief description of the concern",
      "recommendation": "Brief remediation guidance",
      "detail_file": ".claude/cat/review/<stakeholder>-concerns.json"
    }
  ]
}
```

Handle parse failures gracefully - if a stakeholder returns invalid JSON, treat as CONCERNS
with a note about the parse failure.

Trust subagents: do not validate evidence fields. Accept reviewer results at face value.

</step>

<step name="aggregate">

**Aggregate and evaluate severity:**

Read `minSeverity` from `.claude/cat/cat-config.json` (default: `"low"`). Filter out any concerns with severity
below `minSeverity` before counting. Filtered concerns are silently dropped — they are never shown to the user,
never tracked, and never fixed.

Severity ordering (highest to lowest): CRITICAL > HIGH > MEDIUM > LOW.

Count concerns across all stakeholders after filtering:

```bash
MIN_SEVERITY=$(read "minSeverity" from cat-config.json, default "low")
CRITICAL_COUNT=0
HIGH_COUNT=0
MEDIUM_COUNT=0
LOW_COUNT=0
FILTERED_COUNT=0
REJECTED_COUNT=0

for review in reviews:
    if review.approval == "REJECTED":
        REJECTED_COUNT++
    for concern in review.concerns:
        if concern.severity is below MIN_SEVERITY:
            FILTERED_COUNT++
            skip this concern
        elif concern.severity == "CRITICAL":
            CRITICAL_COUNT++
        elif concern.severity == "HIGH":
            HIGH_COUNT++
        elif concern.severity == "MEDIUM":
            MEDIUM_COUNT++
        elif concern.severity == "LOW":
            LOW_COUNT++
```

If FILTERED_COUNT > 0, note in the report summary how many concerns were filtered by minSeverity.

**Decision rules:**

**NOTE:** These statuses indicate stakeholder review outcome, NOT user approval to merge.
User approval is a separate gate that follows stakeholder review.

| Condition | Decision |
|-----------|----------|
| CRITICAL_COUNT > 0 | REJECTED - Must fix critical issues |
| REJECTED_COUNT > 0 | REJECTED - Stakeholder explicitly rejected |
| HIGH_COUNT > 0 | CONCERNS - Document but proceed to user approval |
| Otherwise | REVIEW_PASSED - Proceed to user approval |

The calling skill (work-with-issue) reads the reviewThreshold config and decides whether to trigger the auto-fix loop
and when to stop, based on the configured AUTOFIX_THRESHOLD severity threshold.

</step>

<step name="report">

**Generate compact review report:**

Output the review results:

Generate the review summary by invoking:

```
Skill tool:
  skill: "cat:stakeholder-review-box"
  args: "${ISSUE_ID} ${REVIEWER_STATUS_LIST} ${REVIEW_RESULT} ${REVIEW_SUMMARY}"
```

**CRITICAL - Argument Count:** `cat:stakeholder-review-box` expects exactly 4 arguments. The entire reviewer list MUST
be a single comma-separated string (the second positional argument). Build the string before invoking — do NOT pass
each reviewer as a separate space-separated argument (that produces 8+ args instead of 4 and causes a CLI error).

```bash
# Correct: build comma-separated string first, then pass as single arg
REVIEWER_STATUS_LIST="requirements:✓ APPROVED,architecture:✓ APPROVED,security:⚠ 1 HIGH"
# Invoke: args: "${ISSUE_ID} ${REVIEWER_STATUS_LIST} ${REVIEW_RESULT} ${REVIEW_SUMMARY}"

# WRONG: space-separated — each reviewer becomes a separate arg → CLI rejects as wrong argument count
# args: "${ISSUE_ID} requirements:APPROVED architecture:APPROVED security:CONCERNS ..."
```

The second argument is a comma-separated list of `stakeholder:status` pairs
(e.g., `"architecture:✓ APPROVED,design:⚠ 1 HIGH"`).

For each concern, generate a concern box:

```
Skill tool:
  skill: "cat:stakeholder-concern-box"
  args: "${SEVERITY} ${STAKEHOLDER} ${CONCERN_DESCRIPTION} ${FILE_LOCATION}"
```

Where `${SEVERITY}` is CRITICAL, HIGH, MEDIUM, or LOW.

**Status icons:**
- `✓` - APPROVED
- `⚠` - CONCERNS (shows HIGH count if any)
- `✗` - REJECTED (shows CRITICAL or HIGH count)

</step>

<step name="decide">

**Finalize review output:**

The review box and concern boxes rendered in the `report` step are the complete user-facing output. No additional
output or JSON is needed after the boxes are displayed.

When invoked via `work-with-issue`, the caller reads the aggregated result from the internal state to drive auto-fix
iteration and user approval gates. When invoked standalone, read `reviewThreshold` from `.claude/cat/cat-config.json`
(default `"low"`) and offer to address concerns at or above that threshold without the auto-fix loop — the review
boxes already provide the full picture for the user.

**Concern coverage by status:**
- REJECTED: full concern details (CRITICAL, HIGH, MEDIUM, LOW at or above minSeverity)
- CONCERNS: full concern details (HIGH, MEDIUM, LOW at or above minSeverity)
- APPROVED: any LOW concerns noted (if LOW is at or above minSeverity)

The calling skill (work-with-issue) is responsible for:
- Auto-fix iteration for HIGH+ concerns
- User approval gates for MEDIUM concerns
- Escalation handling when auto-fix fails

</step>

## Output Format

**User-Facing Output:**

The review box CLI tools (`cat:stakeholder-review-box` and `cat:stakeholder-concern-box`) are the complete user-facing
output. Invoke them in the `report` step — they provide all information users need (stakeholder names, concern counts,
icons, overall result). No additional output is needed after the boxes are rendered.

**Internal Result Contract (for work-with-issue only):**

When invoked by `work-with-issue`, the skill's final internal state is consumed by the caller as a structured object.
The caller parses these fields:

- `review_status`: `"APPROVED"`, `"CONCERNS"`, or `"REJECTED"`
- `concerns[]`: flat list of concern objects, each with:
  - `severity`: `"CRITICAL"`, `"HIGH"`, `"MEDIUM"`, or `"LOW"`
  - `stakeholder`: name of the reviewing stakeholder (e.g., `"security"`)
  - `location`: file and line reference (e.g., `"src/UserDao.java:45"`)
  - `explanation`: brief description of the concern
  - `recommendation`: brief remediation guidance
  - `detail_file`: path to detailed reviewer analysis (e.g., `".claude/cat/review/security-concerns.json"`)
- `summary`: brief summary of review outcome

The `detail_file` path points to the comprehensive analysis written by the reviewer subagent. The main agent must NOT
read these detail files — pass the paths to fix subagents instead.

## Integration with work

This skill is invoked automatically after the implementation phase:

```
Implementation Phase
       ↓
  Build Verification
       ↓
  Stakeholder Review ← This skill
       ↓
  [If REJECTED] → Fix concerns → Loop back to implementation
       ↓
  [If APPROVED/CONCERNS] → User Approval Gate
       ↓
  Merge to main
```

## When to Run (Automatic Triggering)

Review triggering depends on verify level (NOT trust level):

| Verify | Action |
|--------|--------|
| `none` | Skip all stakeholder reviews |
| `changed` | Run stakeholder reviews |
| `all` | Run stakeholder reviews |

```bash
VERIFY_LEVEL=$(jq -r '.verify // "changed"' .claude/cat/cat-config.json)
if [[ "$VERIFY_LEVEL" == "none" ]]; then
  # Skip stakeholder review entirely
fi
```

**High-risk detection** (informational, for risk assessment display):
- Risk section mentions "breaking change", "data loss", "security", "production"
- Issue modifies authentication, authorization, or payment code
- Issue touches 5+ files
- Issue modifies public APIs or interfaces
- Issue involves database schema changes
