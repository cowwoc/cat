# Subagent Delegation Principles

## Tool Access (M344)

**General-purpose subagents have access to ALL tools**, including Task and Skill.

If a PLAN.md or delegation prompt specifies using a skill (e.g., `/cat:shrink-doc`), invoke it
directly via the Skill tool. Do not assume tool limitations exist - subagents have full tool access.

## Spawning Subagents: Task vs TaskCreate (M372)

**Use the Task tool to spawn subagents, NOT TaskCreate.**

These are completely different tools:

| Tool | Purpose | What it does |
|------|---------|--------------|
| **Task** | Subagent spawner | Spawns a subagent that executes work and returns results |
| **TaskCreate** | Todo tracker | Adds an item to the task list UI (does NOT spawn anything) |

**Common confusion**: System reminders mention "TaskCreate" for task tracking. When you see
"Task tool:" in a skill, this means the **Task** tool (subagent spawner), not TaskCreate.

```
# ✅ CORRECT: Spawns a subagent
Task tool:
  subagent_type: "general-purpose"
  prompt: "Do the work..."

# ❌ WRONG: Just adds a todo item, nothing executes
TaskCreate:
  subject: "Do the work"
  description: "..."
```

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

### Acceptance Criteria Requirement (M270)

**CRITICAL: Every subagent delegation MUST include explicit acceptance criteria.**

Acceptance criteria define what specific outputs validate successful completion. Without them,
"success" becomes subjective and validation gets skipped.

**For issue-based delegations** (subagent working on a tracked issue):

1. Read the issue's PLAN.md `## Acceptance Criteria` section
2. Extract each measurable criterion (scores, test results, build status)
3. Include criteria in subagent prompt with explicit instruction to produce that output
4. On subagent completion, verify each criterion has evidence in output

```yaml
# Example: PLAN.md says "Execution equivalence verified (score = 1.0 from /compare-docs)"
subagent_prompt_must_include: |
  ACCEPTANCE CRITERIA:
  - Run /compare-docs on compressed file
  - Required score: 1.0
  - Include score in your output
```

**For ad-hoc delegations** (no tracked issue):

Parent agent must define acceptance criteria before spawning:

```yaml
acceptance_criteria:
  - what: "Measurable outcome 1"
    validation: "How to verify"
  - what: "Measurable outcome 2"
    validation: "How to verify"
```

**FAIL-FAST on missing validation:**

When subagent output lacks evidence of criteria satisfaction:

```
❌ ACCEPTANCE CRITERIA NOT MET

Required: {criterion}
Subagent output: {missing | value}

BLOCKING: Cannot proceed without validation evidence.
Action: Re-run validation or adjust approach.
```

**Why this exists (M270):** When subagent prompts bypass skill-mandated validations (e.g., custom
compression prompt without /compare-docs), criteria go unchecked. Requiring explicit criteria
in the delegation catches these gaps at spawn time rather than after completion.

## Common Failure Patterns

### Exploration + Decision in Same Delegation

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

### Output Format Priming (M274)

```
# ❌ WRONG: Output format specifies expected value
"OUTPUT FORMAT:
 - validation_score: 1.0 (required)
 - status: success"

# ✅ RIGHT: Output format specifies structure only
"OUTPUT FORMAT:
 - validation_score: {actual score from compare-docs}
 - status: {success if score >= threshold, else failed}"
```

**Why it fails**: Specifying expected values in output format tells the subagent what to report,
not what to measure. When actual results differ from expected, the subagent may report the expected
value rather than the actual value. This is a form of documentation priming (M269).

**Rule**: Output format defines *structure* (field names, types). Never include *content* (expected
values, required outcomes). Acceptance criteria belong in a separate section, not in output format.

### Validation Separation Requirement (M276)

**CRITICAL: Subagents that PRODUCE output must NOT also VALIDATE it.**

When a subagent creates/modifies content that requires validation:

```
# ❌ WRONG: Same subagent produces AND validates
"Compress these 40 files.
 Verify each scores 1.0 on /compare-docs."
# Subagent may skip validation or fabricate scores

# ✅ RIGHT: Separate production from validation
STEP 1: Subagent A compresses files (NO validation instruction)
STEP 2: Main agent OR Subagent B runs /compare-docs on each file
STEP 3: Main agent reviews actual scores, decides next action
```

**Why separation is mandatory:**
1. Producer bias: Subagent that created content is motivated to report success
2. Priming risk: Knowing the threshold (1.0) primes fabrication
3. No oversight: User cannot verify validation actually ran
4. Skill bypass: Custom prompts may omit skill-mandated validations

**Enforcement pattern:**

| Issue Type | Producer | Validator |
|------------|----------|-----------|
| Document compression | Compression subagent | Main agent via /compare-docs |
| Code generation | Implementation subagent | Test runner (separate step) |
| File transformation | Transform subagent | Diff/verification subagent |

**Main agent MUST verify validation evidence:**
- Check for actual tool invocations (not just claimed scores)
- Require per-file scores in structured format
- Cross-reference file count with validation count

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

Some work should NOT be delegated:

1. **Exploration + decision combined** - "figure out how X works and fix it"
2. **Design decisions** - "choose the best approach"
3. **Ambiguous requirements** - "handle edge cases appropriately"
4. **User-facing choices** - "pick good default values"
5. **Quality judgments** - "make the code clean"

These require the main agent (with user access) to make decisions.

## Valid Subagent Work

Subagents ARE appropriate for:

1. **Pure exploration** - "find all usages of X, return list" (no action)
2. **Research** - "what patterns does this codebase use for Y?" (report only)
3. **Mechanical implementation** - "add this exact code to these files"
4. **Verification** - "run these tests, report pass/fail"
5. **Data collection** - "count lines, list files, measure metrics"

The key: subagent returns information OR executes explicit instructions. Never both.
