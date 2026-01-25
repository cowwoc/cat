# Subagent Delegation Principles

## Core Constraint

**Claude Code does not allow users to supervise subagent execution.**

When a subagent is spawned:
- User cannot see what the subagent is doing
- User cannot correct mistakes in real-time
- User cannot answer questions
- User cannot provide clarification
- Subagent cannot ask for help

The subagent runs to completion (or failure) without any human oversight.

## Implications for Main Agent

### Decision Authority

The main agent is the **decision maker**. The subagent is the **executor** or **information gatherer**.

| Main Agent Does | Subagent Does |
|-----------------|---------------|
| Make decisions | Execute decisions OR gather info |
| Choose approach | Follow approach (never choose) |
| Resolve ambiguities | Report ambiguities, fail-fast |
| Define success criteria | Verify against criteria |
| Review exploration results | Return exploration results |
| Handle failures | Report failures immediately |

### Fail-Fast Requirement

**CRITICAL**: Subagents must fail-fast when encountering problems.

```
# ❌ WRONG: Subagent tries fallback behaviors
"If you can't find the auth module, look in legacy/ or try common patterns"

# ✅ RIGHT: Subagent stops and reports
"Find the auth module in src/auth/.
 FAIL-FAST: If not found within 5 minutes, report:
   'BLOCKED: Auth module not at expected location src/auth/'
 Do NOT search elsewhere or guess."
```

**Why**: Fallback behaviors involve decisions. Those decisions happen without user oversight.
Better to fail and let the main agent (with user access) decide how to proceed.

### Prompt = Complete Specification

The subagent prompt is not a "request" - it's a **complete specification** that requires no
interpretation. Think of it like:

- **Bad**: Email to a colleague ("Can you handle the auth stuff?")
- **Good**: Manufacturing blueprint (exact dimensions, materials, tolerances)

### Pre-Spawn Checklist

Before spawning, the main agent must be able to answer "yes" to all:

1. Have I read every file the subagent will modify?
2. Have I made every design/architecture decision?
3. Can I provide actual code, not just descriptions?
4. Do I know exactly what "success" looks like?
5. Have I specified what to do if things go wrong?
6. Is my commit message written?

If any answer is "no", do not spawn. Gather more information first.

## Common Failure Patterns

### Exploration + Decision in Same Task

Subagents CAN explore/research. They must NOT decide based on findings.

```
# ❌ WRONG: Subagent explores AND decides
"Find where rate limiting should be added and implement it"

# ✅ RIGHT: Subagent explores, returns findings only
"Find all places where rate limiting could be added.
 Return: file paths, method signatures, current behavior.
 FAIL-FAST: If unclear after 10 min, report BLOCKED.
 Do NOT implement - return findings for review."

# ✅ ALSO RIGHT: Main agent already knows, gives exact instructions
"Add rate limiting to src/auth/AuthService.java line 45:
 [exact code to add]"
```

**Why it fails**: When exploration and implementation are combined, the subagent makes decisions
the user can't review. Separating them lets the main agent (with user access) make decisions.

### Delegating Decisions

```
# ❌ WRONG: Subagent must choose
"Use appropriate error handling for the network calls"

# ✅ RIGHT: Decision made by main agent
"Wrap network calls in try-catch:
 - SocketTimeoutException: retry 3 times with exponential backoff
 - IOException: log error, return Optional.empty()
 - Other exceptions: rethrow wrapped in NetworkException"
```

**Why it fails**: "Appropriate" is subjective. The subagent's choice may not match user expectations.

### Vague Success Criteria

```
# ❌ WRONG: Subagent must judge
"Make sure the feature works correctly"

# ✅ RIGHT: Objective verification
"Run ./gradlew test --tests 'FeatureTest'
 Expected: 8 tests pass, 0 failures
 Run ./scripts/integration-test.sh
 Expected output: 'All scenarios passed'"
```

**Why it fails**: Without objective criteria, subagent may declare success when user would not.

### Incomplete Edge Cases

```
# ❌ WRONG: Only happy path specified
"Parse the JSON input and extract the user data"

# ✅ RIGHT: All cases covered
"Parse JSON input:
 - Valid JSON with user field: extract and return UserData
 - Valid JSON without user field: throw MissingFieldException
 - Invalid JSON: throw ParseException with position
 - Null input: throw IllegalArgumentException
 - Empty string: throw IllegalArgumentException"
```

**Why it fails**: Subagent implements the obvious case; edge cases cause silent bugs.

## Quality Indicators

### Prompt Length

Good subagent prompts are **longer than you'd expect**. If your prompt is a few sentences, it's
probably missing something.

Typical good prompt includes:
- 50-200 lines of specification
- Actual code blocks (not pseudocode)
- Explicit file paths
- Verification commands with expected output
- Error handling instructions
- Commit message text

### Self-Test: The Robot Test

Imagine giving your prompt to a robot that:
- Has no judgment or intuition
- Cannot ask clarifying questions
- Takes everything literally
- Has no context beyond the prompt

Would the robot produce correct output? If not, the prompt needs more detail.

## When Not to Use Subagents

Some tasks should NOT be delegated:

1. **Exploration + decision combined** - "figure out how X works and fix it"
2. **Design decisions** - "choose the best approach"
3. **Ambiguous requirements** - "handle edge cases appropriately"
4. **User-facing choices** - "pick good default values"
5. **Quality judgments** - "make the code clean"

These require the main agent (with user access) to make decisions.

## Valid Subagent Tasks

Subagents ARE appropriate for:

1. **Pure exploration** - "find all usages of X, return list" (no action)
2. **Research** - "what patterns does this codebase use for Y?" (report only)
3. **Mechanical implementation** - "add this exact code to these files"
4. **Verification** - "run these tests, report pass/fail"
5. **Data collection** - "count lines, list files, measure metrics"

The key: subagent returns information OR executes explicit instructions. Never both.
