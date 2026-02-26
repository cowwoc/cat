<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Help

Echo the content inside the LATEST `<output skill="help">` tag. Do not summarize, interpret, or add commentary.

<output skill="help">
# CAT Command Reference

**CAT** enables hierarchical project planning with multi-agent issue execution.

---

## Essential Commands (Start Here)

These three commands cover 90% of daily use:

| Command | What It Does |
|---------|--------------|
| `/cat:init` | Set up a new or existing project |
| `/cat:status` | See what's happening and what to do next |
| `/cat:work` | Execute the next available issue |

**Minimum viable workflow:**
```
/cat:init --> /cat:add --> /cat:work
    ^                          |
    +------ /cat:status -------+
```

---

## Planning Commands

Use these when you need to structure your work:

| Command | What It Does |
|---------|--------------|
| `/cat:add [desc]` | Add issues/versions. With desc, creates issue directly |
| `/cat:remove` | Remove issues or versions (with safety checks) |
| `/cat:config` | Change workflow mode, trust level, preferences |

---

## Advanced Commands

Power user features for complex workflows:

| Command | What It Does |
|---------|--------------|
| `/cat:research` | Run stakeholder research on pending versions |
| `/cat:cleanup` | Clean up abandoned worktrees and stale locks |
| `/cat:spawn-subagent` | Launch isolated subagent for an issue |
| `/cat:monitor-subagents` | Check status of running subagents |
| `/cat:collect-results` | Gather results from completed subagents |
| `/cat:merge-subagent` | Merge subagent branch into issue branch |
| `/cat:token-report` | Generate token usage report |
| `/cat:decompose-issue` | Split oversized issue into smaller issues |
| `/cat:parallel-execute` | Orchestrate multiple subagents concurrently |

---

## Full Reference

<details>
<summary>Hierarchy Structure</summary>

CAT supports flexible version schemes:
- **2-level:** MAJOR --> MINOR --> ISSUE (e.g., v1.0)
- **3-level:** MAJOR --> MINOR --> PATCH --> ISSUE (e.g., v1.0.1)

```
.claude/cat/
+-- PROJECT.md              # Project overview
+-- ROADMAP.md              # Version summaries
+-- cat-config.json         # Configuration
+-- v{major}/
    +-- STATE.md            # Major version state
    +-- PLAN.md             # Business-level plan
    +-- v{major}.{minor}/
        +-- STATE.md        # Minor version state
        +-- PLAN.md         # Feature-level plan
        +-- {issue-name}/    # Issues at minor level (2-level scheme)
        +-- v{major}.{minor}.{patch}/
            +-- STATE.md    # Patch version state (optional 3-level)
            +-- PLAN.md     # Patch-level plan
            +-- {issue-name}/  # Issues at patch level
```

Issue changelog content is embedded in commit messages.

</details>

<details>
<summary>/cat:init Details</summary>

Initialize CAT planning structure (new or existing project).
- Creates PROJECT.md, ROADMAP.md, cat-config.json
- Asks for trust level (how much autonomy your partner has)
- For new projects: Deep questioning to gather project context
- For existing codebases: Detects patterns and infers current state
- Offers guided first-issue creation after setup

</details>

<details>
<summary>/cat:work Scope Options</summary>

| Scope Format | Example | Behavior |
|--------------|---------|----------|
| (none) | `/cat:work` | Work through all incomplete issues |
| major | `/cat:work 1` | Work through all issues in v1.x.x |
| minor | `/cat:work 1.0` | Work through all issues in v1.0.x |
| patch | `/cat:work 1.0.1` | Work through all issues in v1.0.1 |
| issue | `/cat:work 1.0-parse` | Work on specific issue (2-level) |
| issue | `/cat:work 1.0.1-parse` | Work on specific issue (3-level) |

**Features:**
- Auto-continues to next issue when trust >= medium
- Creates worktree and issue branch per issue
- Spawns subagent for isolated execution
- Monitors token usage
- Runs approval gate (when trust < high)
- Squashes commits by type
- Merges to main and cleans up

</details>

<details>
<summary>Issue Naming Rules</summary>

- Lowercase letters and hyphens only
- Maximum 50 characters
- Must be unique within minor version

**Valid:** `parse-tokens`, `fix-memory-leak`, `add-user-auth`
**Invalid:** `Parse_Tokens`, `fix memory leak`, `add-very-long-issue-name-that-exceeds-limit`

</details>

<details>
<summary>Branch Naming</summary>

| Type | Pattern | Example |
|------|---------|---------|
| Issue (2-level) | `{major}.{minor}-{issue-name}` | `1.0-parse-tokens` |
| Issue (3-level) | `{major}.{minor}.{patch}-{issue-name}` | `1.0.1-fix-edge-case` |
| Subagent | `{issue-branch}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

</details>

---

## Workflow Modes

Set during `/cat:init` in cat-config.json:

**Trust Levels**

- **Low** - Check in often, verify each move
- **Medium** (default) - Trust routine calls, review key decisions
- **High** - Full autonomy, auto-merges on issue completion

Change anytime with `/cat:config` or edit `.claude/cat/cat-config.json`

---

## Common Workflows

**Starting a new project:**
```
/cat:init
/cat:add          # Select "Major version", then "Issue"
/cat:work
```

**Checking progress:**
```
/cat:status
```

**Adding more work:**
```
/cat:add                       # Interactive: choose Issue, Minor, or Major
/cat:add make install easier   # Quick: creates issue with description
```

**Removing planned work:**
```
/cat:remove       # Interactive: choose Issue, Minor, or Major
```

## Configuration Options

cat-config.json:
```json
{
  "trust": "medium",            // low | medium | high (autonomy level)
  "verify": "changed",          // changed | all (verification scope)
  "effort": "medium",        // low | medium | high (exploration level)
  "patience": "medium"          // low | medium | high (refactoring tolerance)
}
```

**Note:** Context limits are fixed at 200K/40%/80% - see agent-architecture.md for details.

## Getting Help

- Read `.claude/cat/PROJECT.md` for project vision
- Check `.claude/cat/ROADMAP.md` for version overview
- Use `/cat:status` to see current state
- Review individual STATE.md files for detailed progress
</output>
