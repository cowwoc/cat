---
description: Display project progress - versions, issues, and completion status
model: haiku
context: fork
allowed-tools:
  - Read
---

# CAT Status Display

## Purpose

User sees a correctly-aligned, complete project status display with actionable next steps.

---

## Procedure

### Step 1: Output the pre-rendered status box

**COPY AND OUTPUT THIS BOX EXACTLY:**

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

**FAIL-FAST (ESCALATE-A008):** If you do NOT see a status box directly above (containing
`╭──` and issue lists), then preprocessing FAILED. In this case:

1. **STOP** - do NOT proceed
2. Run: `${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`
3. Copy-paste that output exactly

**NEVER manually construct status boxes. NEVER skip this step.**

### Step 2: Output next steps and legend

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
5. Claude outputs the pre-rendered content verbatim
