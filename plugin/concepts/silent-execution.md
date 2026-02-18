<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Silent Execution Pattern

Run commands invisibly through skill preprocessing so users see clean output instead of noisy
Bash tool calls.

## Problem

When a workflow runs shell commands via the Bash tool, each call appears in the user's terminal:

```
● Bash(get-next-task-box --completed-issue 2.1-fix-bug ...)    ← visible noise
● Bash(issue-lock.sh release ...)                               ← visible noise
```

Users see implementation details instead of meaningful results.

## Solution

Route the command through a skill's preprocessor directive (`!` backtick). The command runs
invisibly during skill loading and its output appears inline as skill content.

## How It Works

Claude Code processes skill content in this order:

1. **Argument substitution** — `$ARGUMENTS`, `$name`, `$1`, `$ARGUMENTS[0]` replaced with values
2. **Variable substitution** — `${CLAUDE_PLUGIN_ROOT}`, `${CLAUDE_SESSION_ID}`,
   `${CLAUDE_PROJECT_DIR}` replaced
3. **Preprocessor execution** — `!` backtick commands run, output replaces the directive

Because argument substitution happens first, preprocessor commands can reference skill arguments.

## Argument Syntax

| Syntax | Resolves To | Index |
|--------|------------|-------|
| `$ARGUMENTS` | Full raw args string | — |
| `$ARGUMENTS[0]` | First token | 0-indexed |
| `$ARGUMENTS[1]` | Second token | 0-indexed |
| `$1` | First token | 1-indexed |
| `$2` | Second token | 1-indexed |
| `$name` | Token at position matching `arguments:` frontmatter | by name |

Named arguments require an `arguments:` field in YAML frontmatter listing parameter names.

**Syntax warning:** Use `$ARGUMENTS` (no curly braces). The `${ARGUMENTS}` form is NOT recognized
and will pass through unresolved.

If no argument syntax appears in skill content, Claude Code appends `ARGUMENTS: <value>` as plain
text at the end (fallback for skills that read arguments as agent instructions).

## Composition: Skill A Invokes Skill B

Skill A determines runtime values, then invokes Skill B with those values as arguments.
Skill B's preprocessor runs the command invisibly using the passed arguments.

```
┌──────────────────────────────────────────────────────┐
│ Skill A (orchestrator)                               │
│                                                      │
│   1. Agent determines issue_id, base_branch, etc.    │
│   2. Invokes: /cat:skill-b issue_id base_branch      │
└──────────────────┬───────────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────────┐
│ Skill B (silent executor)                            │
│                                                      │
│ arguments: [completedIssue, baseBranch]              │
│                                                      │
│ Preprocessing (invisible to user):                   │
│   $completedIssue → "2.1-fix-bug"                    │
│   $baseBranch → "v2.1"                               │
│   !`launcher $completedIssue $baseBranch` → output   │
│                                                      │
│ Agent receives: command output as skill content      │
└──────────────────────────────────────────────────────┘
```

## Example: Issue Complete Box

**Before (visible Bash call):**

```markdown
## Next Task
Run `get-next-task-box --completed-issue "${issue_id}" ...` to generate the box.
```

Agent makes a visible Bash tool call. User sees the command.

**After (silent execution):**

```markdown
---
arguments:
  - completedIssue
  - baseBranch
---
!`"${CLAUDE_PLUGIN_ROOT}/hooks/bin/get-next-task-box" $completedIssue $baseBranch`
```

When `/cat:work-complete 2.1-fix-bug v2.1` is invoked:

1. `$completedIssue` → `2.1-fix-bug`, `$baseBranch` → `v2.1`
2. `${CLAUDE_PLUGIN_ROOT}` → `/path/to/plugin`
3. Command executes: `get-next-task-box 2.1-fix-bug v2.1`
4. Output (Issue Complete box) replaces the directive
5. User sees only the formatted box — no Bash tool call

## Anti-Pattern: Dropping Arguments

When converting a visible Bash call to silent execution, **never drop arguments** that the original command accepted.
If the original command used agent-determined values (e.g., keywords, filters, IDs), use the composition pattern:
Skill A determines the values at runtime, then invokes Skill B with those values as arguments.

**Wrong:** Remove keyword arguments because "preprocessing can't use agent-determined values."
Preprocessing CAN use agent-determined values — that is the entire purpose of argument substitution (step 1 above).

**Right:** Skill A determines keywords, invokes `/cat:skill-b keyword1 keyword2`. Skill B's preprocessor receives
the keywords via `$ARGUMENTS` and passes them to the underlying command.

## When to Use

**Good fit:**
- Commands whose output the agent should echo or parse (status boxes, progress banners)
- Replacing visible Bash tool calls with invisible preprocessing
- Multi-phase workflows where Skill A discovers values and Skill B uses them

**Poor fit:**
- Commands requiring interactive error handling (use Bash tool instead)
- Commands whose output the agent needs to branch on before continuing
- Single-use commands where adding a skill adds more complexity than it saves

## Tradeoffs

| Benefit | Cost |
|---------|------|
| Clean user output | Preprocessor errors harder to debug |
| No Bash tool permission prompts | Requires a skill file per command |
| Arguments validated by frontmatter | Command must succeed without agent intervention |
