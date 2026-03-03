# Plan: fix-migration-21-execution-steps-detail-loss

## Goal
Fix Phase 7 of `plugin/migrations/2.1.sh` so that migrating `## Execution Steps` to `## Execution Waves`
preserves all step detail (sub-bullets, file lists, verification criteria, code blocks, phase headings,
etc.) instead of discarding everything except top-level step title lines.

## Satisfies
- None (infrastructure bugfix)

## Root Cause
Phase 7 extracts step content with a pipeline ending in `grep "^- "`, which discards every line that
does not start with `- `. This throws away all sub-content nested under each numbered step:
descriptions, file lists, verify criteria, code blocks, blank lines, and inner headings like
`### Phase 2`. Only the top-level numbered step lines survive (converted to bullet titles).

```bash
# Current (broken): strips all sub-content
steps_content=$(sed -n '/^## Execution Steps/,/^## /p' "$plan_file" | tail -n +2 | \
    grep -v "^## " | sed 's/^[[:space:]]*[0-9]\+\.[[:space:]]\(.*\)$/- \1/' | \
    grep "^- " || true)
```

## Fix Approach
Preserve the entire body of the `## Execution Steps` section verbatim, only:
1. Rename the section heading from `## Execution Steps` to `## Execution Waves`
2. Insert a `### Wave 1` subheading immediately after the new section heading
3. Keep all content under the section unchanged

This is the minimal correct transformation: the heading name changes, a Wave 1 grouping is added, and
nothing else is altered.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Edge cases around trailing newlines; files where Execution Steps is the last section
- **Mitigation:** Reuse the existing awk-based approach already proven in other phases; test with
  representative files

## Files to Modify
- `plugin/migrations/2.1.sh` — replace Phase 7 implementation with content-preserving awk transform

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Replace the Phase 7 implementation in `plugin/migrations/2.1.sh`
  - Files: `plugin/migrations/2.1.sh`
  - Replace the current shell pipeline (sed + grep) with a single `awk` command that:
    1. Matches `^## Execution Steps` and emits `## Execution Waves\n\n### Wave 1` instead
    2. Passes all subsequent lines through unchanged until EOF or the next `^## ` heading
    3. Handles the case where `## Execution Steps` is the last section (no trailing `^## ` to trigger)
  - Idempotency guard already present (`grep -q "^## Execution Waves"`) must remain
- Add or update unit tests for Phase 7 in `client/src/test/`
  - Files: relevant test file under `client/src/test/`
  - Test cases:
    - Simple numbered steps → preserved verbatim under `### Wave 1`
    - Steps with multi-line sub-content (file lists, code blocks, inner headings) → all preserved
    - Steps followed by another section → section boundary respected
    - Steps as last section (no trailing heading) → content fully preserved
    - Already-migrated file (has `## Execution Waves`) → skipped (idempotent)
    - No `## Execution Steps` present → file unchanged
- Run `mvn -f client/pom.xml test` and confirm all tests pass

## Post-conditions
- Phase 7 of `plugin/migrations/2.1.sh` preserves all content beneath `## Execution Steps`
- Running Phase 7 on any PLAN.md file produces an output where:
  - The heading reads `## Execution Waves`
  - A `### Wave 1` subheading appears immediately after
  - Every line that existed below the old `## Execution Steps` heading is present, in order, unchanged
- Running the migration a second time on already-migrated files is a no-op
- All existing client tests pass
