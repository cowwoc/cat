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

## Acceptance Criteria
- [ ] Workflow analyzes task context before stakeholder review
- [ ] Irrelevant stakeholders are automatically skipped
- [ ] User is informed which stakeholders were skipped and why
- [ ] User can still request specific stakeholders if needed
- [ ] Works for both /cat:research and /cat:stakeholder-review
