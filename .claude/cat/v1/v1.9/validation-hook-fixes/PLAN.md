# Plan: validation-hook-fixes

## Objective
fix HEREDOC message extraction in commit type validation hook

## Details
The hook was using grep -oP with a pattern that assumed literal newlines,
but HEREDOC content in TOOL_INPUT has actual newlines. Changed to sed-based
extraction that correctly parses HEREDOC blocks.

Fixes detection gap where invalid types like 'feat:' could bypass validation
when used with HEREDOC commit messages.

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
