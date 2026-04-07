<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Help

Output the following content verbatim. Do not summarize, interpret, or add commentary.

# CAT Command Reference

**CAT** — hierarchical project planning with multi-agent issue execution.

---

## Common Operations

Everything below uses natural language. Slash commands work where shown.

**Initialize a project**
```
/cat:init
"Set up this project"
"Initialize CAT for this codebase"
```

**Check status**
```
/cat:status
"What's the status?"
"What should I work on next?"
```

**Add an issue**
```
"Fix the login"
"Add an issue to fix login"
"I need to track a new issue: improve error messages"
```

**Work on an issue**
```
"Next issue"
"Work on 2.1-fix-login"
"Resume 2.1-fix-login"
"Let's keep going on 2.1-fix-login"
```

**Research a version**
```
"Research v1.0"
"Research best practices before planning v1.0"
```

**Remove an issue**
```
"Remove issue v1.0-parse-tokens"
```

**Remove a version**
```
"Remove the v1.0 version"
```

**Run a retrospective**
```
"Run a retrospective"
```

**Configure settings**
```
/cat:config
"Set my trust level to high"
```

---

## Commands

| Command | What It Does |
|---------|--------------|
| `/cat:init` | Set up a new or existing project |
| `/cat:status` | See what's happening and what to do next |
| `/cat:config` | Change trust level and workflow preferences |
| `/cat:cleanup` | Remove stale locks and abandoned worktrees |

---

## Reference

### Work Scope

Ask Claude to work at different scopes:

| Scope | Example | Behavior |
|-------|---------|----------|
| all | "Next issue" | Work through all incomplete issues |
| major | "Work on v1 issues" | All issues in v1.x.x |
| minor | "Work on v1.0 issues" | All issues in v1.0.x |
| patch | "Work on v1.0.1 issues" | All issues in v1.0.1 |
| specific | "Work on 1.0-parse" | One specific issue |

- Auto-continues to next issue when trust >= medium
- Creates worktree and issue branch per issue
- Runs approval gate when trust < high

### /cat:init Details

- Creates project.md, roadmap.md, config.json
- Asks for trust level (how much autonomy your partner has)
- For new projects: gathers project context through guided questions
- For existing codebases: detects patterns and infers current state
- Offers guided first-issue creation after setup

### Issue Naming

- Lowercase letters and hyphens only
- Maximum 50 characters
- Must be unique within minor version

**Valid:** `parse-tokens`, `fix-memory-leak`, `add-user-auth`
**Invalid:** `Parse_Tokens`, `fix memory leak`, `add-very-long-issue-name-that-exceeds-limit`

### Project Structure

CAT supports 2-level (MAJOR → MINOR → ISSUE) and 3-level (MAJOR → MINOR → PATCH → ISSUE) schemes.

```
.cat/
├── project.md              # Project overview
├── roadmap.md              # Version summaries
├── config.json             # Configuration
└── v{major}/
    └── v{major}.{minor}/
        ├── {issue-name}/   # Issues (2-level)
        └── v{major}.{minor}.{patch}/
            └── {issue-name}/  # Issues (3-level)
```

Issue changelog content is embedded in commit messages.

### Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Issue (2-level) | `{major}.{minor}-{issue-name}` | `1.0-parse-tokens` |
| Issue (3-level) | `{major}.{minor}.{patch}-{issue-name}` | `1.0.1-fix-edge-case` |
| Subagent | `{issue-branch}-sub-{uuid}` | `1.0-parse-tokens-sub-a1b2c3` |

