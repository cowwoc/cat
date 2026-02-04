---
description: Display project progress - versions, issues, and completion status
model: haiku
context: fork
allowed-tools:
  - Read
---

# CAT Status Display

## MANDATORY OUTPUT REQUIREMENT (M341, M395, M401, M402)

**STOP. DO NOT ASK QUESTIONS. DO NOT CHECK GIT STATUS. DO NOT ANALYZE CONTEXT.**

**YOUR ONLY JOB**: Copy-paste ALL content between the START and END markers below. Do NOT summarize, interpret, or reformat.

This skill was invoked to DISPLAY output, not to gather information. Output the content NOW.

*(Enforced by hook M402 - Stop hook blocks if status box missing from response)*

---

<!-- START COPY HERE -->

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

**FAIL-FAST (ESCALATE-A008):** If you do NOT see a status box directly above (containing
`╭──` and issue lists), then preprocessing FAILED. STOP. Do NOT manually run scripts.

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

<!-- END COPY HERE -->

<!--
INTERNAL REFERENCE (NOT FOR AGENT - M402)
=========================================
The sections below are for human maintainers only.
They were REMOVED from agent-visible content because they primed
analytical/verification behavior instead of verbatim output.

## Verification (for human review)
- Status box displayed with aligned right-side │ characters
- Inner boxes have consistent width
- NEXT STEPS table displayed
- Legend displayed

## Architecture
This skill uses direct preprocessing - script renders output before
Claude sees the skill. Claude should output the content verbatim.
-->
