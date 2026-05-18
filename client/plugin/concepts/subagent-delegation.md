<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Agent Delegation Principles

## Capability Limitations

If any step requires a tool or capability you don't have access to (e.g., Skill tool, spawning agents), return
BLOCKED status immediately. Do NOT silently substitute or work around missing capabilities.

When a delegation prompt instructs you to use a specific tool that is unavailable, fail-fast with:

```json
{
  "status": "BLOCKED",
  "reason": "Required tool not available: {tool_name}",
  "requested_capability": "{what the prompt asked for}",
  "available_alternatives": []
}
```

Do NOT attempt to use different tools, rewrite the approach, or continue without the required capability. The
orchestrating agent needs to know the exact blocker to adjust its delegation strategy.

## Tool Access

**General-purpose agents have access to ALL tools**, including Task and Skill.

If a plan.md or delegation prompt specifies using a skill (e.g., `cat:instruction-builder`), invoke it directly via the Skill
tool. Do not assume tool limitations exist - agents have full tool access.

## Spawning Agents: Task vs Agent vs TaskCreate

**Use the Task tool to spawn parallel job agents that require worktree isolation, NOT the Agent tool.**

These are three completely different tools:

- **Task**: Isolated agent spawner. Spawns with `isolation: "worktree"`, giving the agent its own git
  worktree. Commits made inside the agent persist after it completes and can be merged back.
- **Agent**: In-process agent spawner. No worktree isolation — the agent's git state and all its commits
  are destroyed when it completes.
- **TaskCreate**: Todo tracker. Adds an item to the task list UI (does NOT spawn anything).

**Critical distinction — Task vs Agent worktree behavior:**

When `work-implement` spawns job agents using `isolation: "worktree"`, the Task tool is **required**.
Using the Agent tool instead silently breaks worktree isolation:

- **Task tool**: agent gets its own git worktree; commits made inside survive after the agent finishes and can
  be merged back into the issue branch via the branch name in the Task result metadata.
- **Agent tool**: agent's worktree and all its commits are destroyed when the agent completes. The main agent
  receives the JSON result but the actual git commits are unrecoverable. The work appears to succeed but nothing
  was committed.

**Common confusion**: System reminders mention "TaskCreate" for task tracking. When you see
"Task tool:" in a skill, this means the **Task** tool (agent spawner), not TaskCreate.

```
# ✅ CORRECT: Isolated worktree — commits survive, can be merged back
Task tool:
  subagent_type: "cat:work-execute"
  isolation: "worktree"
  prompt: "Implement job 1..."

# ❌ WRONG: No isolation — worktree and commits destroyed on completion
Agent tool:
  subagent_type: "cat:work-execute"
  prompt: "Implement job 1..."

# ❌ WRONG: Just adds a todo item, nothing executes
TaskCreate:
  subject: "Do the work"
  description: "..."
```

## Model Selection for Agents

**MANDATORY: Always specify a model explicitly. Never use the default.**

Choose the model based on issue complexity:

| Issue Type | Model | Reasoning |
|-----------|-------|-----------|
| Skill invocation (orchestration only) | `haiku` | Skill is pure orchestration, agent just runs it |
| Skill invocation (skill exposes algorithm) | `sonnet` | Skill doc shows HOW to do it; haiku will apply algorithm manually |
| Simple file operations | `haiku` | Explicit instructions, no reasoning needed |
| Run commands, check output | `haiku` | Purely mechanical execution |
| Code refactoring | `sonnet` | Requires understanding patterns and context |
| Multi-file changes | `sonnet` | Needs to maintain consistency across files |
| Exploration/research | `sonnet` | Requires judgment about what's relevant |
| Complex logic changes | `sonnet` | Must reason about correctness |
| Critical validation gates | `opus` | Asymmetric failure costs justify higher accuracy |

**Decision rule:** If the execution plan can be followed with zero reasoning (copy-paste level
explicit), use `haiku`. If the agent needs to understand WHY to do something correctly,
use `sonnet`. If failure would be very costly or the task requires generating novel approaches,
consider `opus`.

### When to Use Opus (Rare Cases)

**Opus is the exception, not the default.** Most delegated work should use haiku or sonnet.

Use Opus only when:

1. **Critical validation gates** - When the cost of a false positive (incorrectly passing) is much
   higher than the cost of running a more capable model. Examples:
   - Security review of authentication changes
   - Validating semantic equivalence of compressed documentation
   - Final quality gate before production deployment

2. **Complex architectural analysis** - Evaluating tradeoffs across multiple systems, identifying
   non-obvious dependencies, or reasoning about emergent behavior.

**Signal to reconsider delegation:** If you find yourself reaching for Opus, ask whether this work
should be delegated at all. Work requiring Opus-level reasoning often benefits from:
- Main agent handling it directly (with user oversight)
- Breaking into smaller pieces that sonnet can handle
- More explicit specifications that reduce reasoning requirements

**Anti-pattern:**
```
❌ model: "opus" for mechanical file operations (wasteful)
❌ model: "opus" for straightforward code changes (sonnet suffices)
❌ model: "opus" as a "just to be safe" default (defeats cost efficiency)
✅ model: "opus" for security-critical validation gates
```

**Anti-pattern:**
```
❌ Task tool: subagent_type: "general-purpose" (missing model - uses expensive default)
❌ Task tool: model: "haiku" for code refactoring (will likely fail)
❌ Task tool: model: "haiku" for "cat:instruction-builder file.md" (skill exposes algorithm)
✅ Task tool: model: "sonnet" for "cat:instruction-builder file.md" (skill doc shows HOW, needs reasoning)
✅ Task tool: model: "sonnet" for "refactor these 4 handlers" (needs reasoning)
✅ Task tool: model: "haiku" for "/cat:status" (pure orchestration, no algorithm exposed)
```

## Core Constraint

**Claude Code does not allow users to supervise agent execution.**

When an agent is spawned:
- User cannot see what the agent is doing
- User cannot correct mistakes in real-time
- User cannot answer questions
- User cannot provide clarification
- Agent cannot ask for help

The agent runs to completion (or failure) without any human oversight.

## Implications for Main Agent

### Decision Authority

The main agent is the **decision maker**. The agent is the **executor** or **information gatherer**.

| Main Agent Does | Agent Does |
|-----------------|---------------|
| Make decisions | Execute decisions OR gather info |
| Choose approach | Follow approach (never choose) |
| Resolve ambiguities | Report ambiguities, fail-fast |
| Define post-conditions | Verify against post-conditions |
| Review exploration results | Return exploration results |
| Handle failures | Report failures immediately |

### Fail-Fast Requirement

**CRITICAL**: Agents must fail-fast when encountering problems.

```
# ❌ WRONG: Agent tries fallback behaviors
"If you can't find the auth module, look in legacy/ or try common patterns"

# ✅ RIGHT: Agent stops and reports
"Find the auth module in src/auth/.
 FAIL-FAST: If not found within 5 minutes, report:
   'BLOCKED: Auth module not at expected location src/auth/'
 Do NOT search elsewhere or guess."
```

**Why**: Fallback behaviors involve decisions. Those decisions happen without user oversight.
Better to fail and let the main agent (with user access) decide how to proceed.

### Prompt = Complete Specification

The agent prompt is not a "request" - it's a **complete specification** that requires no
interpretation. Think of it like:

- **Bad**: Email to a colleague ("Can you handle the auth stuff?")
- **Good**: Manufacturing blueprint (exact dimensions, materials, tolerances)

### Pre-Spawn Checklist

Before spawning, the main agent must be able to answer "yes" to all:

1. Have I read every file the agent will modify?
2. Have I made every design/architecture decision?
3. Can I provide actual code, not just descriptions?
4. Do I know exactly what "success" looks like?
5. Have I specified what to do if things go wrong?
6. Is my commit message written?
7. If the agent will write or edit files, have I included relevant project conventions
   (e.g., line wrapping, license headers, language-specific style) in the prompt?

If any answer is "no", do not spawn. Gather more information first.

### Acceptance Criteria Requirement

**CRITICAL: Every agent delegation MUST include explicit acceptance criteria.**

Acceptance criteria define what specific outputs validate successful completion. Without them,
"success" becomes subjective and validation gets skipped.

**For issue-based delegations** (agent working on a tracked issue):

1. Read the issue's plan.md `## Acceptance Criteria` section
2. Extract each measurable criterion (scores, test results, build status)
3. Include criteria in agent prompt with explicit instruction to produce that output
4. On agent completion, verify each criterion has evidence in output

```yaml
# Example: plan.md says "Execution equivalence verified (score = 1.0 from validation protocol)"
subagent_prompt_must_include: |
  ACCEPTANCE CRITERIA:
  - Run validation protocol from cat:instruction-builder on compressed file
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

When agent output lacks evidence of criteria satisfaction:

```
❌ ACCEPTANCE CRITERIA NOT MET

Required: {criterion}
Agent output: {missing | value}

BLOCKING: Cannot proceed without validation evidence.
Action: Re-run validation or adjust approach.
```

**Why this exists:** When agent prompts bypass skill-mandated validations (e.g., custom
compression prompt without validation), criteria go unchecked. Requiring explicit criteria
in the delegation catches these gaps at spawn time rather than after completion.

## Common Failure Patterns

### Exploration + Decision in Same Delegation

Agents CAN explore/research. They must NOT decide based on findings.

```
# ❌ WRONG: Agent explores AND decides
"Find where rate limiting should be added and implement it"

# ✅ RIGHT: Agent explores, returns findings only
"Find all places where rate limiting could be added.
 Return: file paths, method signatures, current behavior.
 FAIL-FAST: If unclear after 10 min, report BLOCKED.
 Do NOT implement - return findings for review."

# ✅ ALSO RIGHT: Main agent already knows, gives exact instructions
"Add rate limiting to src/auth/AuthService.java line 45:
 [exact code to add]"
```

**Why it fails**: When exploration and implementation are combined, the agent makes decisions
the user can't review. Separating them lets the main agent (with user access) make decisions.

### Delegating Decisions

```
# ❌ WRONG: Agent must choose
"Use appropriate error handling for the network calls"

# ✅ RIGHT: Decision made by main agent
"Wrap network calls in try-catch:
 - SocketTimeoutException: retry 3 times with exponential backoff
 - IOException: log error, return Optional.empty()
 - Other exceptions: rethrow wrapped in NetworkException"
```

**Why it fails**: "Appropriate" is subjective. The agent's choice may not match user expectations.

### Vague Success Criteria

```
# ❌ WRONG: Agent must judge
"Make sure the feature works correctly"

# ✅ RIGHT: Objective verification
"Run ./gradlew test --tests 'FeatureTest'
 Expected: 8 tests pass, 0 failures
 Run ./scripts/integration-test.sh
 Expected output: 'All scenarios passed'"
```

**Why it fails**: Without objective criteria, agent may declare success when user would not.

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

**Why it fails**: Agent implements the obvious case; edge cases cause silent bugs.

### Output Format Priming

```
# ❌ WRONG: Output format specifies expected value
"OUTPUT FORMAT:
 - validation_score: 1.0 (required)
 - status: success"

# ✅ RIGHT: Output format specifies structure only
"OUTPUT FORMAT:
 - validation_score: {actual score from instruction-builder validation}
 - status: {success if score >= threshold, else failed}"
```

**Why it fails**: Specifying expected values in output format tells the agent what to report,
not what to measure. When actual results differ from expected, the agent may report the expected
value rather than the actual value. This is a form of documentation priming.

**Rule**: Output format defines *structure* (field names, types). Never include *content* (expected
values, required outcomes). Acceptance criteria belong in a separate section, not in output format.

### Validation Separation Requirement

**CRITICAL: Agents that PRODUCE output must NOT also VALIDATE it.**

When an agent creates/modifies content that requires validation:

```
# ❌ WRONG: Same agent produces AND validates
"Compress these 40 files.
 Verify each scores 1.0 via validation protocol."
# Agent may skip validation or fabricate scores

# ✅ RIGHT: Separate production from validation
STEP 1: Agent A compresses files (NO validation instruction)
STEP 2: Main agent OR Agent B runs validation protocol from cat:instruction-builder on each file
STEP 3: Main agent reviews actual scores, decides next action
```

**Why separation is mandatory:**
1. Producer bias: Agent that created content is motivated to report success
2. Priming risk: Knowing the threshold (1.0) primes fabrication
3. No oversight: User cannot verify validation actually ran
4. Skill bypass: Custom prompts may omit skill-mandated validations

**Enforcement pattern:**

| Issue Type | Producer | Validator |
|------------|----------|-----------|
| Document compression | Compression agent | Main agent via `cat:instruction-builder` validation protocol |
| Code generation | Implementation agent | Test runner (separate step) |
| File transformation | Transform agent | Diff/verification agent |

**Main agent MUST verify validation evidence:**
- Check for actual tool invocations (not just claimed scores)
- Require per-file scores in structured format
- Cross-reference file count with validation count

## Result Presentation

When presenting agent results to users, preserve the format specified by the source skill.

**Problem:** Agents return structured data (JSON). Presenting agents may recompose this into
custom formats, losing units, context, or required formatting from the source skill.

**Rule:** Before presenting results, check if the invoked skill specifies an output format.
If so, use that format - don't compose your own.

| Source | Format Location | Example |
|--------|-----------------|---------|
| cat:instruction-builder | SKILL.md output section | Table with "Tokens" header |
| cat:instruction-builder validation | validation-protocol.md | Comparison report format |
| /cat:status | Handler preprocessing | Status box (skill output) |

**Pattern for presenting skill results:**

```
# ❌ WRONG: Recompose results into custom format
Agent returns: {"tokens_before": 1598, "tokens_after": 1278}
You present: "| Before | After |" (units unclear)

# ✅ RIGHT: Use source skill's format
Agent returns: {"tokens_before": 1598, "tokens_after": 1278}
Check cat:instruction-builder format specification
Present: "| Tokens (Before) | Tokens (After) |" (matches skill spec)
```

**When skill has no format specification:** Include units in all numeric column headers.

## Quality Indicators

### Prompt Length

Good agent prompts are **longer than you'd expect**. If your prompt is a few sentences, it's
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

## When Not to Use Agents

Some work should NOT be delegated:

1. **Exploration + decision combined** - "figure out how X works and fix it"
2. **Design decisions** - "choose the best approach"
3. **Ambiguous requirements** - "handle edge cases appropriately"
4. **User-facing choices** - "pick good default values"
5. **Quality judgments** - "make the code clean"

These require the main agent (with user access) to make decisions.

## Valid Agent Work

Agents ARE appropriate for:

1. **Pure exploration** - "find all usages of X, return list" (no action)
2. **Research** - "what patterns does this codebase use for Y?" (report only)
3. **Mechanical implementation** - "add this exact code to these files"
4. **Verification** - "run these tests, report pass/fail"
5. **Data collection** - "count lines, list files, measure metrics"

The key: agent returns information OR executes explicit instructions. Never both.
