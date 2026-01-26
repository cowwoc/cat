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

## Procedure

> **CRITICAL:** This skill uses pre-computed output. Do NOT attempt manual computation.
> The handler already computed the display - your job is to OUTPUT it verbatim.

### Step 1: Require pre-computed display

**MANDATORY:** Check conversation context for "PRE-COMPUTED STATUS DISPLAY".

The UserPromptSubmit hook pre-computes the entire status display to prevent alignment errors.

**If found**:
1. Locate the box output between "PRE-COMPUTED STATUS DISPLAY" and "NEXT STEPS table"
2. Output the pre-computed box **directly without preamble** (no "I can see...", no "Let me output...")
3. Output the NEXT STEPS table EXACTLY as shown
4. Output the legend
5. Skip to Verification

**CRITICAL SELF-CHECK (M256):**
Before outputting, verify you are copy-pasting the EXACT pre-computed content:
- [ ] Content starts with `╭─` (top-left corner character)
- [ ] Content contains emoji like `📊`, `☑️`, `🔳` (NOT dots or question marks)
- [ ] All lines end with `│` at the same column position
- [ ] You are NOT typing/reconstructing the box - you are PASTING it

**If any checkbox fails:** You are NOT using the pre-computed content. STOP and find it.

**Silent output (M194):** Do NOT announce or explain the pre-computed content. Simply output it.

**Anti-pattern (M256):** Manually constructing a "similar-looking" status box instead of copy-pasting
the pre-computed output. Signs you are doing this wrong:
- Running Bash commands to gather status data yourself
- Building inner boxes with `build_inner_box()` or similar
- Typing box characters (`│`, `╭`, `─`) instead of pasting them
- Emojis appear as dots or question marks in your output

**If NOT found**: **FAIL immediately**.

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/check-hooks-loaded.sh" "status display" "/cat:status"
if [[ $? -eq 0 ]]; then
  # Hooks exist but still no output - other issue
  if [[ ! -d .claude/cat ]]; then
    echo "ERROR: No CAT project found. Run /cat:init to initialize."
  else
    echo "ERROR: Pre-computed status display not found. Check for hook errors above."
  fi
fi
```

Output the error and STOP. Do NOT attempt to render manually.

**Why fail-fast?** Box alignment requires precise emoji width calculations that
LLMs cannot perform reliably. The hook uses Python's unicodedata module for
accurate widths. Manual rendering defeats the purpose of extraction.

**Anti-pattern (M242):** Do NOT attempt workarounds when pre-computed output is missing:
- ❌ Running handler directly via `python3 -c` (fails: relative imports)
- ❌ Calling handler functions manually (fails: missing context)
- ❌ Building status display with Bash (fails: emoji alignment)
- ✅ Output the ERROR message and STOP

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

## External Computation (Reference Only)

> **NOTE:** This section is for understanding WHY pre-computation exists.
> You do NOT need to understand or use any of these details.
> Your only job: copy-paste the pre-computed output.

This skill relies on pre-computation via UserPromptSubmit hook to prevent alignment errors.

**Handler**: `hooks/skill_handlers/status_handler.py`
**Trigger**: User invokes `/cat:status`
**Output**: Complete rendered box via additionalContext

The handler:
1. Collects status data directly from `.claude/cat` directory structure
2. Uses `build_box_lines.py` to compute exact line padding
3. Returns pre-rendered display for direct output

**Why pre-compute?** LLMs cannot reliably calculate emoji widths or character padding.
Pre-computing ensures correct alignment every time.

> **Note:** Emoji meanings are included in the pre-computed Legend line.
> They are NOT documented separately here to prevent manual construction attempts.

