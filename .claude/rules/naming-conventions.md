---
paths: ["client/**", "plugin/**"]
---
# Naming Conventions

## Variable Names in Markdown Files

Variable and parameter names referenced in Markdown (MD) files use **snake_case**.

This applies to:
- Skill parameter names in argument-hint frontmatter and argument tables (e.g., `cat_agent_id`, `issue_id`,
  `worktree_path`, `target_branch`)
- Agent variable references embedded in Bash commands within skill instructions (e.g., `${issue_id}`,
  `${worktree_path}`)
- Named identifiers used in skill invocation `args:` strings

**Correct (snake_case):**
```
<cat_agent_id> <issue_id> <worktree_path> <target_branch>
```

**Incorrect (camelCase):**
```
<catAgentId> <issueId> <worktreePath> <targetBranch>
```

## Variable Names in Java Source Files

Variable names in Java source files use **camelCase** per Java language convention (e.g., `catAgentId`,
`issueId`, `worktreePath`, `targetBranch`).

## JSON Field Names

JSON field names in API output contracts (from Java CLI tools) use **snake_case**
(e.g., `"issue_id"`, `"worktree_path"`, `"target_branch"`). This is consistent with the Configuration Reads
table which already shows `target_branch` and `issue_id` in snake_case.

## All-Caps Shell Variables

Shell environment variables use **SCREAMING_SNAKE_CASE** per POSIX convention (e.g., `WORKTREE_PATH`,
`CLAUDE_SESSION_ID`, `TARGET_BRANCH`). This is a separate convention from the agent variable names described
above.
