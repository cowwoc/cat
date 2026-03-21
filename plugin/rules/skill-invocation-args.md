## Skill Invocation: Passing Arguments

**CRITICAL**: When invoking skills that accept arguments, your CAT agent ID MUST be the first argument.

Your CAT agent ID is injected at session start. It identifies you uniquely within the session:
- Main agent format: `{UUID}` (e.g., `c405969a-dc75-405c-beed-bd21c8461676`)
- Subagent format: `{session_id}/subagents/{agent_id}` (e.g., `abc123/subagents/def456`)

**How to pass the cat_agent_id as the first argument:**

```
skill: "cat:example", args: "<cat_agent_id>"
```

For skills with additional arguments, append them after the cat_agent_id:

```
skill: "cat:example", args: "<cat_agent_id> <issue_id> <worktree_path>"
```

**NEVER** pass a branch name, skill name, or other value as the first argument — it must always be
the cat_agent_id injected at session start.
