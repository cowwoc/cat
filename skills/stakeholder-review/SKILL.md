---
name: cat:stakeholder-review
description: Multi-perspective quality review gate with architect, security, quality, tester, and performance stakeholders
---

# Skill: stakeholder-review

Multi-perspective stakeholder review gate for implementation quality assurance.

## Purpose

Run parallel stakeholder reviews of implementation changes to identify concerns from multiple
perspectives (architecture, security, quality, testing, performance) before user approval.

## When to Use

- After implementation phase completes in `/cat:execute-task`
- Before the user approval gate
- When significant code changes need multi-perspective validation

## Stakeholders

| Stakeholder | Reference | Focus |
|-------------|-----------|-------|
| architect | @stakeholders/architect.md | System design, module boundaries, APIs |
| security | @stakeholders/security.md | Vulnerabilities, input validation |
| quality | @stakeholders/quality.md | Code quality, complexity, duplication |
| tester | @stakeholders/tester.md | Test coverage, edge cases |
| performance | @stakeholders/performance.md | Efficiency, resource usage |

## Process

<step name="prepare">

**Prepare review context:**

1. Identify files changed in implementation
2. Get diff summary for reviewers
3. Determine which stakeholders are relevant (skip if no applicable changes)

```bash
# Get changed files
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
if [[ -f ".claude/cat/references/stakeholders/lang/${PRIMARY_LANG}.md" ]]; then
    LANG_SUPPLEMENT=$(cat ".claude/cat/references/stakeholders/lang/${PRIMARY_LANG}.md")
fi
```

**Stakeholder relevance:**
- **architect**: Always run if source files changed
- **security**: Always run if source files changed
- **quality**: Always run if source files changed
- **tester**: Run if test files changed OR production code changed without tests
- **performance**: Run if algorithm-heavy code changed

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

Use `/cat:spawn-subagent` or `Task` tool with subagent_type for each stakeholder.

</step>

<step name="collect_reviews">

**Collect and parse stakeholder reviews:**

Wait for all stakeholder subagents to complete. Parse each response as JSON:

```json
{
  "stakeholder": "architect|security|quality|tester|performance",
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

**Generate review report:**

```markdown
## Stakeholder Review Summary

**Status:** {APPROVED|CONCERNS|REJECTED}

### Stakeholder Results

| Stakeholder | Status | Critical | High | Medium |
|-------------|--------|----------|------|--------|
| architect | {status} | {count} | {count} | {count} |
| security | {status} | {count} | {count} | {count} |
| quality | {status} | {count} | {count} | {count} |
| tester | {status} | {count} | {count} | {count} |
| performance | {status} | {count} | {count} | {count} |

### Critical Concerns (Must Fix)
{list of critical concerns with locations and recommendations}

### High Priority Concerns
{list of high concerns}

### Medium Priority Concerns (Informational)
{list of medium concerns}
```

</step>

<step name="decide">

**Take action based on result:**

**If REJECTED:**
1. Present concerns to user with clear explanation
2. Ask user how to proceed:
   - "Fix concerns" → Return to implementation phase with concern list
   - "Override and proceed" → Continue to user approval with concerns noted
   - "Abort task" → Stop execution

**If CONCERNS:**
1. Note concerns in task documentation
2. Proceed to user approval gate
3. Include concern summary in approval presentation

**If APPROVED:**
1. Proceed directly to user approval gate
2. Note that stakeholder review passed

</step>

## Output Format

Return structured result for integration with execute-task:

```json
{
  "review_status": "APPROVED|CONCERNS|REJECTED",
  "stakeholder_results": {
    "architect": {"status": "...", "concerns": [...]},
    "security": {"status": "...", "concerns": [...]},
    "quality": {"status": "...", "concerns": [...]},
    "tester": {"status": "...", "concerns": [...]},
    "performance": {"status": "...", "concerns": [...]}
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

## Integration with execute-task

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

Stakeholder review is **automatically triggered based on task characteristics**, not a global preference.
This ensures high-risk changes always get reviewed regardless of user settings.

**High-risk indicators** (any triggers review):
- Risk section mentions "breaking change", "data loss", "security", "production"
- Task modifies authentication, authorization, or payment code
- Task touches 5+ files
- Task modifies public APIs or interfaces
- Task involves database schema changes

**Note:** Reviews are always skipped when `yoloMode: true` (user explicitly chose autonomous mode).
