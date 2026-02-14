<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Agent Architecture

## Main Agent

### Responsibilities

| Area | Actions |
|------|---------|
| Orchestration | Coordinate subagent execution |
| Planning | Read code, make decisions, decompose issues |
| Metadata | Create/update planning documents |
| Git operations | Branch creation, merging |
| Conflict resolution | Resolve merge conflicts |
| Queue processing | Handle subagent returns serially |
| State updates | Update STATE.md after completions |

### Does NOT

- Write production code directly
- Execute implementation work
- Work in worktrees except for merging

### No Exceptions for "Small Fixes"

**MANDATORY**: Main agent NEVER edits source code directly, even for:
- Merge conflict resolution (spawn subagent)
- 1-3 line fixes (spawn subagent)
- Compilation error fixes (spawn subagent)
- Style corrections (spawn subagent)

**Anti-pattern**: "This is just a small fix, I'll do it directly" → ALWAYS delegate.

The delegation boundary exists for quality and traceability, not efficiency.
"Quick fixes" bypass fresh context and create untraceable changes.

**MANDATORY Pre-Edit Self-Check:**

BEFORE using the Edit tool on ANY source file (.java, .md code docs, etc.), STOP and verify:

1. **Am I the main agent?** (orchestrating a CAT task)
2. **Is this a source/documentation file?** (not STATE.md, PLAN.md, CHANGELOG.md)
3. **Is a subagent already running or could one be spawned?**

If answers are YES/YES/YES → **SPAWN SUBAGENT INSTEAD**

**This applies even for "simple" changes:**
- Variable renaming → subagent
- Comment updates → subagent
- Style fixes → subagent
- Convention updates to style guides → subagent

**Rationale:** "Simple" edits bypass the delegation boundary. If it touches code, delegate it.

### Work Request Handling

**Default behavior:** When a user requests work, the main agent proposes issue creation first.

This ensures all work is tracked in the planning structure. The agent should respond:

> "I'll create an issue for this so it's tracked properly. Let me add it via `/cat:add`."

**Trust-level variations:**

| Trust Level | Behavior |
|-------------|----------|
| `low` | Always ask before any work, even trivial changes |
| `medium` | Propose issue creation for non-trivial work; ask for trivial |
| `high` | Create issue automatically via `/cat:add`, then proceed to `/cat:work` |

**Trivial work definition:**
- Single-line changes (typos, import fixes, obvious corrections)
- Changes affecting only 1 file
- No logic changes, purely cosmetic

**User override:** User can bypass with phrases like "just do it", "quick fix", or "no issue needed".
When overridden, the agent should still warn: "Working directly without issue tracking."

**Example interactions:**

User: "Fix the bug where parsing fails on empty input"
Agent (medium trust): "I'll create an issue for this so it's tracked properly. Running `/cat:add fix parsing failure on
empty input`..."

User: "Fix the typo in README"
Agent (medium trust): "This looks like a trivial fix. Should I create an issue for tracking, or just fix it directly?"

User: "Just fix it"
Agent: "Working directly without issue tracking. [proceeds to fix]"

**What this does NOT change:**
- `/cat:work` workflow remains unchanged
- Subagent delegation rules remain unchanged
- Main agent still does not write production code directly

### Worktree Usage

Main agent uses worktrees ONLY for:
- Merging subagent branches into issue branches
- Merging issue branches into main

## Subagent Types

### Implementation Subagent

Standard subagent for executing coding issues.

| Area | Actions |
|------|---------|
| Execution | Perform implementation work |
| Isolation | Work in dedicated worktree |
| Token tracking | Monitor context usage |
| Compaction detection | Track summary events |
| Reporting | Return metrics on completion |
| Fail-fast | Return immediately on plan issues |

### Exploration Subagent

Specialized subagent for issue preparation, codebase exploration, and verification.
Handles three phases internally to hide noisy tool calls from user.

| Phase | Responsibilities | Output |
|-------|------------------|--------|
| **Preparation** | Read PLAN.md, analyze issue size, create worktree | Estimate and worktree path |
| **Exploration** | Search codebase, find patterns, check duplicates | File locations and patterns |
| **Verification** | Validate findings, confirm paths exist | Validation results |

**Returns structured JSON** for clean main agent display:

```json
{
  "status": "READY|OVERSIZED|DUPLICATE|BLOCKED",
  "preparation": {"estimatedTokens": 45000, "worktreePath": "..."},
  "findings": {"filesToModify": [...], "patterns": [...]},
  "verification": {"allPathsExist": true}
}
```

**Benefits:**
- User sees clean summary instead of Bash/Read/Grep tool calls
- Main agent receives structured data for decision-making
- Preparation work isolated from main agent context

See `spawn-subagent` skill → "Expanded Exploration Subagent" for full details.

## Subagent Responsibilities

### Token Tracking

Subagents read session file:
```
/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl
```

Collect:
1. Sum `input_tokens + output_tokens`
2. Count `type: "summary"` entries

### Return Protocol

**MANDATORY**: On completion, subagent MUST output a completion report.

**Format**:
```json
{
  "status": "success|failure",
  "tokensUsed": 75000,
  "compactionEvents": 0,
  "summary": "Brief description of work completed"
}
```

**How to calculate**:
```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${CLAUDE_SESSION_ID}.jsonl"

# Total tokens
TOTAL=$(jq -s '[.[] | select(.type == "assistant") | .message.usage |
  (.input_tokens + .output_tokens)] | add' "${SESSION_FILE}")

# Compaction events
COMPACTIONS=$(jq -s '[.[] | select(.type == "summary")] | length' "${SESSION_FILE}")
```

**Output**: Print the JSON to stdout before exiting. Main agent will capture this.

## Communication Flow

```
Main Agent
    |
    +---> Spawn Subagent 1 (issue-a)
    |         |
    +---> Spawn Subagent 2 (issue-b)
    |         |
    v         v
    [Wait for completions]
         |
         v
    Process returns serially
         |
         v
    Merge branches
         |
         v
    Update STATE.md
```

## Parallel Execution

- No arbitrary limits on concurrent subagents
- Main agent manages based on available resources
- Independent issues execute simultaneously
- Dependent issues wait for prerequisites

## Context Limit Constants (A018)

**These values are FIXED and defined here as the single source of truth.**

| Constant | Value | Purpose |
|----------|-------|---------|
| `CONTEXT_LIMIT` | 200000 | Claude's context window (tokens) |
| `SOFT_TARGET_PCT` | 40 | Ideal issue size percentage |
| `HARD_LIMIT_PCT` | 80 | Maximum safe execution percentage |

**Derived values:**
- Soft target: 80,000 tokens (40% of 200K)
- Hard limit: 160,000 tokens (80% of 200K)

**Usage in scripts:**
```bash
# Reference: agent-architecture.md § Context Limit Constants
CONTEXT_LIMIT=200000
SOFT_TARGET_PCT=40
HARD_LIMIT_PCT=80
SOFT_TARGET=$((CONTEXT_LIMIT * SOFT_TARGET_PCT / 100))  # 80000
HARD_LIMIT=$((CONTEXT_LIMIT * HARD_LIMIT_PCT / 100))    # 160000
```

**Why fixed, not configurable:**
- Claude's context window is model-determined, not user preference
- Quality thresholds are based on empirical testing
- Consistency across all CAT installations ensures reliable behavior

### Limit Hierarchy

| Limit | Percentage | Tokens (200K) | Purpose |
|-------|------------|---------------|---------|
| Soft target | 40% | 80,000 | Recommended issue size for optimal quality |
| Hard limit | 80% | 160,000 | Maximum allowed - MANDATORY decomposition above |
| Context limit | 100% | 200,000 | Absolute ceiling - compaction occurs |

### Quality Degradation by Context Usage

| Context Usage | Quality | Claude's State |
|---------------|---------|----------------|
| 0-30% | PEAK | Thorough, comprehensive |
| 30-50% | GOOD | Confident, solid work |
| 50-70% | DEGRADING | Efficiency mode begins |
| 70%+ | POOR | Rushed, minimal |

**The 40-50% inflection point:** Claude perceives context mounting and enters "completion mode."
Result: Quality crash before reaching hard limit. This is why soft target is 40%, not 70%.

### Issue Sizing Guidelines

| Issue Complexity | Context/Issue | Guideline |
|------------------|---------------|-----------|
| Simple (CRUD, config) | ~10-15% | Can batch 3-4 per session |
| Medium (business logic) | ~20-30% | 2-3 per session |
| Complex (algorithms) | ~30-40% | 1-2 per session |
| Very complex (migrations) | ~40-50% | 1 per session, consider decomposition |

### Main Agent Responsibilities

**Pre-Spawn (BEFORE spawning any subagent):**

1. Calculate estimated tokens from issue analysis
2. Calculate hard limit: `HARD_LIMIT = CONTEXT_LIMIT * 80 / 100`
3. Validate: `estimate < hard_limit`
4. If validation fails: MANDATORY decomposition (do NOT spawn)

**Post-Spawn (AFTER subagent completes):**

1. Collect actual token usage from `.completion.json`
2. Compare actual against hard limit
3. Flag violations with EXCEEDED status
4. Trigger `/cat:learn` for each violation

### Enforcement Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    MAIN AGENT (Pre-Spawn)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Analyze issue → estimate tokens                             │
│                     │                                           │
│                     v                                           │
│  2. Calculate: HARD_LIMIT = CONTEXT_LIMIT * 80%                │
│                     │                                           │
│                     v                                           │
│  3. Validate: estimate < HARD_LIMIT?                           │
│                     │                                           │
│           ┌────────┴────────┐                                  │
│           │                 │                                   │
│           v                 v                                   │
│        YES: Spawn      NO: MANDATORY                            │
│        subagent        decomposition                            │
│           │            (do NOT spawn)                           │
│           v                                                     │
│  4. Monitor execution                                           │
│           │                                                     │
│           v                                                     │
│  5. Collect results → actual tokens                             │
│           │                                                     │
│           v                                                     │
│  6. Check: actual >= HARD_LIMIT?                               │
│           │                                                     │
│     ┌─────┴─────┐                                              │
│     │           │                                               │
│     v           v                                               │
│   NO: OK     YES: Flag EXCEEDED                                 │
│              + learn                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Aggregate Reporting Format

For multi-subagent issues, generate aggregate token report:

```
## Aggregate Token Report

| Subagent | Tokens | % of Limit | Status |
|----------|--------|------------|--------|
| issue-sub-a1b2c3d4 | 65,000 | 32% | OK |
| issue-sub-e5f6g7h8 | 170,000 | 85% | EXCEEDED |
| issue-sub-i9j0k1l2 | 45,000 | 22% | OK |

**Total tokens:** 280,000
**Subagents exceeded hard limit:** 1
```

### Violation Handling Process

When a subagent exceeds the hard limit:

1. **Flag:** Mark subagent status as EXCEEDED in aggregate report
2. **Record:** Invoke `/cat:learn` with:
   - Mistake reference: A018
   - Subagent ID
   - Actual tokens used
   - Hard limit value
   - Issue context
3. **Analyze:** Review why estimation failed
4. **Improve:** Update estimation factors based on pattern

## Path and Config Verification (A003)

**MANDATORY**: Verify paths and config values before use. Common failure patterns documented below.

### Worktree Path Handling

When working in a worktree (e.g., `/workspace/.claude/cat/worktrees/issue-name/`):

| Path Type | Example | Risk |
|-----------|---------|------|
| Absolute to /workspace/ | `/workspace/plugin/skills/` | Bypasses worktree isolation |
| Relative from cwd | `plugin/skills/` | Correct - stays in worktree |
| ${CLAUDE_PROJECT_DIR} | `${CLAUDE_PROJECT_DIR}/plugin/` | Points to main workspace |

**Verification checklist:**
1. Check if cwd is a worktree: `[ -f ".git/cat-base" ]`
2. If in worktree, use relative paths for file creation/editing
3. Use absolute paths only for reading config from main workspace

**Anti-pattern**: Creating files with `/workspace/` prefix while in worktree - changes go to main workspace instead of
issue worktree.

### Config File Resolution

Config files may not exist in worktrees due to `.gitignore`:

| File | Main Workspace | Worktree |
|------|----------------|----------|
| `cat-config.json` | ✓ Exists | ✓ Exists (tracked) |
| `.local.json` | ✓ Exists | ✗ Missing (gitignored) |
| `.claude/settings.json` | ✓ Exists | ✗ May be missing |

**Resolution strategy:**
```bash
# Use CLAUDE_PROJECT_DIR for config lookup (always points to main workspace)
CONFIG_DIR="${CLAUDE_PROJECT_DIR}/.claude/cat"
CONFIG_FILE="${CONFIG_DIR}/cat-config.json"

# Fall back to cwd only if CLAUDE_PROJECT_DIR not set
if [ -z "$CLAUDE_PROJECT_DIR" ]; then
  CONFIG_FILE=".claude/cat/cat-config.json"
fi
```

**Anti-pattern**: Reading config from cwd in worktree, getting default values instead of user's configured values.

### Terminology Disambiguation

Ambiguous terms require user clarification before action:

| Term | Possible Meanings | Required Action |
|------|-------------------|-----------------|
| "abort issue" | Stop and keep pending, OR cleanup and abandon | Use AskUserQuestion |
| "cancel" | Same as abort - clarify intent | Use AskUserQuestion |
| "delete" | Remove planning entry, OR just cleanup worktree | Use AskUserQuestion |

**Clarification template:**
```
When user says "[ambiguous term]", ask:
1. Stop working, keep issue pending for later
2. Cleanup worktree/branch, keep issue pending
3. Abandon permanently (remove from planning)
```

**Anti-pattern**: Interpreting "abort" as permanent abandonment without asking.

### Environment Variable Availability

Environment variable availability depends on the execution context. Understanding these distinctions prevents common
path resolution failures.

| Context | CLAUDE_PLUGIN_ROOT | CLAUDE_SESSION_ID | CLAUDE_PROJECT_DIR |
|---------|-------------------|-------------------|-------------------|
| Skill preprocessing (`!` subprocess) | String-substituted | String-substituted | ❌ NOT available |
| Bash tool calls (agent shell) | ✅ Via CLAUDE_ENV_FILE | ✅ Via CLAUDE_ENV_FILE | ✅ Via CLAUDE_ENV_FILE |
| Hook scripts | ✅ Env var | ✅ Env var | ✅ Env var |

**Context details:**

1. **Skill preprocessing (`!` subprocess)**: The `!` line in SKILL.md runs load-skill.sh, which processes handler.sh
   output AND first-use.md through substitute_vars. Only CLAUDE_PLUGIN_ROOT and CLAUDE_SESSION_ID are string-substituted.
   CLAUDE_PROJECT_DIR is neither substituted nor available as an env var. When first-use.md contains
   `${CLAUDE_PROJECT_DIR}` in code blocks, it passes through as literal text — it resolves later because Claude copies
   those code blocks into Bash tool calls where the variable IS available via CLAUDE_ENV_FILE.

2. **Bash tool calls (agent shell)**: All three variables are available because InjectEnv (Java SessionStart handler)
   persists them to CLAUDE_ENV_FILE, which is sourced before each Bash command.

3. **Hook scripts**: All variables provided as environment variables by the plugin system.

**CLAUDE_ENV_FILE injection chain:**
- Claude Code provides `CLAUDE_ENV_FILE` path and `CLAUDE_PROJECT_DIR` to SessionStart hooks
- `InjectEnv` (Java handler) writes `export CLAUDE_PROJECT_DIR=...` (and other vars) to CLAUDE_ENV_FILE
- Claude Code sources CLAUDE_ENV_FILE before each Bash tool call
- Result: `${CLAUDE_PROJECT_DIR}` resolves in Bash tool calls and in skill content code blocks that Claude executes

**When writing skill content:**
- Bash command examples CAN use `${CLAUDE_PROJECT_DIR}` — Claude copies them into Bash tool calls where it resolves
- Handler scripts called from `!` lines do NOT have access to `${CLAUDE_PROJECT_DIR}` as an env var or substitution
- Skill preprocessing `!` lines must use `"$(pwd)"` instead of `${CLAUDE_PROJECT_DIR}`
- For CLAUDE_PLUGIN_ROOT and CLAUDE_SESSION_ID, substitution works in both contexts

### Path Discovery

CAT scripts use **self-discovery** to find paths, avoiding dependency on environment variables:

| Path Type | Discovery Method |
|-----------|------------------|
| Plugin root | `SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"` |
| Project dir | `git rev-parse --git-common-dir` → parent directory |
| Session ID | Injected via SessionStart hook into context |

**Why self-discovery:**
- Environment variables set in hooks don't persist to Bash tool calls
- Scripts can run from any directory without configuration
- No dependency on external variable injection

**Session ID** is the exception - it must be extracted from context (SessionStart hook injects
"Session ID: ..." into conversation via additionalContext).

## Hook Registration Architecture

Claude Code supports hooks at two levels with different registration locations:

| Hook Type | Registration Location | Purpose |
|-----------|----------------------|---------|
| **Plugin hooks** | `plugin/hooks/hooks.json` | CAT plugin behavior (skills, enforcement) |
| **Project hooks** | `.claude/settings.json` | Project-specific overrides |

### Plugin Hooks

Plugin hooks are registered in the plugin's own `hooks.json`:

```
plugin/
├── hooks/
│   ├── hooks.json          ← Plugin hook registration
│   ├── enforce-status-output.py
│   ├── get-skill-output.py
│   └── ...
```

These hooks are automatically loaded when the CAT plugin is active. **Do not look for them in
`.claude/settings.json`** - that file is for project-specific hooks only.

### Project Hooks

Project-specific hooks are registered in `.claude/settings.json`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Write",
        "hooks": [{"type": "command", "command": ".claude/hooks/my-hook.sh"}]
      }
    ]
  }
}
```

These override or supplement plugin hooks for project-specific behavior.

### Common Mistake

When investigating plugin hook behavior, check `plugin/hooks/hooks.json` first, not
`.claude/settings.json`. The project settings file is for user-defined project hooks,
not plugin-provided hooks.
