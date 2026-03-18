# Plan: fix-migration-21-phase7-double-blank-line

## Goal
Fix Phase 7 of `plugin/migrations/2.1.sh` so it does not insert an empty line between
`## Execution Waves` content and the following `## ` section heading.

## Satisfies
- None (bugfix)

## Root Cause
The Phase 7 awk script unconditionally emits `print ""` before the next `## ` heading that
follows the migrated Execution Steps section. Markdown sections already end with a blank line,
so the unconditional blank produces a double blank line in the output.

```awk
in_section && /^## / {
    in_section = 0
    print ""   # <-- always printed, even if previous line was already blank
    print
    next
}
```

## Files to Modify
- `plugin/migrations/2.1.sh` — Phase 7 awk block (around line 621)

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- In the Phase 7 awk block, track whether the previous printed line was blank via a
  `last_blank` variable; only emit the separator blank line when `!last_blank`
  - Files: `plugin/migrations/2.1.sh` (Phase 7 awk block)

## Post-conditions
- [ ] Phase 7 awk script tracks `last_blank` and conditionally emits the separator
- [ ] `bash -n plugin/migrations/2.1.sh` passes without syntax errors
- [ ] A PLAN.md whose `## Execution Steps` section ends with a blank line before the
      next `## ` heading produces exactly one blank line after migration (not two)
