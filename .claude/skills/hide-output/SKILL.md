---
description: Apply silent preprocessing or skill chaining to hide undesirable tool output from users
---

# Hide Output Skill

**Purpose**: Convert visible tool calls, Bash output, or LLM-generated formatting into invisible
preprocessing so users see only meaningful output.

**When to Use**:
- A skill produces noisy Bash/Read/Grep calls that clutter the user's terminal
- Formatted output (boxes, tables, banners) is being built by the LLM and sometimes breaks
- Intermediate data-gathering steps are visible but not useful to the user

## Step 1: Identify the Target

Ask the user (or read from `$ARGUMENTS`) which skill or output pattern needs hiding.

Determine:
- **What output is undesirable?** (tool calls, formatted output, intermediate data reads)
- **Where does it currently happen?** (which skill file, which step)
- **Is the output deterministic or LLM-dependent?**

Read the target skill file to understand its current structure.

## Step 2: Classify the Output

| Question | If YES | Approach |
|----------|--------|----------|
| Can a script compute the output from files/environment alone? | No LLM judgment needed | **Direct Preprocessing** |
| Does the LLM decide what data appears, but a script can format it? | LLM selects, script renders | **Delegated Preprocessing** (skill chaining) |
| Does the LLM need to gather data across multiple steps before acting? | Multi-step collection | **Handler Chain** |

### Direct Preprocessing

Use when output is fully deterministic. A shell script reads files/state and produces the
final formatted output.

**Implementation**:
1. Create a script in `plugin/scripts/` that produces the output
2. Replace the output section in the skill with the preprocessing syntax shown below
3. Claude receives the rendered output and echoes it verbatim

**Preprocessing syntax** (where [BANG] represents the exclamation mark character):

`[BANG]`plugin/scripts/my-script.sh args``

**Structure** (in the target SKILL.md):
```
"The current status:"
[BANG]`plugin/scripts/my-script.sh args`
"Proceed with next step..."
```

**CAUTION (M440):** The preprocessor regex matches `[BANG]`...`` everywhere in any invocable
skill file - code fences, comments, and inline code do NOT prevent expansion. When documenting
the syntax, always use `[BANG]` placeholder instead of the literal character.

The script runs during skill expansion. Claude never sees the command, only the output.

### Delegated Preprocessing (Skill Chaining)

Use when the LLM must decide what data to include, but formatting should be computed.

**Implementation**:
1. **Skill A** (orchestrator): LLM analyzes context and determines data
2. **Skill B** (renderer): Contains exclamation-backtick syntax that formats the data
3. Skill A invokes Skill B via the Skill tool, passing data as arguments

**Structure**:
```
Skill A (orchestrator):
  LLM analyzes and selects data
  Invokes Skill B with: args = selected data

Skill B (renderer):
  Receives $ARGUMENTS in markdown context (not shell)
  [exclamation-backtick: render-script.sh --from-env]
  Script reads $ARGUMENTS from environment, sanitizes, then renders
```

### Handler Chain

Use when multiple invisible data-gathering steps must precede skill execution.

**Implementation**:
1. Create a Python handler in `plugin/hooks/skill_handlers/`
2. Handler runs subprocess calls invisibly during skill loading
3. Handler returns JSON data + rendered banners via `additionalContext`
4. Skill parses handler output and proceeds with meaningful work

**Structure**:
```
Handler (invisible):
  - Read config files
  - Run discovery scripts
  - Create resources
  - Return: JSON data + banner

SKILL.md:
  Parse HANDLER_DATA from context
  Route based on status
  Invoke next skill or spawn Task
```

## Step 3: Implement the Chosen Approach

Based on the classification from Step 2, implement the appropriate pattern.

### For Direct Preprocessing

1. **Create the script**:
   - Location: `plugin/scripts/<descriptive-name>.sh` (or `.py`)
   - Script must be executable (`chmod +x`)
   - Script reads all needed data from environment/files
   - Script outputs the final formatted content to stdout
   - Use `set -euo pipefail` for bash scripts

2. **Update the skill**:
   - Remove any Bash/Read tool calls that gathered data for this output
   - Remove any LLM instructions for formatting this output
   - Insert exclamation-backtick syntax where the output should appear
   - Add a fail-fast check below: "If you do NOT see output above, preprocessing FAILED. STOP."

3. **Available environment variables in preprocessing scripts**:
   - `$CLAUDE_PROJECT_DIR` - Project root directory
   - `$CLAUDE_PLUGIN_ROOT` - Plugin installation root
   - `$CLAUDE_SESSION_ID` - Current session ID
   - `$ARGUMENTS` - Arguments passed to the skill (read from environment inside the script; do not pass directly on the
     shell command line — see Anti-Patterns)

### For Delegated Preprocessing (Skill Chaining)

1. **Create the renderer skill** (non-user-invocable):
   - Location: `plugin/skills/<renderer-name>/SKILL.md`
   - Script reads `$ARGUMENTS` from environment and sanitizes before use
   - Contains exclamation-backtick syntax calling the render script
   - Minimal instructions: "Output the above verbatim"

2. **Update the orchestrator skill**:
   - Keep the LLM analysis/selection logic
   - Replace manual formatting with: "Invoke the `<renderer-name>` skill with args: <data>"
   - The Skill tool triggers the renderer's preprocessing automatically

3. **Register the renderer** in `plugin/plugin.json` if it's a plugin skill.

### For Handler Chain

1. **Create the handler**:
   - Location: `plugin/hooks/skill_handlers/<name>_handler.py`
   - Implement `handle(self, context: dict) -> str | None`
   - Register in `plugin/hooks/skill_handlers/__init__.py`

2. **Move data collection into the handler**:
   - All file reads, config parsing, discovery logic
   - Return structured data as `HANDLER_DATA:` JSON block
   - Optionally include rendered banners

3. **Update the skill**:
   - Remove data-gathering steps
   - Add "Parse HANDLER_DATA" as first step
   - Route based on handler status (READY, ERROR, NO_DATA, etc.)

## Step 4: Verify the Output Is Hidden

1. **Test the skill invocation** - invoke the modified skill and confirm:
   - No unwanted Bash/Read/Grep tool calls appear in the terminal
   - The preprocessed output appears correctly inline
   - Fail-fast triggers if preprocessing fails

2. **Check for pattern collisions** - ensure no literal exclamation-backtick patterns
   exist in documentation sections of invocable skills (pattern matcher would try to expand them).

3. **Run tests** if the modified handler has test coverage:
   ```bash
   python3 /workspace/run_tests.py
   ```

## Step 5: Commit

Commit the changes with an appropriate message:
- Script additions: `config: add <script-name> preprocessing script`
- Skill modifications: `config: migrate <skill-name> to silent preprocessing`
- Handler additions: `config: add <handler-name> handler for invisible data collection`

## Decision Quick Reference

```
Output to hide
  │
  ├─ Can script compute everything from files/env?
  │   └─ YES → Direct Preprocessing (simplest)
  │
  ├─ LLM selects data, script formats it?
  │   └─ YES → Delegated Preprocessing / Skill Chaining
  │
  └─ Multiple invisible gathering steps needed?
      └─ YES → Handler Chain Pattern
```

## Anti-Patterns

| Anti-Pattern | Why It Fails | Correct Approach |
|-------------|-------------|-----------------|
| LLM builds boxes/tables manually | Emoji widths, padding errors (M246, M256) | Preprocessing script |
| Visible Bash calls to read config | Noisy terminal output | Move to handler |
| Skill contains formatting algorithm | LLM may miscalculate | Extract to script |
| Using `$ARGUMENTS` directly in shell | Injection risk with special characters | Script controls input |
