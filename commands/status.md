---
name: cat:status
description: Show hierarchy status with visual tree
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

</step>

<step name="render">

**Render adventure-style visual tree:**

**IMPORTANT: Output styled text DIRECTLY - do NOT use Bash tool for rendering.**

Claude Code shows all Bash tool invocations in the terminal. To display clean output without
tool call wrappers, output the styled text directly as part of your response.

**CRITICAL: Markdown rendering rules:**
- Text inside code fences (```) does NOT render markdown (bold shows as `**text**`)
- Text outside code fences DOES render markdown (**text** becomes bold)
- Use code fences only for box-drawing that needs alignment
- Put bold elements (current version, options) OUTSIDE code fences

**Use this exact format (substitute actual values):**

```
╔════════════════════════════════════════════════════════════════════╗
║  🗺️ YOUR ADVENTURE - {PROJECT_NAME}                                ║
╠════════════════════════════════════════════════════════════════════╣
║                                                                    ║
║  📊 Progress: [████████████░░░░░░░░] **{PERCENT}%**                ║
║  🏆 **{COMPLETED}/{TOTAL}** tasks complete                         ║
║                                                                    ║
║  ⚙️ Mode: {Interactive|YOLO}                                       ║
║                                                                    ║
╚════════════════════════════════════════════════════════════════════╝
```

┌─ 📦 v{N}: {Major Version Name} ────────────────────────────────────┐

☑️ v{N}.{M}: {Minor description} ({completed}/{total})
☑️ v{N}.{M}: {Another completed minor} ({completed}/{total})

🔄 **v{N}.{M}: {Current minor description}** ({completed}/{total})
   🔳 {pending-task-1}
   🔳 {pending-task-2}
   🔳 {pending-task-3}
   📋 ... and {N} more pending tasks

🔳 v{N}.{M}: {Future minor} ({completed}/{total})
   🚧 Entry gate: waiting on v{N}.{M-1} completion

└────────────────────────────────────────────────────────────────────┘

**Gate status indicators (show inline with version when applicable):**

For versions with unsatisfied entry gates:
```
🚧 v{N}.{M}: {Minor description} ({completed}/{total})
   🚧 Entry gate: waiting on {unmet condition}
```

For current/in-progress versions, show exit gate progress:
```
🔄 **v{N}.{M}: {Current minor}** ({completed}/{total}) | Exit: 2/3 conditions
```

═══════════════════════════════════════════════════════════════════════
🎯 **Current Quest:** v{N}.{M} - {Minor version description}
📋 **Available tasks:** {N} pending
═══════════════════════════════════════════════════════════════════════

**🚀 NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/cat:execute-task {version}-<task-name>` |
| [**2**] | Add new task | `/cat:add-task {version}` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

**Progress bar format:** Use block characters: `█` for filled, `░` for empty.
Example: `[████████████████░░░░]` for 80% complete.

**Status symbols (emoji):**
- ☑️ completed (for done tasks and 100% complete minors)
- 🔄 in-progress (for current minor version AND any actively running task)
- 🔳 pending
- 🚫 blocked (dependencies not met)

**Collapse completed versions:** For completed minor versions, show only summary line.
For current/incomplete versions, show task details.

**Key point:** Output this text directly in your response. Do NOT wrap it in Bash tool calls.
The visual structure renders correctly in the terminal without needing ANSI escape codes.

**When a task is actively in progress, show it like:**

🔄 **v{N}.{M}: {Current minor description}** ({completed}/{total})
   🔄 {in-progress-task}
   🔳 {pending-task-1}
   🔳 {pending-task-2}

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
- [ ] Current minor version bolded
- [ ] NEXT STEPS table renders with bold [**1**] and [**2**]
- [ ] Legend displayed (including 🚧 Gate Waiting)
- [ ] Blocked tasks and gate-blocked versions listed (if any)

</success_criteria>
