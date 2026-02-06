# Refactor: scan-outdated-templates

## Goal

Scan all scripts for outdated output templates containing placeholders (like `{issue-name}`, `{version}`) and replace
them with preprocessor equivalents that generate properly aligned output.

## Background

PATTERN-008 identified that LLMs cannot accurately count character display widths, leading to misaligned boxes when
manually constructing output. The solution is to use pre-rendered scripts. However, some older scripts still contain
placeholder-based templates that encourage manual construction.

## Scope

- Identify scripts with placeholder templates in output
- Replace with calls to pre-rendering scripts (get-progress-banner.sh, get-work-boxes.py, etc.)
- Ensure all output is generated via scripts, not manual construction

## Satisfies

None (infrastructure/cleanup issue)

## Acceptance Criteria

- [ ] All tests still pass
- [ ] Code quality improved - no placeholder templates remain
- [ ] Technical debt reduced - consistent use of pre-rendering scripts

## Approach

1. Grep for placeholder patterns in plugin/scripts/ and plugin/skills/
2. Identify which scripts need updating
3. Replace placeholder templates with appropriate pre-rendering script calls
4. Verify output alignment with existing tests
