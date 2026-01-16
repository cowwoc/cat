# Plan: context-aware-stakeholders

## Goal
Update stakeholder review workflow to automatically select relevant stakeholders based on task context, skipping irrelevant ones while informing the user which stakeholders were skipped and why.

## Satisfies
- None (workflow improvement)

## Approach Outlines

### Conservative
Add simple keyword matching to decide stakeholder relevance (e.g., "license" -> legal, "performance" -> performance stakeholder).
- **Risk:** LOW
- **Tradeoff:** May miss nuanced contexts

### Balanced
Implement context analysis that considers task type, description keywords, file types being modified, and version focus to select stakeholders. Report skipped stakeholders with brief rationale.
- **Risk:** MEDIUM
- **Tradeoff:** Requires tuning relevance rules

### Aggressive
Full semantic analysis of task context with LLM-based stakeholder relevance scoring and detailed justification for each inclusion/exclusion.
- **Risk:** HIGH
- **Tradeoff:** Higher token cost, over-engineering

## Implementation Notes

### Context-Based Selection Rules

Map task context to relevant stakeholders:

| Context Signal | Include | Exclude |
|----------------|---------|---------|
| Task type: documentation | requirements | architect, security, quality, tester, performance, ux, sales, marketing |
| Task type: refactor | architect, quality, tester | ux, sales, marketing |
| Task type: bugfix | requirements, quality, tester, security | sales, marketing |
| Task type: performance | performance, architect, tester | ux, sales, marketing |
| Keywords: "license", "compliance", "legal" | legal | - |
| Keywords: "UI", "frontend", "user interface" | ux | - |
| Keywords: "API", "endpoint", "public" | architect, security, marketing | - |
| Keywords: "internal", "tooling", "CLI" | architect, quality | ux, sales, marketing |
| Keywords: "security", "auth", "permission" | security | - |
| Version focus: commercialization | legal, sales, marketing | - |
| File changes: only .md files | requirements | all others |
| File changes: only test files | tester, quality | ux, sales, marketing |

### Layered Selection Strategy

Use both context-based and file-based selection as complementary layers:

| Layer | When Applied | Purpose |
|-------|--------------|---------|
| Context-based | Always (research + review) | Sets baseline from intent |
| File-based | Review mode only | Catches scope drift from reality |

**Why keep both:**
- Context captures intent but can be fooled by misleading descriptions
- File changes are objective but only work post-implementation
- Combined approach provides defense in depth

**Example of layered value:**
```
Task: "Refactor internal CLI parser"
Context-based: Skip ux, sales, marketing (internal tooling)
But implementation touches: src/ui/TerminalRenderer.ts
File-based override: Add ux back (UI file changed)
```

### Selection Algorithm

**Research mode (pre-implementation):**
1. Start with base set: `[requirements]` (always included)
2. Apply context-based inclusions from task description/type/keywords
3. Apply context-based exclusions
4. Check version PLAN.md focus for additional inclusions
5. Report selection with rationale

**Review mode (post-implementation):**
1. Start with base set: `[requirements]` (always included)
2. Apply context-based inclusions/exclusions (same as research)
3. Apply file-based overrides:
   - If UI files changed → add ux (even if context excluded it)
   - If security-sensitive files changed → add security
   - If test files changed → add tester
   - If algorithm-heavy files changed → add performance
4. Report final selection, noting any file-based overrides

### Output Format

```
Stakeholder Review: 5 of 10 stakeholders selected

Running: requirements, architect, security, quality, tester
Skipped:
  - ux: No UI/frontend changes detected
  - legal: No licensing/compliance keywords in task
  - sales: Internal tooling task
  - marketing: Internal tooling task
  - performance: No algorithm-heavy code changes
```

## Acceptance Criteria
- [ ] Workflow analyzes task context before stakeholder review
- [ ] Irrelevant stakeholders are automatically skipped
- [ ] User is informed which stakeholders were skipped and why
- [ ] User can still request specific stakeholders if needed
- [ ] Works for both /cat:research and /cat:stakeholder-review
