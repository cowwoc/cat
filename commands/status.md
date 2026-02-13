---
name: cat:status
description: Show hierarchy status with visual tree
model: haiku
context: fork
allowed-tools:
  - Bash
---

<objective>

Display the complete CAT hierarchy status with a visual tree showing all major versions, minor versions,
and tasks with their current status.

Provides situational awareness for project progress.

</objective>

<process>

<step name="collect-data">

**Collect status data:**

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/status-data.sh" .claude/cat
```

This outputs JSON with:
- `project_name`: Project name from PROJECT.md
- `percent`: Overall completion percentage
- `completed`/`total`: Task counts
- `current_minor`: The current minor version being worked on
- `first_pending`: Next executable task name
- `in_progress_task`: Currently in-progress task (if any)
- `majors[]`: Array of major versions with nested minors and their stats
- `pending_tasks[]`: List of pending tasks in current minor

If the script fails with "No planning structure found", inform user to run `/cat:init`.

**MANDATORY: Use ONLY the data from status-data.sh and the gate collection below.**
If the script output seems incomplete (e.g., empty pending_tasks despite work remaining), that indicates
a script bug - report it after rendering. Never run additional bash commands to "supplement" the data.

**Additionally, collect gate status:**

For each version, read its PLAN.md and extract the `## Gates` section:
```bash
# For each minor version, check gates
for version_dir in .claude/cat/v*/v*.*; do
  plan_file="$version_dir/PLAN.md"
  [ -f "$plan_file" ] && grep -A 20 "^## Gates" "$plan_file" 2>/dev/null
done
```

Parse gate conditions and evaluate their status:
- For entry gates: check if conditions are met
- For exit gates: count how many conditions are satisfied

Store gate status for each version:
- `entry_gate_satisfied`: boolean
- `entry_gate_blocking`: string (unmet condition, if any)
- `exit_gate_progress`: string (e.g., "2/3 conditions met")

**Also check for exit gate tasks:**

Exit gate tasks are identified by the `[task]` prefix in the `### Exit` section of PLAN.md.
For each version, parse exit gate tasks:
```bash
for version_dir in .claude/cat/v*/v*.*; do
  plan_file="$version_dir/PLAN.md"
  # Look for [task] prefix in Exit section
  [ -f "$plan_file" ] && sed -n '/^### Exit/,/^###\|^##/p' "$plan_file" | grep -E '^\s*-\s*\[task\]' 2>/dev/null
done
```

Exit gate tasks cannot execute until all non-gating tasks in the version are complete.
Track for each exit gate task:
- `is_exit_gate`: true
- `non_gating_incomplete`: count of incomplete non-gating tasks

</step>

<step name="render">

**Render adventure-style visual tree:**

**IMPORTANT: Output styled text DIRECTLY - do NOT use Bash tool for rendering.**

Claude Code shows all Bash tool invocations in the terminal. To display clean output without
tool call wrappers, output the styled text directly as part of your response.

**CRITICAL: No vertical borders - use horizontal borders only.**

Per display-standards.md, avoid vertical borders (`║`, `│`) because emoji width varies across
terminals. Horizontal-only borders eliminate padding calculation problems.

**CRITICAL: Do NOT wrap output in code blocks (M125).**

Markdown bold (`**text**`) renders correctly when output directly, but shows as literal asterisks
inside triple-backtick code blocks. Output the status display as plain text, NOT inside ``` blocks.

**CRITICAL: Keep version info and metrics on the SAME LINE.**

Do not manually wrap lines. Let version description, counts, and gate status all appear on one line.
The box width should accommodate the content, not force line breaks.

**Use this exact format (substitute actual values):**

╭─── 🗺️ YOUR ADVENTURE - {PROJECT_NAME} ────────────────────────────╮
│                                                                    │
│  📊 Overall: [████████████████████████████████████░░░░░░░░░] **{PERCENT}%**
│  🏆 **{COMPLETED}/{TOTAL}** tasks complete                         │
│  ⚙️ Mode: {Interactive|YOLO}                                       │
│                                                                    │
│  ╭─── 📦 v{N}: {Major Version Name} ────────────────────╮          │
│  │                                                      │          │
│  │  ☑️ v{N}.{M}: {Minor description} ({completed}/{total})         │
│  │  ☑️ v{N}.{M}: {Another completed minor} ({completed}/{total})   │
│  │                                                      │          │
│  │  🔄 **v{N}.{M}: {Current minor description}** ({completed}/{total}) | Exit: {X}/{Y}
│  │    🔳 {pending-task-1}                               │          │
│  │    🔳 {pending-task-2}                               │          │
│  │    🔳 {pending-task-3}                               │          │
│  │    📋 ... and {N} more pending tasks                 │          │
│  │                                                      │          │
│  │  🔳 v{N}.{M}: {Future minor} ({completed}/{total})   │          │
│  │    🚧 Entry gate: waiting on v{N}.{M-1} completion   │          │
│  │                                                      │          │
│  ╰──────────────────────────────────────────────────────╯          │
│                                                                    │
│  ──────────────────────────────────────────────────────────────    │
│  🎯 **Current Quest:** v{N}.{M} - {Minor version description}      │
│  📋 **Available tasks:** {N} pending                               │
│  ──────────────────────────────────────────────────────────────    │
│                                                                    │
╰────────────────────────────────────────────────────────────────────╯

**Gate status indicators (show inline with version when applicable):**

For versions with unsatisfied entry gates:

│  │  🚧 v{N}.{M}: {Minor description} ({completed}/{total})   │          │
│  │    🚧 Entry gate: waiting on {unmet condition}            │          │

For current/in-progress versions, show exit gate progress inline:

│  │  🔄 **v{N}.{M}: {Current minor}** ({completed}/{total}) | Exit: 2/3 conditions

**🚀 NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/cat:execute-task {version}-<task-name>` |
| [**2**] | Add new task | `/cat:add-task {version}` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

**Progress bar format:** Use block characters: `█` for filled, `░` for empty.
The bar should be **45 characters** wide (filled + empty = 45) to match the border width.
Example for 80%: `[████████████████████████████████████░░░░░░░░░]` (36 filled + 9 empty)
This represents **overall project progress** across all versions.

**Status symbols (emoji):**
- ☑️ completed (for done tasks and 100% complete minors)
- 🔄 in-progress (for current minor version AND any actively running task)
- 🔳 pending (no unsatisfied entry gate)
- 🚫 blocked (task-level dependencies not met)
- 🚧 gate waiting (entry gate not satisfied - applies to ALL such versions, not just immediate next)

**CRITICAL: Entry gate evaluation algorithm:**
For EACH version after the current 🔄 version:
1. Check if its entry gate is satisfied (predecessor complete?)
2. If NOT satisfied → use 🚧 (regardless of position in sequence)
3. If satisfied → use 🔳
Do NOT use 🔳 for versions whose entry gates depend on incomplete predecessors.

**Task display rules (CRITICAL):**
- **Completed versions (☑️):** Summary line only, no tasks
- **Current/in-progress version (🔄):** Show up to 5 pending tasks, then "📋 ... and {N} more"
- **Blocked versions (🚧):** Summary line + gate blocking message ONLY, no individual tasks
- **Future pending versions (🔳):** Summary line only, no tasks

**Only the CURRENT active version (marked 🔄) displays its pending tasks.**

**Key point:** Output this text directly in your response. Do NOT wrap it in Bash tool calls.
The visual structure renders correctly in the terminal without needing ANSI escape codes.

**When a task is actively in progress, show it like:**

│  │  🔄 **v{N}.{M}: {Current minor description}** ({completed}/{total})
│  │    🔄 {in-progress-task}                              │          │
│  │    🔳 {pending-task-1}                                │          │
│  │    🔳 {pending-task-2}                                │          │

</step>

<step name="blockers">

**Identify blocked tasks and versions:**

If any tasks are blocked by dependencies, list them:

**🚫 BLOCKED TASKS:**
🚫 v1.1/optimize-ir - waiting on: generate-ir
🚫 v2.0/emit-code - waiting on: v1.5-core-complete

If any versions have unsatisfied entry gates, list them:

**🚧 ENTRY GATES NOT MET:**
🚧 v0.6 - waiting on: v0.5 completion
🚧 v1.0 - waiting on: Major 0 completion
🚧 v1.2 - waiting on: manual approval

If any tasks are exit gate tasks waiting on non-gating tasks, list them:

**🚧 EXIT GATE TASKS WAITING:**
🚧 v0.5/validate-spring-framework-parsing - waiting on: 4 non-gating tasks
   📋 Incomplete: fix-contextual-keyword-declarations, fix-lambda-arrow-in-parenthesized-context, ...

To override an entry gate for a specific task:
```
/cat:execute-task {version}-{task} --override-gate
```

To configure gates:
```
/cat:config → 📊 Version Gates
```

</step>

<step name="next">

**Adapt NEXT STEPS based on state:**

The table options change based on current state:

| State | Option 1 | Option 2 |
|-------|----------|----------|
| Has pending tasks | Execute a task | Add new task |
| All tasks complete for minor | Add new task | Add minor version |
| All minors complete for major | Add minor version | Add major version |
| All complete | 🎉 Quest complete! | (no options needed) |

</step>

</process>

<output_format>

The status output should be:
1. Compact but informative
2. Easy to scan visually
3. Show progress at all levels
4. Highlight current position
5. Suggest next action

</output_format>

<success_criteria>

- [ ] All major versions displayed
- [ ] All minor versions under each major displayed
- [ ] All tasks with correct status emojis (☑️ 🔄 🔳 🚫 🚧)
- [ ] Gate status shown for versions with entry/exit gates
- [ ] Versions with unsatisfied entry gates show 🚧 indicator
- [ ] Progress bar accurate
- [ ] Current minor version bolded with **markdown**
- [ ] NEXT STEPS table renders with bold [**1**] and [**2**]
- [ ] Legend displayed (including 🚧 Gate Waiting)
- [ ] Output is NOT wrapped in code blocks (``` breaks bold rendering)
- [ ] Blocked tasks and gate-blocked versions listed (if any)
- [ ] Exit gate tasks waiting on non-gating tasks shown with 🚧 indicator
- [ ] Pending tasks shown ONLY for current 🔄 version, NOT for blocked 🚧 versions

</success_criteria>
