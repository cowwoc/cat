# Error Handling

## Subagent Failure Escalation

```
Subagent encounters error
          |
          v
Subagent fails fast, returns to main agent
          |
          v
Main agent attempts resolution
          |
          +---> Resolved: Continue execution
          |
          +---> Unresolved: Escalate to user
```

## Fail-Fast Triggers

Subagents return immediately when:
- Plan has ambiguities or issues
- Required files not found
- Prerequisites not met
- Build/test failures
- Unrecoverable errors

## User Escalation

When escalating to user, provide:

### Required Information
1. **Error message**: Clear description of failure
2. **Task context**: Which task failed
3. **Subagent logs**: Relevant output
4. **Remediation**: Suggested next steps

### Example Escalation
```markdown
## Task Failed: parse-switch-statements

**Error:** Unable to resolve merge conflict in Parser.java

**Context:**
- Branch: 1.0-parse-switch-statements-sub-a1b2c3
- Conflicting file: src/main/java/Parser.java

**Conflict Details:**
Lines 45-52 have conflicting changes from:
- main branch: Added null check
- task branch: Refactored method signature

**Suggested Actions:**
1. Review conflict in Parser.java
2. Decide which changes to keep
3. Resume with /cat:work
```

## Unplanned Issues

When bugs/issues discovered during development:

1. Main agent proposes adding as new task
2. User decides target minor/major version
3. If accepted, create task via normal flow

## Learn-from-Mistakes Integration

When analyzing mistakes, consider:

| Factor | Consideration |
|--------|---------------|
| Conversation length | Late mistakes may indicate context degradation |
| Token usage | High usage correlates with errors |
| Compaction events | May signal lost context |

Recommendation: Decompose work earlier when context-related patterns emerge.

## Recovery After Session Interruption

If session ends during subagent execution:
1. STATE.md reflects last known state
2. Worktree may exist with partial work
3. User resumes with `/cat:work`
4. Main agent assesses state and continues or restarts
