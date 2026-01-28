---
description: Display project progress - versions, tasks, and completion status
model: haiku
context: fork
allowed-tools:
  - Read
---

# CAT Status Display

## Purpose

User sees a correctly-aligned, complete project status display with actionable next steps.

---

## Pre-rendered Status Display

!`${CLAUDE_PLUGIN_ROOT}/scripts/get-status-display.sh --project-dir "${CLAUDE_PROJECT_DIR}"`

---

## Procedure

### Step 1: Output the status display

The status box above was pre-rendered via silent preprocessing.

**Output it exactly as shown** - do NOT modify, recalculate, or reconstruct.

### Step 2: Output next steps and legend

**After the status box, output:**

**NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/cat:work {version}-<task-name>` |
| [**2**] | Add new task | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

---

## Verification

- [ ] Status box displayed with all right-side `│` characters aligned vertically
- [ ] Inner boxes (major versions) have consistent width with each other
- [ ] NEXT STEPS table displayed
- [ ] Legend displayed

---

## Why Silent Preprocessing?

This skill uses silent preprocessing (exclamation-backtick syntax) instead of OUTPUT TEMPLATE injection.

**Benefits:**
- Status is computed BEFORE Claude sees the skill content
- No possibility of manual reconstruction errors
- Simpler skill - just output what's there
- No handler registration or hook complexity

**How it works:**
1. User invokes `/cat:status`
2. Claude Code loads the skill and expands the command pattern
3. Script runs and outputs the rendered status box
4. Claude receives the skill with actual status data embedded
5. Claude outputs the pre-rendered content verbatim
