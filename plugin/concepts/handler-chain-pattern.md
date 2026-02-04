# Handler Chain Pattern

Technique for hiding intermediate tool calls from user output by chaining skills where each
skill's handler performs data collection invisibly.

## Problem

When a workflow requires multiple steps (read config, discover task, create worktree, execute,
review, merge), each step traditionally appears as a visible tool call in the user's terminal:

```
● Bash(read config)           ← visible
● Task(prepare)               ← visible
● Bash(verify worktree)       ← visible
● Task(execute)               ← visible
```

This creates noisy output that obscures the actual workflow progress.

## Solution

**Skill handlers run invisibly** before skill content loads. By moving data-gathering logic
into handlers and chaining skills together, intermediate steps become invisible:

```
/cat:work
  ⎿  Skill loaded (handler ran config + prepare invisibly)
● Skill(work-execute)
  ⎿  Skill loaded (handler output Banner 2)
● Task(implement)             ← only meaningful work visible
● Skill(work-review)
  ⎿  Skill loaded (handler output Banner 3)
● Task(review)                ← only meaningful work visible
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│ Skill A invoked                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ Handler A runs (INVISIBLE to user):                                 │
│   - subprocess: read config                                         │
│   - subprocess: run discovery script                                │
│   - subprocess: create worktree                                     │
│   - Returns: JSON data + Banner via additionalContext               │
├─────────────────────────────────────────────────────────────────────┤
│ SKILL.md loads with pre-collected data                              │
│   - Parses JSON from handler output                                 │
│   - Invokes Skill B with data as arguments                          │
└─────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Skill B invoked (with JSON args)                                    │
├─────────────────────────────────────────────────────────────────────┤
│ Handler B runs (INVISIBLE):                                         │
│   - Outputs next progress banner                                    │
│   - Any additional preprocessing                                    │
├─────────────────────────────────────────────────────────────────────┤
│ SKILL.md spawns Task for actual work                                │
│   - Task runs (VISIBLE but meaningful)                              │
│   - On completion, invokes Skill C                                  │
└─────────────────────────────────────────────────────────────────────┘
```

## Implementation Rules

### 1. Handlers Do Data Collection

Move all "gathering" operations into the handler:

```python
# skill_handlers/work_handler.py
class WorkHandler:
    def handle(self, context: dict) -> str | None:
        # These run INVISIBLY
        config = self._read_config(project_root)
        task = self._discover_task(project_root, session_id)
        worktree = self._create_worktree(task)

        # Return data for skill to use
        return f"""HANDLER_DATA:
```json
{json.dumps({"config": config, "task": task, "worktree": worktree})}
```

{self._render_banner(task["id"], "preparing")}
"""
```

### 2. Skills Parse Handler Data and Chain

```markdown
# SKILL.md

## Step 1: Parse Handler Data

Extract JSON from HANDLER_DATA section in context.

## Step 2: Route Based on Status

| Status | Action |
|--------|--------|
| READY | Invoke `/cat:work-execute {task_json}` |
| NO_TASKS | Output NO_TASKS box, done |
| ERROR | Output error, done |
```

### 3. Chain Skills via Skill Tool

Each phase is a separate skill that invokes the next:

```
/cat:work → handler prepares → invokes work-execute
/cat:work-execute → handler outputs banner → spawns Task → invokes work-review
/cat:work-review → handler outputs banner → spawns Task → invokes work-merge
/cat:work-merge → handler outputs banner → spawns Task → done
```

## When to Use

**Good fit:**
- Multi-phase workflows with progress indicators
- Workflows requiring config/discovery before execution
- When intermediate tool calls are noise, not signal

**Poor fit:**
- Simple single-step operations
- When user needs to see intermediate steps for debugging
- When handler execution time would cause perceived lag

## Example: Work Workflow

| Phase | Handler Does | Skill Does |
|-------|--------------|------------|
| work | Read config, discover task, create worktree, Banner 1 | Parse data, invoke work-execute |
| work-execute | Banner 2 | Spawn implementation Task, invoke work-review |
| work-review | Banner 3 | Spawn review Task, invoke work-merge |
| work-merge | Banner 4 | Spawn merge Task, output completion |

## Testing

Handler logic should have unit tests since it runs synchronously and can't be debugged
interactively:

```python
def test_work_handler_discovers_task():
    handler = WorkHandler()
    result = handler.handle({"project_root": "/test", "session_id": "abc"})
    assert "HANDLER_DATA:" in result
    assert '"issue_id":' in result
```

## Tradeoffs

| Benefit | Cost |
|---------|------|
| Cleaner user output | More complex handler code |
| Progress visible at right moments | Handler errors harder to debug |
| Data collection parallelizable in handler | More skill files to maintain |

## Related Patterns

- **Batch Executor**: Single subagent handles multiple phases (alternative approach)
- **Silent Preprocessing**: Handler runs scripts before skill loads (used here)
- **Skill Composition**: Skills invoke other skills (foundation of this pattern)
