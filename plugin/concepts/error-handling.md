# Error Handling and Fail-Fast Patterns

## Core Principle: Fail-Fast

**CRITICAL**: When something is wrong, stop immediately. Do not attempt workarounds.

```
Problem detected
      |
      v
STOP immediately
      |
      v
Report clear error message
      |
      v
Let caller/user decide next action
```

**Why fail-fast?**
- Workarounds produce incorrect or incomplete results
- Silent failures cascade into harder-to-debug issues
- Early failure with clear message enables faster resolution
- User/main-agent has context to make informed decisions

## Fail-Fast Patterns

### 1. Script Output Handler Output

Skills that depend on handler-computed output must fail if output is missing.

```markdown
**Check for output template:**

Look in conversation context for "OUTPUT TEMPLATE {SKILL_NAME} OUTPUT" section.

If NOT found: **FAIL immediately**.

Do NOT attempt to compute the output manually - the handler exists precisely
because manual computation is error-prone.
```

**Why?** Manual computation was extracted to handlers because agents make errors
when computing deterministic values (widths, alignments, formatting). The handler
guarantees correctness.

### 2. Subagent Fail-Fast

Subagents must fail-fast when encountering problems, not attempt recovery.

```markdown
FAIL-FAST CONDITIONS:
- Required file not found within N minutes
- Ambiguity in requirements that needs clarification
- Build/test failure (report BLOCKED, do NOT fix)
- Merge conflicts requiring human judgment
- Any situation requiring user decision
```

**Example prompt inclusion:**
```
FAIL-FAST: If you cannot locate the authentication code within 10 minutes,
report BLOCKED with search attempts. Do NOT guess or make assumptions.
```

**Why?** Subagents lack full context. Recovery attempts may:
- Make incorrect assumptions
- Waste tokens on wrong paths
- Produce work that must be discarded

### 3. Environment/Metadata Fail-Fast

Operations requiring specific environment state must verify before proceeding.

```bash
# Detect base branch from worktree metadata (fail-fast if missing)
BASE_BRANCH=$(cat .git/cat-base 2>/dev/null)
if [[ -z "$BASE_BRANCH" ]]; then
    echo "ERROR: No cat-base file found. Not in a CAT worktree."
    exit 1
fi
```

**Why?** Operating without required metadata produces undefined behavior.

### 4. Validation Fail-Fast

Input validation must fail on first error, not accumulate.

```bash
# WRONG - continues after error
[ -z "$ISSUE_NAME" ] && echo "Warning: no issue name"
[ -z "$VERSION" ] && echo "Warning: no version"
# ... continues with invalid state

# CORRECT - fail-fast
[ -z "$ISSUE_NAME" ] && echo "ERROR: ISSUE_NAME required" && exit 1
[ -z "$VERSION" ] && echo "ERROR: VERSION required" && exit 1
```

## Anti-Patterns

### Fallback Computation

```markdown
# BAD - Fallback undermines handler purpose
If output template not found:
  - Try to compute manually...
  - Fall back to simplified version...

# GOOD - Fail-fast exposes problems
If output template not found:
  - FAIL immediately
  - Error message explains what's missing
```

### Silent Degradation

```markdown
# BAD - Silently produces wrong result
if [[ -z "$CONFIG" ]]; then
    CONFIG="default"  # User never knows this happened
fi

# GOOD - Explicit about state
if [[ -z "$CONFIG" ]]; then
    echo "ERROR: CONFIG not set. Run /cat:config first."
    exit 1
fi
```

### Guessing Intent

```markdown
# BAD - Guesses what user meant
Requirement unclear? Assume they meant X and proceed...

# GOOD - Ask for clarification
Requirement unclear? Report BLOCKED:
"Cannot proceed: requirement 'improve performance' is ambiguous.
 Please specify: CPU performance, memory usage, or response time?"
```

## Escalation Flow

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

### User Escalation Format

When escalating to user, provide:

1. **Error message**: Clear description of failure
2. **Issue context**: Which issue/operation failed
3. **Details**: Relevant logs or state
4. **Suggested actions**: What user can do

```markdown
## Issue Failed: parse-switch-statements

**Error:** Unable to resolve merge conflict in Parser.java

**Context:**
- Branch: 1.0-parse-switch-statements
- Conflicting file: src/main/java/Parser.java

**Conflict Details:**
Lines 45-52 have conflicting changes from:
- main branch: Added null check
- issue branch: Refactored method signature

**Suggested Actions:**
1. Review conflict in Parser.java
2. Decide which changes to keep
3. Resume with /cat:work
```

### Main Agent Response to Subagent Skill Failures (M429)

**CRITICAL: When a subagent fails to invoke a skill, main agent must NOT do the work manually.**

```
Subagent delegated to invoke /cat:some-skill
          |
          v
Subagent returns FAILED (skill invocation issues)
          |
          v
Main agent MUST NOT:
  - Read files and do the work directly
  - "Take a more direct approach"
  - Bypass the skill with manual implementation
          |
          v
Main agent MUST:
  - Retry with clearer instructions OR
  - Escalate to user for guidance
```

**Why this matters:**
- Skills contain validation logic (e.g., `/cat:shrink-doc` uses `/cat:compare-docs` for equivalence)
- Manual implementation bypasses this validation
- The skill exists precisely BECAUSE manual work is error-prone or requires validation

**Escalation options when subagent fails at skill invocation:**

| Option | When to use |
|--------|-------------|
| Retry with simpler scope | If batch operation, try single file first |
| Retry with explicit instructions | Add "You MUST use Skill tool, NOT manual implementation" |
| Escalate to user | If retries fail, ask user how to proceed |

**Anti-pattern (M429):**
```
# BAD - Main agent sees subagent failure and decides to "just do it"
Subagent: "FAILED: recursion issues with /cat:shrink-doc"
Main agent: "Let me take a more direct approach..."
Main agent: [Uses Edit tool to compress file manually]

# GOOD - Main agent respects skill boundary
Subagent: "FAILED: recursion issues with /cat:shrink-doc"
Main agent: "The skill invocation failed. Let me retry with a single file first,
             or ask the user how to proceed."
```

## Recovery After Session Interruption

If session ends during execution:

1. STATE.md reflects last known state
2. Worktree may exist with partial work
3. User resumes with `/cat:work`
4. Main agent assesses state and continues or restarts

## Learn-from-Mistakes Integration

When analyzing mistakes, consider context factors:

| Factor | Consideration |
|--------|---------------|
| Conversation length | Late mistakes may indicate context degradation |
| Token usage | High usage correlates with errors |
| Compaction events | May signal lost context |

Recommendation: Decompose work earlier when context-related patterns emerge.
