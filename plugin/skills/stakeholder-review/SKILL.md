---
description: Multi-perspective quality review gate with architect, security, design, testing, performance, and deployment stakeholders
user-invocable: false
---

# Skill: stakeholder-review

Multi-perspective stakeholder review gate for implementation quality assurance.

## Purpose

Run parallel stakeholder reviews of implementation changes to identify concerns from multiple
perspectives (architecture, security, design, testing, performance, deployment) before user approval.

## When to Use

- After implementation phase completes in `/cat:work`
- Before the user approval gate
- When significant code changes need multi-perspective validation

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-stakeholder-boxes.sh`

Use the box templates above for all display output (STAKEHOLDER_SELECTION, STAKEHOLDER_REVIEW, etc.)

## Stakeholders

| Stakeholder | Reference | Focus |
|-------------|-----------|-------|
| requirements | @stakeholders/requirements.md | Requirement satisfaction verification |
| architect | @stakeholders/architect.md | System design, module boundaries, APIs |
| security | @stakeholders/security.md | Vulnerabilities, input validation |
| design | @stakeholders/design.md | Code quality, complexity, duplication |
| testing | @stakeholders/testing.md | Test coverage, edge cases |
| performance | @stakeholders/performance.md | Efficiency, resource usage |
| deployment | @stakeholders/deployment.md | CI/CD, build systems, release readiness |
| ux | @stakeholders/ux.md | Usability, accessibility, interaction design |
| sales | @stakeholders/sales.md | Customer value, competitive positioning |
| marketing | @stakeholders/marketing.md | Positioning, messaging, go-to-market |
| legal | @stakeholders/legal.md | Licensing, compliance, IP, data privacy |

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
  → architect: ✓
  → security: ⚠ 1 HIGH
  → testing: ✓
  → performance: ✓
```

The aggregated result provides all necessary information without exposing 50+ internal
tool calls from reviewers.

## Process

<step name="analyze_context">

**Context-Aware Stakeholder Selection**

Analyze issue context to determine which stakeholders are relevant, reducing token usage by skipping irrelevant reviewers.

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
| documentation | requirements | architect, security, design, testing, performance, ux, sales, marketing |
| refactor | architect, design, testing | ux, sales, marketing |
| bugfix | requirements, design, testing, security | sales, marketing |
| performance | performance, architect, testing | ux, sales, marketing |

### Keyword Mappings

Scan issue description, goal, and PLAN.md for keywords:

| Keywords | Include |
|----------|---------|
| "license", "compliance", "legal" | legal |
| "UI", "frontend", "user interface" | ux |
| "API", "endpoint", "public" | architect, security, marketing |
| "internal", "tooling", "CLI" | architect, design (exclude ux, sales, marketing) |
| "security", "auth", "permission" | security |
| "CI", "CD", "pipeline", "build", "deploy", "release", "migration" | deployment |

### Version Focus Mapping

Check version PLAN.md for strategic focus:

- If version PLAN.md mentions "commercialization" → include legal, sales, marketing

### File-Based Overrides (Review Mode Only)

In review mode, file changes can override context exclusions:

| File Pattern | Add Stakeholder |
|--------------|-----------------|
| UI/frontend files (`**/ui/**`, `**/frontend/**`, `*.tsx`, `*.vue`) | ux |
| Security-sensitive files (`**/auth/**`, `**/permission/**`, `**/security/**`) | security |
| Test files (`*Test*`, `*Spec*`, `*_test*`) | testing |
| Algorithm-heavy files (sort, search, optimize, process) | performance |
| CI/CD files (`Dockerfile`, `*.yml` in `.github/`, `Jenkinsfile`, `*.yaml` pipeline) | deployment |
| Only .md files changed | requirements only, exclude all others |
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
TASK_PLAN=$(cat .claude/cat/issues/*/PLAN.md 2>/dev/null || echo "")

# Check for forced stakeholders
FORCED=$(echo "$TASK_PLAN" | sed -n '/## Force Stakeholders/,/^##/p' | grep '^ *-' | sed 's/^ *- *//')

# Detect issue type
TASK_TYPE=$(echo "$TASK_PLAN" | grep -E '^## Type' -A1 | tail -1 | tr '[:upper:]' '[:lower:]' || echo "")
if [[ -z "$TASK_TYPE" ]]; then
    # Infer from commit messages or issue name
    TASK_TYPE=$(git log -1 --pretty=%s 2>/dev/null | grep -oE '^(fix|feat|refactor|docs|perf)' | head -1)
    case "$TASK_TYPE" in
        docs) TASK_TYPE="documentation" ;;
        fix) TASK_TYPE="bugfix" ;;
        perf) TASK_TYPE="performance" ;;
    esac
fi

# Apply issue type mappings
case "$TASK_TYPE" in
    documentation)
        EXCLUDED="architect security design testing performance ux sales marketing"
        ;;
    refactor)
        SELECTED="$SELECTED architect design testing"
        EXCLUDED="ux sales marketing"
        ;;
    bugfix)
        SELECTED="$SELECTED design testing security"
        EXCLUDED="sales marketing"
        ;;
    performance)
        SELECTED="$SELECTED performance architect testing"
        EXCLUDED="ux sales marketing"
        ;;
    *)
        # Default: include core technical reviewers
        SELECTED="$SELECTED architect security design testing performance"
        EXCLUDED=""
        ;;
esac

# Scan for keywords in issue description
TASK_TEXT=$(echo "$TASK_PLAN" | tr '[:upper:]' '[:lower:]')

if echo "$TASK_TEXT" | grep -qE 'license|compliance|legal'; then
    SELECTED="$SELECTED legal"
fi
if echo "$TASK_TEXT" | grep -qE '\bui\b|frontend|user interface'; then
    SELECTED="$SELECTED ux"
fi
if echo "$TASK_TEXT" | grep -qE '\bapi\b|endpoint|public'; then
    SELECTED="$SELECTED architect security marketing"
fi
if echo "$TASK_TEXT" | grep -qE 'internal|tooling|\bcli\b'; then
    SELECTED="$SELECTED architect design"
    EXCLUDED="$EXCLUDED ux sales marketing"
fi
if echo "$TASK_TEXT" | grep -qE 'security|auth|permission'; then
    SELECTED="$SELECTED security"
fi
if echo "$TASK_TEXT" | grep -qE '\bci\b|\bcd\b|pipeline|build|deploy|release|migration'; then
    SELECTED="$SELECTED deployment"
fi

# Check version PLAN.md for focus
VERSION_PLAN=$(cat .claude/cat/versions/*/PLAN.md 2>/dev/null || echo "")
if echo "$VERSION_PLAN" | grep -qi 'commercialization'; then
    SELECTED="$SELECTED legal sales marketing"
fi

# Add forced stakeholders
for stakeholder in $FORCED; do
    SELECTED="$SELECTED $stakeholder"
done

# Deduplicate and finalize selection
SELECTED=$(echo "$SELECTED" | tr ' ' '\n' | sort -u | tr '\n' ' ')
```

### File-Based Override Logic (Review Mode)

```bash
# Get changed files
CHANGED_FILES=$(git diff --name-only HEAD~1..HEAD 2>/dev/null || git diff --name-only --cached)

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
    # Only markdown files - limit to requirements only
    SELECTED="requirements"
    SKIPPED="$SKIPPED architect:only_md_files security:only_md_files design:only_md_files testing:only_md_files performance:only_md_files ux:only_md_files sales:only_md_files marketing:only_md_files legal:only_md_files"
fi

# Special case: only test files changed
NON_TEST_FILES=$(echo "$CHANGED_FILES" | grep -vE '(Test|Spec|_test)\.' || true)
if [[ -z "$NON_TEST_FILES" ]] && [[ -n "$CHANGED_FILES" ]]; then
    SELECTED="requirements testing design"
    SKIPPED="$SKIPPED architect:only_test_files security:only_test_files performance:only_test_files ux:only_test_files sales:only_test_files marketing:only_test_files legal:only_test_files"
fi
```

### Output Format

After context analysis, use the **STAKEHOLDER_SELECTION** box from SCRIPT OUTPUT STAKEHOLDER BOXES.
Replace placeholders with actual selection data.

If file-based overrides occurred, add an "Overrides (file-based):" section inside the box.

### Skip Reason Mapping

| Stakeholder | Skip Reason Examples |
|-------------|---------------------|
| ux | No UI/frontend changes detected |
| legal | No licensing/compliance keywords in issue |
| sales | Internal tooling issue / No user-facing features |
| marketing | Internal tooling issue / No public API changes |
| performance | No algorithm-heavy code changes |
| deployment | No CI/CD, build, or release changes detected |
| architect | Documentation-only issue |
| security | Documentation-only issue / No source code changes |
| design | Documentation-only issue |
| testing | Documentation-only issue |

</step>

<step name="prepare">

**Prepare review context:**

Uses stakeholders selected by the `analyze_context` step. The `SELECTED` variable contains
the space-separated list of stakeholders to run.

1. Identify files changed in implementation
2. Get diff summary for reviewers
3. Use stakeholder selection from analyze_context step

```bash
# Get changed files (used for diff context, selection already done)
CHANGED_FILES=$(git diff --name-only HEAD~1..HEAD 2>/dev/null || git diff --name-only --cached)

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
# Contains: space-separated stakeholder names (e.g., "requirements architect security design testing")
# SKIPPED contains: stakeholder:reason pairs for reporting
# OVERRIDDEN contains: stakeholder:reason pairs for file-based overrides
```

**Stakeholder selection is now context-aware:**

The `analyze_context` step determines which stakeholders run based on:
1. Issue type (documentation, refactor, bugfix, performance)
2. Keywords in issue description (license, UI, API, security, etc.)
3. Version focus (commercialization triggers legal/sales/marketing)
4. File-based overrides (review mode only)
5. User-forced stakeholders via `## Force Stakeholders` in PLAN.md

See `analyze_context` step for full selection rules and skip reasons.

</step>

<step name="spawn_reviewers">

**Spawn stakeholder subagents in parallel:**

For each relevant stakeholder, spawn a subagent with:

```
You are the {stakeholder} stakeholder reviewing an implementation.

## Your Role
{content of stakeholders/{stakeholder}.md}

## Language-Specific Patterns
{content of LANG_SUPPLEMENT if available, otherwise "No language supplement loaded."}

## Files to Review
{list of changed files relevant to this stakeholder}

## Diff Summary
{git diff output or summary}

## Instructions
1. Review the implementation against your stakeholder criteria
2. Apply language-specific red flags from the supplement (if loaded)
3. Identify concerns at CRITICAL, HIGH, or MEDIUM severity
4. Return your assessment in the specified JSON format
5. Be specific about locations and recommendations

Return ONLY valid JSON matching the format in your stakeholder definition.
```

Use `/cat:spawn-subagent` or `Issue` tool with subagent_type for each stakeholder.

</step>

<step name="collect_reviews">

**Collect and parse stakeholder reviews:**

Wait for all stakeholder subagents to complete. Parse each response as JSON:

```json
{
  "stakeholder": "architect|security|design|testing|performance",
  "approval": "APPROVED|CONCERNS|REJECTED",
  "concerns": [...],
  "summary": "..."
}
```

Handle parse failures gracefully - if a stakeholder returns invalid JSON, treat as CONCERNS
with a note about the parse failure.

</step>

<step name="aggregate">

**Aggregate and evaluate severity:**

Count concerns across all stakeholders:

```bash
CRITICAL_COUNT=0
HIGH_COUNT=0
REJECTED_COUNT=0

for review in reviews:
    if review.approval == "REJECTED":
        REJECTED_COUNT++
    for concern in review.concerns:
        if concern.severity == "CRITICAL":
            CRITICAL_COUNT++
        elif concern.severity == "HIGH":
            HIGH_COUNT++
```

**Decision rules:**

| Condition | Decision |
|-----------|----------|
| CRITICAL_COUNT > 0 | REJECTED - Must fix critical issues |
| REJECTED_COUNT > 0 | REJECTED - Stakeholder rejected |
| HIGH_COUNT >= 3 | REJECTED - Too many high concerns |
| HIGH_COUNT > 0 | CONCERNS - Document but proceed |
| Otherwise | APPROVED - Proceed to user approval |

</step>

<step name="report">

**Generate compact review report:**

Output the review results:

**Summary box:** Use the **STAKEHOLDER_REVIEW** box from SCRIPT OUTPUT STAKEHOLDER BOXES.
Replace placeholders with actual reviewer results.

**Concern boxes (if any):** Use the **CRITICAL_CONCERN** or **HIGH_CONCERN** boxes.
Repeat as needed for each concern.

**Status icons:**
- `✓` - APPROVED
- `⚠` - CONCERNS (shows HIGH count if any)
- `✗` - REJECTED (shows CRITICAL or HIGH count)

</step>

<step name="decide">

**Take action based on result:**

**If REJECTED:**

Behavior depends on trust level:

| Trust | Rejection Behavior |
|-------|-------------------|
| `low` | Ask user: Fix / Override / Abort |
| `medium` | Auto-loop to fix (up to 3 iterations) |

Note: `trust: "high"` skips review entirely, so rejection handling doesn't apply.

For `trust: "low"`:
1. Present concerns to user with clear explanation
2. Ask user how to proceed:
   - "Fix concerns" → Return to implementation phase with concern list
   - "Override and proceed" → Continue to user approval with concerns noted
   - "Abort issue" → Stop execution

For `trust: "medium"`:
1. Automatically loop back to implementation phase with concern list
2. No user prompt required
3. Escalate to user only after 3 failed fix attempts

**If CONCERNS:**
1. Note concerns in issue documentation
2. Proceed to user approval gate
3. Include concern summary in approval presentation

**If APPROVED:**
1. Proceed directly to user approval gate
2. Note that stakeholder review passed

</step>

## Output Format

Return structured result for integration with work:

```json
{
  "review_status": "APPROVED|CONCERNS|REJECTED",
  "stakeholder_results": {
    "requirements": {"status": "...", "concerns": [...]},
    "architect": {"status": "...", "concerns": [...]},
    "security": {"status": "...", "concerns": [...]},
    "design": {"status": "...", "concerns": [...]},
    "testing": {"status": "...", "concerns": [...]},
    "performance": {"status": "...", "concerns": [...]},
    "deployment": {"status": "...", "concerns": [...]},
    "ux": {"status": "...", "concerns": [...]},
    "sales": {"status": "...", "concerns": [...]},
    "marketing": {"status": "...", "concerns": [...]},
    "legal": {"status": "...", "concerns": [...]}
  },
  "aggregated_concerns": {
    "critical": [...],
    "high": [...],
    "medium": [...]
  },
  "summary": "Brief summary of review outcome",
  "action_required": "none|fix_concerns|user_decision"
}
```

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
