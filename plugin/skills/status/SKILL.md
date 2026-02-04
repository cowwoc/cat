---
description: Display project progress - versions, issues, and completion status
model: haiku
context: fork
allowed-tools:
  - Read
---

# CAT Status Display

## MANDATORY OUTPUT REQUIREMENT (M341)

**YOUR ONLY JOB**: Copy-paste the content below VERBATIM. Do NOT summarize, interpret, or reformat.

---

## SCRIPT OUTPUT: Status Box (COPY THIS EXACTLY)

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

**FAIL-FAST (ESCALATE-A008):** If you do NOT see a status box directly above (containing
`╭──` and issue lists), then preprocessing FAILED. STOP. Do NOT manually run scripts.

**After the status box, output:**

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a issue | `/cat:work {version}-<issue-name>` |
| [**2**] | Add new issue | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

---

## Verification

- [ ] Status box displayed with all right-side `│` characters aligned vertically
- [ ] Inner boxes (major versions) have consistent width with each other
- [ ] NEXT STEPS table displayed
- [ ] Legend displayed

---

## Architecture: Direct Preprocessing

This skill uses **direct preprocessing** - the script collects all inputs from the filesystem
and renders the complete output before Claude sees the skill.

**Benefits:**
- Status is computed BEFORE Claude sees the skill content
- No possibility of manual reconstruction errors
- Simpler skill - just output what's there

**How it works:**
1. User invokes `/cat:status`
2. Claude Code expands the exclamation-backtick command during skill loading
3. Script reads STATE.md files and renders the status box
4. Claude receives the skill with actual status data embedded
5. Claude outputs the script output content verbatim
