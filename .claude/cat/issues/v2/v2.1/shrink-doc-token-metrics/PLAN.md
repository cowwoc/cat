# Task: shrink-doc-token-metrics

## Type
Feature

## Description
The /cat:shrink-doc skill should report compression metrics in terms of tokens instead of lines. Token counts are more meaningful for LLM context management since Claude's context window is measured in tokens, not lines.

## Acceptance Criteria
- [ ] Functionality works as described - shrink-doc reports compression in tokens
- [ ] Tests written and passing - unit tests cover token counting
- [ ] Documentation updated - SKILL.md reflects token-based reporting
- [ ] No regressions - existing shrink-doc functionality unaffected

## Implementation Notes
- Count tokens using tiktoken or similar tokenizer
- Report: original tokens, compressed tokens, token reduction percentage
- May keep line count as secondary metric for reference

## Scope
1-2 files (shrink-doc SKILL.md, possibly a token counting utility)

## Satisfies
None (infrastructure/UX improvement)
