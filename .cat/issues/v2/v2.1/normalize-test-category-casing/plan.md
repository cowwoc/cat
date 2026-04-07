# Plan

## Goal

Normalize test case category values to lowercase and add schema validation to ensure all category values match the allowed set.

## Pre-conditions

(none)

## Post-conditions

- [ ] All test case `category` frontmatter fields use lowercase values
- [ ] The complete allowed set of category values is defined in `skill-test.md`
- [ ] Schema validation rejects test files with unknown or non-lowercase category values, with an error message listing valid values
- [ ] Migration script converts all uppercase values to lowercase (`REQUIREMENT` → `requirement`, `CONDITIONAL` → `conditional`, `SEQUENCE` → `sequence`, `PROHIBITION` → `prohibition`)
- [ ] `skill-test.md` is updated to reflect the final authoritative allowed set
- [ ] Tests passing, no regressions
