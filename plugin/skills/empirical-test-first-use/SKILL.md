---
description: "Internal skill for subagent preloading. Do not invoke directly."
user-invocable: false
---

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
| Empty | `/cat:empirical-test` | Interactive: gather failure description |
| Description | `/cat:empirical-test "haiku doesn't echo status box"` | Start with provided description |

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
    "success_criteria": {
        "must_contain": ["text the output must contain"],
        "must_not_contain": ["text that indicates failure"],
        "must_use_tools": [],
        "must_not_use_tools": []
    },
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
        "A_baseline": "[The exact prompt/skill content that fails]"
    }
}
```

**Priming messages** simulate prior conversation context. Each message can be:
- **String** - Sent as a user message (backward compatible)
- **Object with type "tool_use"** - Generates assistant tool_use + user tool_result messages

Use tool_use priming to simulate scenarios where the failure occurs after tool execution (e.g., after running `git
branch` or `/cat:status`). This is critical for reproducing failures that only occur in post-tool-use context.

**System prompt** is an optional string passed as `--append-system-prompt` to the claude CLI, simulating project
instructions like CLAUDE.md content that would be present in a real session.

**MANDATORY for verbatim-output skills:** When testing a skill that requires the agent to echo, copy, or reproduce
content exactly (e.g., `/cat:status`, `/cat:get-history`), always populate `system_prompt` with the project's CLAUDE.md
content. General project instructions like "be helpful" or "be concise" actively compete with verbatim-copy
instructions, and tests without this context will report artificially high success rates that do not reflect real
sessions. Omitting the system prompt is the primary cause of false positives in empirical tests.

```bash
# Read CLAUDE.md for use as system_prompt in verbatim-output skill tests
CLAUDE_MD_CONTENT=$(cat "${CLAUDE_PROJECT_DIR}/CLAUDE.md")
# Include as the "system_prompt" field in the test config JSON
```

**System reminders** are optional strings injected into the test prompt user message, each wrapped in
`<system-reminder>` tags. This simulates hook-injected system reminders that appear in production sessions and can
affect agent behavior.

**Success criteria** define what constitutes a passing trial:
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
  CLIENT_BIN="/workspace/client/target/jlink/bin"
fi

"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-config.json \
    --trials 5 \
    --model haiku
```

**Interpreting results:**

| Baseline Rate | Interpretation | Next Step |
|---------------|---------------|-----------|
| 0-20% | Failure confirmed | Proceed to isolation |
| 30-70% | Intermittent failure | Increase trials to 10, proceed to isolation |
| 80-100% | Cannot reproduce | Verify priming context matches real usage |

If the baseline shows 80%+ success, the failure may depend on specific conversation context. Add more priming messages
or adjust the test prompt to match the real failure scenario.

## Step 4: Examine Full Agent Responses

When baseline confirms failure (0-70% success rate), run with `--output` to capture full agent responses before
proceeding to isolation. The summary output shows only a short preview — full responses reveal *why* the agent failed.

```bash
"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-config.json \
    --trials 5 \
    --model haiku \
    --output /tmp/empirical-test-baseline.json
```

Open `/tmp/empirical-test-baseline.json` and examine the `results` field. Each trial contains:
- `outputPreview`: a 300-character preview of the agent's response
- `toolsUsed`: which tools the agent called
- `checks`: which success criteria passed or failed

**Multi-message evaluation warning:** When `priming_messages` exist, the agent processes each message and produces a
response. Failures in priming responses (e.g., a tool_use turn) do not indicate the test prompt failed — they may be
expected intermediate steps. Check which specific response triggered the `success_criteria` evaluation. Only the final
response to the test prompt is evaluated against the criteria.

Use the full output to form a hypothesis about *what* the agent produced (hallucinating content, refusing to act, using
wrong format) before attempting isolation. Isolation experiments are more targeted when the failure mode is understood.

## Step 5: Isolate Variables

Generate test configurations that vary one element at a time from the failing baseline. Each hypothesis becomes a config.

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
        "A_original": "...",
        "B_best_single_fix": "...",
        "C_combined_top2": "...",
        "D_combined_top3": "..."
    }
}
```

## Step 7: Test the Fix

Once the root cause is identified, create the candidate fix and test it in production context:

1. Apply the fix to the actual skill/prompt file
2. Create a new config that uses the **actual file content** (not a simplified version)
3. Include **all production elements** (priming, prefix, full content, and system prompt from CLAUDE.md)
4. Run with higher trial count (10-15) for statistical confidence

```bash
"$CLIENT_BIN/empirical-test-runner" \
    --config /tmp/empirical-test-final.json \
    --trials 15 \
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
| A_baseline (original) | 20% | Reproduces failure |
| B_without_X | 100% | Removing X fixes it |
| C_with_fix | 93% | Production validation |

**Mechanism:** [explanation of why the root cause affects agent behavior]
```

## Tips

- **Start with haiku** — it's cheaper and more sensitive to prompt issues. If haiku passes, sonnet will too.
- **Use 5 trials** for initial exploration, 10-15 for final validation.
- **Priming matters** — always include tool-use priming if the real scenario has it.
- **Include system prompt for verbatim-output tests** — tests of skills that echo content verbatim MUST include
  CLAUDE.md content as the `system_prompt`. General project instructions compete with verbatim-copy instructions and
  cause false positives when omitted (found in M361: test showed 100% but production failed).
- **Test production content** — simplified test prompts may pass when production content fails (found in M507: isolated
  test 100% vs production 33%).
- **Description lines hurt** — meta-descriptions like "Display current status" cause haiku to treat them as tasks.
- **Ordering matters** — put the most important instruction first (primacy effect).
- **Explain WHY** — Anthropic docs: "Add context/motivation to improve performance."

## Related Skills

- `/cat:learn` - Record the mistake and root cause analysis
- `/cat:skill-builder` - Create or update skills with compliance-tested patterns
