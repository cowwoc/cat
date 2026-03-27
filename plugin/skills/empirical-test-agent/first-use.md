<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Empirical Compliance Test

Systematically troubleshoot agent compliance failures using controlled experiments with statistical trials.

## When to Use

Use when documentation is correct but agents don't follow instructions reliably. Common symptoms:
- Agent summarizes instead of echoing content
- Agent skips steps or adds unwanted content
- Agent uses wrong tools or wrong approach
- Behavior differs between models (haiku vs sonnet)
- Behavior degrades after tool-use turns

## Arguments

| Format | Example | Behavior |
|--------|---------|----------|
| Empty | (no arguments) | Interactive: gather failure description |
| Description | `"haiku doesn't echo status box"` | Start with provided description |

## Methodology

This skill implements an 8-step empirical testing methodology:

1. **Define** the compliance failure (what should happen vs what does happen)
2. **Baseline** reproduce the failure with a controlled test config
3. **Examine** full agent responses to understand what the agent actually produced
4. **Hypothesize** which elements cause the failure
5. **Isolate** variables with controlled test configurations (change one thing at a time)
6. **Analyze** results to identify the root cause
7. **Fix** by testing candidate solutions with the same methodology
8. **Report** findings with data tables

## Step 1: Define the Failure

Gather from the user (or from ARGUMENTS):

- **Expected behavior**: What the agent should do
- **Actual behavior**: What the agent does instead
- **Affected model(s)**: Which models fail (haiku, sonnet, both)
- **Context**: What happens before the failure (tool use, conversation history)
- **Skill/prompt**: The exact prompt or skill content that triggers the failure

If ARGUMENTS provides a description, use it. Otherwise, use AskUserQuestion:

```
question: "Describe the compliance failure"
options:
  - "Agent doesn't echo content verbatim"
  - "Agent skips documented steps"
  - "Agent uses wrong tools"
  - "Other (describe in text)"
```

## Step 2: Create Baseline Test Config

Build the initial test configuration to reproduce the failure.

**Create a JSON config file** at `/tmp/empirical-test-config.json`:

```json
{
    "target_description": "[Description of expected behavior]",
    "system_prompt": "Optional string passed as --append-system-prompt to claude CLI",
    "system_reminders": [
        "Content injected as <system-reminder> tags in the test prompt user message"
    ],
    "priming_messages": [
        "User message text",
        {"type": "tool_use", "tool": "Bash", "input": {"command": "git branch --show-current"}, "output": "v2.1"},
        {"type": "tool_use", "tool": "Bash", "input": {"command": "git log --oneline -3"}, "output": "abc123 feat"}
    ],
    "configs": {
        "A_baseline": {
            "messages": [
                {
                    "prompt": "[The exact prompt/skill content that fails]",
                    "success_criteria": {
                        "must_contain": ["text the output must contain"],
                        "must_not_contain": ["text that indicates failure"],
                        "must_use_tools": [],
                        "must_not_use_tools": []
                    }
                }
            ]
        }
    }
}
```

**Priming messages** simulate prior conversation context. Each message can be:
- **String** - Sent as a user message (backward compatible)
- **Object with type "tool_use"** - Generates assistant tool_use + user tool_result messages

Use tool_use priming to simulate scenarios where the failure occurs after tool execution (e.g., after running `git
branch` or an agent status check). This is critical for reproducing failures that only occur in post-tool-use context.

**System prompt** is an optional string passed as `--append-system-prompt` to the claude CLI, simulating project
instructions like CLAUDE.md content that would be present in a real session.

**MANDATORY for verbatim-output skills:** When testing a skill that requires the agent to echo, copy, or reproduce
content exactly, always populate `system_prompt` with the project's CLAUDE.md content. General project instructions
like "be helpful" or "be concise" actively compete with verbatim-copy instructions, and tests without this context
will report artificially high success rates that do not reflect real sessions. Omitting the system prompt is the
primary cause of false positives in empirical tests.

```bash
# Read CLAUDE.md for use as system_prompt in verbatim-output skill tests
CLAUDE_MD_CONTENT=$(cat "${CLAUDE_PROJECT_DIR}/CLAUDE.md")
# Include as the "system_prompt" field in the test config JSON
```

**System reminders** are optional strings injected into the test prompt user message, each wrapped in
`<system-reminder>` tags. This simulates hook-injected system reminders that appear in production sessions and can
affect agent behavior.

**Success criteria** are defined per message inside the `messages` array (not at the top level):
- `must_contain`: Strings that must appear in agent output (case-insensitive)
- `must_not_contain`: Strings that indicate failure
- `must_use_tools`: Tools the agent must invoke
- `must_not_use_tools`: Tools the agent must not invoke

**Priming message examples:**

```json
"priming_messages": [
  "User asked: What is the current branch?",
  {"type": "tool_use", "tool": "Bash", "input": {"command": "git branch --show-current"}, "output": "main"},
  {"type": "tool_use", "tool": "Read", "input": {"file_path": "/workspace/README.md"}, "output": "# Project\n..."}
]
```

This generates the conversation:
1. User message: "User asked: What is the current branch?"
2. Assistant tool_use: Bash with command "git branch --show-current"
3. User tool_result: "main"
4. Assistant tool_use: Read with file_path "/workspace/README.md"
5. User tool_result: "# Project\n..."
6. User message: [test prompt]

## Step 3: Run Baseline and Confirm Failure

Run the baseline to confirm the failure reproduces:

```bash
CLIENT_BIN="${WORKTREE_PATH}/client/target/jlink/bin"
if [[ ! -x "$CLIENT_BIN/empirical-test-runner" ]]; then
  CLIENT_BIN="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin"
fi

"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-config.json \
    --trials 1 \
    --model haiku
```

**Interpreting results:**

| Baseline Rate | Interpretation | Next Step |
|---------------|---------------|-----------|
| 0% | Failure confirmed | Proceed to isolation |
| 100% | Single trial cannot confirm — expand to 5 trials | Run with `--trials 5`, then verify priming context matches real usage |

If the baseline shows 80%+ success after 5 trials, the failure may depend on specific conversation context. Add more
priming messages or adjust the test prompt to match the real failure scenario.

## Step 4: Examine Raw JSONL from Test Trials

When baseline confirms failure (0% success rate), run with `--output` to capture full agent responses, then examine
the raw JSONL session files to understand what the agent actually received and produced. The summary output shows only
a short preview — raw JSONL reveals the complete conversation, including what content was actually delivered vs what
the source files intended to deliver.

```bash
"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-config.json \
    --trials 5 \
    --model haiku \
    --output /tmp/empirical-test-baseline.json
```

The output file records the session ID for each trial. Use session-analyzer to examine the raw JSONL as the primary
source of evidence for what the agent received:

```bash
SESSION_ANALYZER="${WORKTREE_PATH}/client/target/jlink/bin/session-analyzer"
if [[ ! -x "$SESSION_ANALYZER" ]]; then
  SESSION_ANALYZER="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin/session-analyzer"
fi

# Find the session ID for a specific trial (from --output JSON session_id field)
TRIAL_SESSION_ID="TRIAL-SESSION-ID"  # from --output JSON session_id field
```

**Session-analyzer commands** — choose the right one for your investigation need:

| Command | When to Use |
|---------|-------------|
| `search <session-id> <keyword> --context N` | Find specific content the agent received (test prompt, system prompt) |
| `errors <session-id>` | Find tool failures, non-zero exit codes, error patterns |
| `analyze <session-id>` | Get full session overview including subagent discovery |

```bash
# Find what content was delivered for the test prompt
"$SESSION_ANALYZER" search "$TRIAL_SESSION_ID" "keyword-from-test-prompt" --context 5

# Find tool errors in the trial
"$SESSION_ANALYZER" errors "$TRIAL_SESSION_ID"

# Get session overview (useful if trial spawned subagents)
"$SESSION_ANALYZER" analyze "$TRIAL_SESSION_ID"
```

**What to verify in raw JSONL before assuming the source content was delivered intact:**
- Did the test prompt content appear in the agent's context exactly as written?
- Was any content transformed, truncated, or altered during delivery?
- Did the agent receive the system prompt and system reminders as configured?
- If JSONL content differs from the test config, flag the discrepancy — this is a delivery issue, not a compliance
  issue

**After JSONL examination**, open `/tmp/empirical-test-baseline.json` and examine the `results` field for the
structured trial summaries. Each trial contains:
- `outputPreview`: a 300-character preview of the agent's response
- `toolsUsed`: which tools the agent called
- `checks`: which success criteria passed or failed

**Multi-message evaluation warning:** When `priming_messages` exist, the agent processes each message and produces a
response. Failures in priming responses (e.g., a tool_use turn) do not indicate the test prompt failed — they may be
expected intermediate steps. Check which specific response triggered the `success_criteria` evaluation. Only the final
response to the test prompt is evaluated against the criteria.

Use the raw JSONL and full output together to form a hypothesis about what the agent produced (hallucinating content,
refusing to act, using wrong format) before attempting isolation. Isolation experiments are more targeted when the
failure mode is understood from actual delivered content, not assumed from source files.

## Step 5: Isolate Variables

Generate test configurations that vary one element at a time from the failing baseline. Each hypothesis becomes a
config.

**Common variables to isolate:**

| Variable | What to Test | Example Configs |
|----------|-------------|-----------------|
| **Instruction wording** | Different phrasings of the same instruction | A: "respond verbatim", B: "echo exactly" |
| **Instruction ordering** | Move instructions before/after content | A: instruction first, B: content first |
| **Conditional checks** | Remove/reframe conditional logic | A: with "if not", B: without conditional |
| **Bold formatting** | Remove `**KEYWORD:**` bold labels | A: with bold, B: without bold |
| **Description lines** | Remove meta-descriptions | A: with description, B: without |
| **Negative framing** | Replace "don't/not" with positive | A: "do NOT", B: positive framing |
| **Context/motivation** | Add WHY explanation | A: bare instruction, B: with explanation |
| **Priming context** | With/without tool-use turns | A: with priming, B: no priming |
| **Content amount** | More/less surrounding content | A: minimal, B: full production |

**Key principle: Change exactly ONE variable per config.** Start from the baseline and modify a single element.

Update the config file with isolation configs and re-run:

```bash
"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-config.json \
    --trials 5 \
    --model haiku
```

## Step 6: Analyze Results

Read the results table. The root cause is identified by which config restores high success rates.

**Analysis patterns:**

| Pattern | Root Cause | Fix Strategy |
|---------|-----------|-------------|
| Removing element X restores 100% | Element X causes failure | Remove or reframe X |
| Reordering restores success | Instruction ordering matters | Move critical instruction first |
| Adding WHY restores success | Missing context/motivation | Add explanatory framing |
| No priming → 100%, with priming → 0% | Tool-use context interference | Structural separation needed |
| Nothing restores success | Fundamental model limitation | Use different model or approach |

**If results are ambiguous** (multiple configs show partial improvement), run a second round combining the top
improvements:

```json
{
    "configs": {
        "A_original": {
            "messages": [{"prompt": "...", "success_criteria": {"must_contain": ["expected"]}}]
        },
        "B_best_single_fix": {
            "messages": [{"prompt": "...", "success_criteria": {"must_contain": ["expected"]}}]
        },
        "C_combined_top2": {
            "messages": [{"prompt": "...", "success_criteria": {"must_contain": ["expected"]}}]
        },
        "D_combined_top3": {
            "messages": [{"prompt": "...", "success_criteria": {"must_contain": ["expected"]}}]
        }
    }
}
```

## Step 7: Test the Fix

Once the root cause is identified, create the candidate fix and test it in production context:

**CRITICAL: Fix the skill under test, not the test cases.**

When compliance is low (e.g., 0% or 33%), the default assumption is that the skill/agent is broken. Do NOT mask
underlying issues by tweaking test prompts.

**When to Modify Test Cases:**

Modify test cases ONLY if the test itself was fundamentally broken from the start. A test is fundamentally broken if:

**Example 1: Test was measuring the wrong behavior**
- Original test expected: "Agent skips this step when configured with low trust"
- But the rule says: "This step is mandatory and cannot be skipped"
- Fix: Correct the test expectation, not the skill (the skill is correct, the test was wrong)

**Example 2: Test prompt is ambiguous or contradicts the rule being tested**
- Test prompt says: "Use tool X"
- Skill instructions say: "Tool X is not available; use tool Y instead"
- Fix: Clarify the test prompt (remove the contradiction)

**When to Fix the Skill (Default):**

When compliance is low, assume the skill/agent is broken and needs fixing. Common scenarios:

**Example 3: Agent doesn't follow a clearly documented rule**
- Rule says: "Always echo content verbatim"
- Test has agent echo content, but agent is paraphrasing (0% pass rate)
- Fix: Improve the skill instructions or system prompt so agent follows the rule

**Example 4: Agent uses wrong approach despite clear guidance**
- Rule says: "Use tool X to validate the data"
- Agent is not using tool X even with clear instructions (33% pass rate over 5 trials)
- Fix: Add stronger instruction or example to the skill

**Decision Rule:** If the test was written correctly (prompts are clear, expectations match documented rules),
and compliance is still low, the skill is broken. Fix the skill, not the test.

1. Apply the fix to the actual skill/prompt file
2. Create a new config that uses the **actual file content** (not a simplified version)
3. Include **all production elements** (priming, prefix, full content, and system prompt from CLAUDE.md)
4. Run with higher trial count (10-15) for statistical confidence

```bash
"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-final.json \
    --trials 10 \
    --model haiku \
    --output /tmp/empirical-test-results.json
```

**Acceptance thresholds:**

| Rate | Decision |
|------|----------|
| 90-100% | Fix is effective, proceed to commit |
| 70-89% | Fix helps but may need additional changes |
| Below 70% | Fix is insufficient, return to isolation |

## Step 8: Report Findings

Present a summary report to the user:

```
## Empirical Compliance Test Results

**Failure:** [description]
**Root Cause:** [what element caused the failure and why]
**Fix:** [what was changed]
**Evidence:** [key data points]

| Config | Rate | Notes |
|--------|------|-------|
| A_baseline (original) | {actual}% | Reproduces failure |
| B_without_X | {actual}% | Effect of removing X |
| C_with_fix | {actual}% | Production validation |

**Mechanism:** [explanation of why the root cause affects agent behavior]
```

## Tips

- **Start with haiku** — it's cheaper and more sensitive to prompt issues. If haiku passes, sonnet will too.
- **Use 1 trial** for baseline and isolation (quick signal), **5 trials** for confirmation, **10 trials** for
  final validation.
- **Priming matters** — always include tool-use priming if the real scenario has it.
- **Include system prompt for verbatim-output tests** — tests of skills that echo content verbatim MUST include
  CLAUDE.md content as the `system_prompt`. General project instructions compete with verbatim-copy instructions
  and cause false positives when omitted (found in M361: test showed 100% but production failed).
- **Test production content** — simplified test prompts may pass when production content fails (found in M507:
  isolated test 100% vs production 33%).
- **Description lines hurt** — meta-descriptions like "Display current status" cause haiku to treat them as tasks.
- **Ordering matters** — put the most important instruction first (primacy effect).
- **Explain WHY** — Anthropic docs: "Add context/motivation to improve performance."

## Related Skills

- `cat:learn` - Record the mistake and root cause analysis
- `cat:instruction-builder-agent` - Create or update skills with compliance-tested patterns

---

## Subagent Execution Instructions

**Purpose**: Run controlled compliance experiments to measure agent behavior across prompt configurations.
Executes N trials per configuration and reports pass rates, structured grading evidence, post-hoc analysis
of failures, and blind comparison between prompt candidates.

### When to Use as a Subagent

- Validating that a new instruction or rule is reliably followed
- Comparing two system prompt variants to determine which is more effective
- Diagnosing why an agent fails a specific behavior requirement
- Measuring regression risk before deploying prompt changes
- Building confidence that edge cases are handled correctly

### Hypothesis-Driven Test Design

Before writing a test, define a clear hypothesis. A well-formed hypothesis has three parts:

1. **What behavior** the agent should exhibit
2. **Under what conditions** (input, context, prior turns)
3. **Why it matters** (the consequence of the agent getting it wrong)

Structure your test suite around three case types:

- **Happy path**: The agent receives the canonical input and should succeed easily
- **Edge case**: Boundary conditions (empty input, max length, ambiguous phrasing)
- **Adversarial case**: Inputs that tempt the agent to violate the rule (e.g. user asks it to skip a required step)

A test suite with only happy-path cases will over-report compliance. Always include at least one adversarial
case per behavior under test.

### Test Config JSON Schema

```json
{
  "target_description": "Human-readable description of what is being tested",
  "system_prompt": "Optional prompt appended via --append-system-prompt",
  "priming_messages": [],
  "system_reminders": [],
  "configs": {
    "config_name": {
      "messages": [
        {
          "prompt": "The user message to send",
          "success_criteria": {
            "must_contain": ["expected phrase"],
            "must_not_contain": ["forbidden phrase"],
            "must_use_tools": ["Bash"],
            "must_not_use_tools": ["Write"],
            "_metadata": {
              "contains:expected phrase": {
                "description": "Agent confirms the action was taken",
                "reason": "Without this confirmation the user has no feedback",
                "severity": "HIGH"
              }
            }
          }
        }
      ]
    }
  }
}
```

### Structured Grading with `_metadata`

Each criterion key can have rich metadata under `_metadata` to describe what is being tested and why:

| Field | Type | Default | Purpose |
|-------|------|---------|---------|
| `description` | string | criterion key | What the criterion tests |
| `reason` | string | `""` | Why the criterion matters |
| `severity` | `HIGH`/`MEDIUM`/`LOW` | `MEDIUM` | Impact if the criterion fails |

Existing configs without `_metadata` continue to work unchanged — severity defaults to MEDIUM and
description defaults to the criterion key name.

**Example with rich metadata:**

```json
"success_criteria": {
  "must_contain": ["commit hash"],
  "must_use_tools": ["Bash"],
  "_metadata": {
    "contains:commit hash": {
      "description": "Agent reports the commit hash after committing",
      "reason": "User needs the hash to reference the commit later",
      "severity": "HIGH"
    },
    "uses_tool:Bash": {
      "description": "Agent uses Bash to run git commit",
      "reason": "Ensuring the commit actually happens rather than being simulated",
      "severity": "HIGH"
    }
  }
}
```

### Running Tests

```bash
empirical-test-runner --config /path/to/test.json --trials 10 --model haiku
```

Options:

| Flag | Default | Purpose |
|------|---------|---------|
| `--config <path>` | required | Path to test config JSON |
| `--trials <N>` | 10 | Trials per configuration |
| `--model <name>` | haiku | Model to test with |
| `--cwd <path>` | `/workspace` | Working directory for claude CLI |
| `--output <path>` | none | Write full JSON results |
| `--baseline <prompt>` | none | Enable blind comparison mode |

### Blind Comparison Workflow

Blind comparison runs the same config against two system prompts — a **candidate** and a **baseline** —
and determines which performs better.

**When to use blind comparison:**
- Before replacing a production prompt with a new version
- When A/B testing two instruction phrasings
- When trying to diagnose whether a regression came from the prompt or the model

**How winner determination works:**
1. Primary: assertion pass rate (higher wins)
2. Secondary (tiebreaker): total multi-dimensional rubric score

The rubric scores four dimensions from 1-5:
- **Instruction adherence**: Overall compliance with stated requirements
- **Output quality**: Quality of text-based criteria (contains/not_contains)
- **Tool usage correctness**: Compliance with tool usage requirements
- **Error handling**: Avoidance of forbidden error-related output

**Running a blind comparison:**

```bash
# system_prompt in config.json = candidate; --baseline = baseline
empirical-test-runner \
  --config test.json \
  --baseline "You are a helpful assistant." \
  --output comparison.json \
  --trials 10
```

Output includes: per-criterion pass rates for each prompt, rubric scores, and the winner with reasoning.

### Post-Hoc Analysis of Failures

When trials fail, the tool produces a post-hoc analysis report that identifies which instructions were
violated and why.

**Interpreting the analysis:**

- **adherenceScore** (1-10): 10 = perfect adherence, 1 = complete non-compliance
- **violations**: List of specific criterion failures, each with:
  - `category`: `instructions`, `tool_usage`, or `error_handling`
  - `expected`: What the agent was supposed to do
  - `actual`: What it did instead
  - `quote`: The relevant excerpt from the output
  - `severity`: HIGH / MEDIUM / LOW
- **suggestions**: Prioritized improvement recommendations sorted by severity

**Using analysis to improve prompts:**

HIGH severity violations are the most impactful to fix first. For each violation:
1. Read the `expected` vs `actual` to understand the gap
2. Look at the `quote` to see the exact failing output
3. Consider whether the instruction is ambiguous or missing from the system prompt
4. Revise the prompt and re-run with `--baseline` to verify improvement

### Tips for Effective Test Design

- **Explain, don't mandate**: Instructions that explain *why* a behavior matters tend to be followed
  more reliably than bare mandates. Test both phrasings.
- **Use tight criteria**: Overly broad must_contain terms (e.g. `"ok"`) may match incidentally. Use
  phrases specific enough that only correct behavior triggers them.
- **Calibrate trial count**: 5 trials is enough for fast iteration; use 20+ for final validation.
- **Test the edge, not just the center**: If you only test the happy path, you will overestimate
  compliance. Include at least one adversarial case per test suite.
- **Record hypotheses**: Write the hypothesis in `target_description` so the intent is clear when
  reviewing results months later.
