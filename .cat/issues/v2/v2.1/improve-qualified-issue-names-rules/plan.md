# Plan

## Goal

Run the full instruction-builder workflow on plugin/rules/qualified-issue-names.md to improve its design: add explicit ### subsections for Issues, Skills, and Files; expand Issues "Applies to" to cover planning files and plan.md; prohibit leading ./ and / prefixes on file paths; clarify Project root as repository root vs worktree directory.

## Pre-conditions

(none)

## Main Agent Jobs

- /cat:instruction-builder-agent plugin/rules/qualified-issue-names.md

## Post-conditions

- [ ] instruction-builder workflow completes with SPRT ACCEPT on all test cases
- [ ] plugin/rules/qualified-issue-names.md has explicit ### subsections for Issues, Skills, and Files
- [ ] Issues "Applies to" covers planning files and plan.md (not just free-text responses)
- [ ] Files "Never use" prohibits leading ./ and / prefixes
- [ ] Project root clarified as repository root (not worktree directory)
