## Skill Invocation: Passing Arguments

**CRITICAL**: When invoking skills that accept arguments, your CAT agent ID MUST be the first argument.

Your CAT agent ID is injected at session start. It identifies you uniquely within the session:
- Main agent format: `{UUID}` (e.g., `c405969a-dc75-405c-beed-bd21c8461676`)
- Subagent format: `{sessionId}/subagents/{agentId}` (e.g., `abc123/subagents/def456`)

**How to pass the catAgentId as the first argument:**

```
skill: "cat:example", args: "<catAgentId>"
```

For skills with additional arguments, append them after the catAgentId:

```
skill: "cat:example", args: "<catAgentId> <issueId> <worktreePath>"
```

**NEVER** pass a branch name, skill name, or other value as the first argument — it must always be
the catAgentId injected at session start.
