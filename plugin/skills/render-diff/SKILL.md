---
description: >
  MANDATORY: Use BEFORE showing ANY diff to user - transforms git diff into 4-column table.
  Required for approval gates, code reviews, change summaries.
user-invocable: false
---

# Render Diff

## MANDATORY OUTPUT REQUIREMENT (M341, M395, M401)

**STOP. DO NOT ASK QUESTIONS. DO NOT CHECK GIT STATUS. DO NOT ANALYZE CONTEXT.**

**YOUR ONLY JOB**: Copy-paste ALL content between the START and END markers below. Do NOT summarize, interpret, or reformat.

This skill was invoked to DISPLAY output, not to gather information. Output the content NOW.

---

<!-- START COPY HERE -->

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-render-diff.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

<!-- END COPY HERE -->

**FAIL-FAST:** If you do NOT see a diff above, then preprocessing FAILED. STOP. Do NOT manually run scripts.

<!--
INTERNAL REFERENCE (NOT FOR AGENT - M402)
=========================================
The sections below are for human maintainers only.
They were REMOVED from agent-visible content because they primed
analytical/verification behavior instead of verbatim output.

## Reference
The preprocessing has already:
- Calculated column widths and alignments
- Rendered box borders with correct padding
- Applied word-level diff highlighting
- Added file headers and hunk context
- Generated the legend

## Output Structure
Per-hunk boxes with file headers, column headers, diff lines with symbols,
word-level highlighting. Legend box at end shows symbols used.

## Configuration
Script reads terminalWidth from .claude/cat/cat-config.json

## Integration with Approval Gates
Before invoking, enumerate ALL changed files with git diff --name-only.
Anti-pattern: Manually specifying paths based on memory.

## Verification (for human review)
- Script Output diff found in context
- Content output exactly as provided
- All changed files included
- No Bash tool invocations shown to user

## Related Skills
- cat:stakeholder-review
-->
