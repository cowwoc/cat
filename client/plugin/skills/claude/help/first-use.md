<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Help

Return the Markdown below as your final assistant response. Do not wrap the response in a code block. Do not
summarize, interpret, or add commentary.

# CAT Command Reference

## Start Here

Use slash commands to select a CAT workflow explicitly.

## User-Facing Commands

- `/cat:init` - Set up a new or existing project.
- `/cat:status` - See what's happening and what to do next.
- `/cat:config` - Change trust level and workflow preferences.
- `/cat:cleanup` - Remove stale locks and abandoned worktrees.

---

## Reference

### Work Scope

Ask the agent to work at different scopes:

- `Next issue` - Work through all incomplete issues.
- `Work on v1 issues` - Work on all issues in `v1.x.x`.
- `Work on v1.0 issues` - Work on all issues in `v1.0.x`.
- `Work on v1.0.1 issues` - Work on all issues in `v1.0.1`.
- `Work on 1.0-parse` - Work on one specific issue.

Behavior:
- Auto-continues to the next issue when trust is `medium` or `high`.
- Creates a worktree and issue branch per issue.
- Runs an approval gate when trust is below `high`.

### Project Structure

CAT supports 2-level (MAJOR -> MINOR -> ISSUE) and 3-level (MAJOR -> MINOR -> PATCH -> ISSUE) schemes.

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

- Issue, 2-level: `{major}.{minor}-{issue-name}`
  Example: `1.0-parse-tokens`
- Issue, 3-level: `{major}.{minor}.{patch}-{issue-name}`
  Example: `1.0.1-fix-edge-case`
- Subagent: `{issue-branch}-sub-{uuid}`
  Example: `1.0-parse-tokens-sub-a1b2c3`
