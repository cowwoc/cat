---
name: cat:status
description: Display project progress - versions, tasks, and completion status
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
---

# CAT Status Display

## Purpose

User sees a correctly-aligned, complete project status display with actionable next steps.

---

## Prerequisites

- CAT project initialized (`.claude/cat` directory exists)
- Status data collection script: `${CLAUDE_PLUGIN_ROOT}/scripts/status.sh`
- Box rendering script: `${CLAUDE_PLUGIN_ROOT}/scripts/build-box-lines.py`

---

## Functions

### select_emoji(minor) -> emoji

Select status emoji for a minor version based on completion state.

**Definition**:
```
if minor.completed == minor.total AND minor.total > 0:
  return "☑️"
if minor.inProgress is not empty OR minor.id == current.minor:
  return "🔄"
return "🔳"
```

**Additional states** (for tasks):
- `🚫` = Blocked (task cannot proceed due to dependency)
- `🚧` = Gate waiting (entry/exit conditions not met)

### build_progress_bar(percent, width=25) -> string

Generate visual progress bar.

**Definition**:
```
filled = floor(percent * width / 100)
empty = width - filled
return "█"×filled + "░"×empty
```

**Example**:
```
build_progress_bar(45, 25) = "███████████░░░░░░░░░░░░░░"
```

---

## Procedure

### Step 1: Require pre-computed display

**MANDATORY:** Check conversation context for "PRE-COMPUTED STATUS DISPLAY".

The UserPromptSubmit hook pre-computes the entire status display to prevent alignment errors.

**If found**:
1. Locate the box output between "PRE-COMPUTED STATUS DISPLAY" and "NEXT STEPS table"
2. Output the pre-computed box **directly without preamble** (no "I can see...", no "Let me output...")
3. Output the NEXT STEPS table EXACTLY as shown
4. Output the legend
5. Skip to Verification

**Silent output (M194):** Do NOT announce or explain the pre-computed content. Simply output it.

**If NOT found**: **FAIL immediately** with:

```
ERROR: Pre-computed status display not found in context.

The hook (precompute-status-display.sh) should have provided this.

Troubleshooting:
1. Check if hooks/precompute-status-display.sh exists in plugin
2. Verify hook is registered in hooks.json
3. Check for hook errors in output above
4. Ensure .claude/cat directory exists (run /cat:init if needed)

Do NOT attempt to render the status box manually - it will have alignment errors.
```

**Why fail-fast?** Box alignment requires precise emoji width calculations that
LLMs cannot perform reliably. The hook uses Python's unicodedata module for
accurate widths. Manual rendering defeats the purpose of extraction.

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
- [ ] NEXT STEPS table displayed with correct active version
- [ ] Legend displayed

---

## External Computation

This skill relies on pre-computation via UserPromptSubmit hook to prevent alignment errors.

**Hook**: `hooks/precompute-status-display.sh`
**Trigger**: User invokes `/cat:status`
**Output**: Complete rendered box via additionalContext

The hook:
1. Runs `status.sh` to collect JSON data
2. Runs `build-box-lines.py` to compute exact line padding
3. Returns pre-rendered display for direct output

**Why pre-compute?** LLMs cannot reliably calculate emoji widths or character padding.
Pre-computing ensures correct alignment every time.
