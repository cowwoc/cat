# Learn Skill Examples

Reference examples for context analysis patterns.
Lazy-loaded when reviewing how to classify mistakes.

## Context-Related Mistake

```yaml
mistake:
  type: "Forgot earlier requirement"
  tokens_at_error: 110000
  compactions: 3

analysis:
  context_related: YES
  pattern: "Requirement stated at 15K tokens, forgotten by 110K"

prevention:
  type: earlier_decomposition
  action: "Split issue at 40K tokens, before degradation"
```

## Non-Context-Related Mistake

```yaml
mistake:
  type: "Used wrong API method"
  tokens_at_error: 25000
  compactions: 0

analysis:
  context_related: NO
  pattern: "Simple misunderstanding of API, not context issue"

prevention:
  type: validation
  action: "Add API usage verification in code review checklist"
```

## Ambiguous Case

```yaml
mistake:
  type: "Inconsistent code style"
  tokens_at_error: 75000
  compactions: 1

analysis:
  context_related: POSSIBLY
  pattern: "Style was consistent until compaction, then diverged"
  contributing_factors:
    - Compaction lost style context
    - No automated style check

prevention:
  type: hybrid
  actions:
    - "Add automated style linting (code fix)"
    - "Lower threshold to avoid compaction (CAT-specific)"
```
