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

This skill implements a 6-step empirical testing methodology:

1. **Define** the compliance failure (what should happen vs what does happen)
2. **Hypothesize** which elements cause the failure
3. **Isolate** variables with controlled test configurations (change one thing at a time)
4. **Measure** success rates across N trials per configuration
5. **Fix** by testing candidate solutions with the same methodology
6. **Report** findings with data tables

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
    "priming_messages": [
        "Run `git branch --show-current`",
        "Run `git log --oneline -3`"
    ],
    "configs": {
        "A_baseline": "[The exact prompt/skill content that fails]"
    }
}
```

**Priming messages** simulate prior conversation context. Include tool-use turns if the failure happens after tool use.
Omit them to test without prior context.

**Success criteria** define what constitutes a passing trial:
- `must_contain`: Strings that must appear in agent output (case-insensitive)
- `must_not_contain`: Strings that indicate failure
- `must_use_tools`: Tools the agent must invoke
- `must_not_use_tools`: Tools the agent must not invoke

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

## Step 4: Isolate Variables

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

## Step 5: Analyze Results

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

## Step 6: Test the Fix

Once the root cause is identified, create the candidate fix and test it in production context:

1. Apply the fix to the actual skill/prompt file
2. Create a new config that uses the **actual file content** (not a simplified version)
3. Include **all production elements** (priming, prefix, full content)
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

## Step 7: Report Findings

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
- **Test production content** — simplified test prompts may pass when production content fails (found in M507: isolated
  test 100% vs production 33%).
- **Description lines hurt** — meta-descriptions like "Display current status" cause haiku to treat them as tasks.
- **Ordering matters** — put the most important instruction first (primacy effect).
- **Explain WHY** — Anthropic docs: "Add context/motivation to improve performance."

## Related Skills

- `/cat:learn` - Record the mistake and root cause analysis
- `/cat:skill-builder` - Create or update skills with compliance-tested patterns
