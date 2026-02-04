---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
user-invocable: false
---

# Render Diff

## MANDATORY OUTPUT REQUIREMENT (M341, M395)

**YOUR ONLY JOB**: Copy-paste ALL content between the START and END markers below. Do NOT summarize, interpret, or reformat.

---

<!-- START COPY HERE -->

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-render-diff.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

<!-- END COPY HERE -->

**FAIL-FAST:** If you do NOT see a diff above, then preprocessing FAILED. STOP. Do NOT manually run scripts.

---

## Reference (do not output this section)

The preprocessing has already:
- Calculated column widths and alignments
- Rendered box borders with correct padding
- Applied word-level diff highlighting
- Added file headers and hunk context
- Generated the legend

Do NOT modify, reformat, or manually reconstruct any part of the output.

---

## Output Structure Reference

> **NOTE:** This section describes WHAT the output contains, not HOW to render it.
> The preprocessing script produces all rendering. You only need to copy-paste.

The script output output contains:

**Per-hunk boxes** with:
- File header row showing the file path
- Column headers: Old line number, Symbol, New line number, Content
- Hunk context (function/class name) marked with special symbol
- Diff lines with appropriate symbols for additions/deletions/context
- Word-level highlighting using bracket notation for changed portions

**Special cases handled by the handler:**
- Binary files: Simplified box noting binary change
- Renamed files: Shows old path to new path
- Long lines: Wrapped with continuation marker
- Whitespace changes: Visible markers for tabs and spaces

**Legend box** appears once at the end, showing only the symbols used in that diff.

---

## Configuration

The script reads `terminalWidth` from `.claude/cat/cat-config.json` to determine box width.

---

## Integration with Approval Gates

### Complete File Coverage (MANDATORY)

Before invoking render-diff, enumerate ALL changed files to ensure complete coverage:

```bash
# Step 1: List all changed files
git diff --name-only "${BASE_BRANCH}..HEAD"

# Step 2: Generate diff for ALL files (no path filtering)
git diff "${BASE_BRANCH}..HEAD"
```

**Anti-pattern**: Manually specifying paths based on memory leads to incomplete diffs.

---

## Verification

- [ ] Script Output diff found in context
- [ ] Content output exactly as provided (no manual reconstruction)
- [ ] All changed files included in the diff
- [ ] No Bash tool invocations shown to user

---

## Related Skills

- `cat:stakeholder-review` - Uses render-diff for showing changes to reviewers
