---
name: cat:status
description: Display project progress - versions, tasks, and completion status
model: haiku
context: fork
allowed-tools:
  - Bash
  - Read
---

<objective>

Display the CAT hierarchy status with a visual tree showing all major versions, minor versions,
and tasks with their current status.

</objective>

<process>

<step name="collect-data">

**Run the status script to collect project data:**

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/status.sh" .claude/cat
```

The script outputs JSON with this schema:

```json
{
  "project": "Project Name",
  "overall": {
    "percent": 45,
    "completed": 10,
    "total": 22
  },
  "current": {
    "minor": "v1.2",
    "inProgressTask": "task-name",
    "pendingTasks": ["task-a", "task-b"]
  },
  "majors": [
    {"id": "v1", "name": "Major Version Name"}
  ],
  "minors": [
    {
      "id": "v1.2",
      "major": "v1",
      "description": "Minor description",
      "completed": 3,
      "total": 5,
      "inProgress": "current-task",
      "tasks": [{"name": "task-name", "status": "completed"}]
    }
  ]
}
```

If the script outputs an error JSON, inform user to run `/cat:init`.

</step>

<step name="render-display">

**Render the status display in open-border format:**

Using the JSON data, output this format. **EVERY major version gets its own inner box with ╭─ and ╰─.**

```
╭─
│ 📊 Overall: [████████░░░░░░░░░░░░░░░░░] {percent}%
│ 🏆 {completed}/{total} tasks complete
│
│ ╭─ {emoji} {major1.id}: {major1.name}
│ │
│ │  {emoji} {minor.id}: {minor.description} ({completed}/{total})
│ │  {emoji} {minor.id}: {minor.description} ({completed}/{total})
│ ╰─
│
│ ╭─ {emoji} {major2.id}: {major2.name}
│ │
│ │  {emoji} {minor.id}: {minor.description} ({completed}/{total})
│ │     🔄 {inProgressTask}
│ │     🔳 {pendingTask}
│ ╰─
│
│ 🎯 Active: {current.minor}
│ 📋 Available: {pendingTasks.length} pending tasks
╰─
```

**Structure rules:**
- Each major version starts with `│ ╭─` and its minors are indented with `│ │`
- Close each major's inner box with `│ ╰─` BEFORE starting the next major
- The active minor (with pending tasks) shows tasks indented below it
- Completed majors can be collapsed (show only summary line if all minors complete)

**Emoji rules:**
- ☑️ = Minor complete (completed == total && total > 0)
- 🔄 = Current active minor OR in-progress task
- 🔳 = Pending minor or task
- 🚫 = Blocked (task cannot proceed)
- 🚧 = Gate waiting (entry/exit conditions not met)

**Progress bar:** Use █ for filled, ░ for empty. Width = 25 characters.

**Truncation:** Long task/version names should be truncated with `...`
- Example: "very-long-task-na..." for names exceeding ~20 characters
- Keep first N-3 characters, append `...`

</step>

<step name="next-steps">

**After the status display, show:**

**🚀 NEXT STEPS**

| Option | Action | Command |
|--------|--------|---------|
| [**1**] | Execute a task | `/cat:work {version}-<task-name>` |
| [**2**] | Add new task | `/cat:add` |

---

**Legend:** ☑️ Completed · 🔄 In Progress · 🔳 Pending · 🚫 Blocked · 🚧 Gate Waiting

</step>

</process>

<success_criteria>

- [ ] JSON data collected from script
- [ ] Open-border status display rendered
- [ ] NEXT STEPS table displayed
- [ ] Legend displayed

</success_criteria>
