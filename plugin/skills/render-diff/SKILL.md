---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
user-invocable: false
---

# Render Diff

## Purpose

Transform raw git diff output into a 4-column table format optimized for approval gate reviews.
Each hunk is rendered as a self-contained box with file header, making diffs easy to review.

---

## Procedure

### Step 1: Require output template (MANDATORY)

**Check context for "OUTPUT TEMPLATE RENDER-DIFF OUTPUT".**

If found:
1. Output the rendered diff content **directly** - no preamble, no Bash commands
2. The content is already formatted with 4-column tables and box characters
3. Do NOT wrap in code blocks or show any tool invocations

If NOT found during an approval gate: **FAIL immediately**.

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/check-hooks-loaded.sh" "diff output" "render-diff"
if [[ $? -eq 0 ]]; then
  echo "ERROR: Output template diff not found. Check:"
  echo "1. Handler is registered in skill_handlers/__init__.py"
  echo "2. Handler file exists in plugin/hooks/skill_handlers/"
  echo "3. Base branch is detectable from worktree/branch name"
fi
```

Output the error and STOP. Do NOT attempt manual Bash computation or manual box rendering.

**Why fail-fast (M238):** Running `git diff | render-diff.py` via Bash shows the command
execution to the user, breaking the clean output experience. Pre-computation hides the
implementation details.

### Step 2: Output the rendered diff

Copy-paste the output template content exactly as provided. The handler has already:
- Calculated column widths and alignments
- Rendered box borders with correct padding
- Applied word-level diff highlighting
- Added file headers and hunk context
- Generated the legend

Do NOT modify, reformat, or manually reconstruct any part of the output.

---

## Output Structure Reference

> **NOTE:** This section describes WHAT the output contains, not HOW to render it.
> The handler produces all rendering. You only need to copy-paste.

The output template contains:

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

- [ ] Output template found in context
- [ ] Content output exactly as provided (no manual reconstruction)
- [ ] All changed files included in the diff
- [ ] No Bash tool invocations shown to user

---

## Related Skills

- `cat:stakeholder-review` - Uses render-diff for showing changes to reviewers
