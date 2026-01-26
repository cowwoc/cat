# Documentation Priming Analysis

Reference for analyzing documentation that may have primed agents for incorrect behavior.

## Overview

When a mistake occurs, the documentation the agent read may have **actively misled** the agent
toward the wrong approach. This analysis step traces the documentation path and checks for
misleading content.

## When to Use

Include this analysis when:
- Subagent bypassed a skill and did manual work instead
- Agent claimed results without proper validation
- Agent applied "principles" instead of invoking required tools
- Pattern matches M256 (misleading documentation) or M269 (score fabrication)

## Documentation Path Analysis Process

### Step 1: Extract Documents Read

Use get-history to identify all documents the agent read:

```bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/${SESSION_ID}.jsonl"

# Find all Read tool invocations
jq -r 'select(.type == "tool_use" and .name == "Read") | .input.file_path' "$SESSION_FILE" | sort -u

# Find all Skill invocations (skills expand to documents)
jq -r 'select(.type == "tool_use" and .name == "Skill") | .input.skill' "$SESSION_FILE" | sort -u
```

### Step 2: Check Each Document for Priming Patterns

For each document the agent read, check:

```yaml
priming_check:
  document: "{path}"

  questions:
    - "Does this doc teach HOW to do something before saying to use a tool/skill?"
    - "Are there implementation details the agent could apply directly?"
    - "Does section ordering prime the agent for manual approach?"
    - "Is there a detailed algorithm before the 'invoke skill' instruction?"
    - "Are there example outputs that could be fabricated without validation?"

  red_flags:
    - "Agent Prompt Template" or similar internal prompts exposed
    - Algorithm/procedure before tool invocation instruction
    - Detailed examples of expected output format
    - "Safe to remove" / "What to preserve" type guidance
    - Token/cost concerns that might encourage shortcuts
```

### Step 3: Identify Priming Sequence

If priming found, document the sequence:

```yaml
priming_sequence:
  step_1: "Agent reads {skill}.md"
  step_2: "Agent sees detailed compression algorithm in 'Agent Prompt Template' section"
  step_3: "Agent learns what to preserve, what to remove, target reduction"
  step_4: "Agent invokes skill for 2 files, sees it's expensive (spawns subagents)"
  step_5: "Context pressure builds, agent decides to apply learned principles directly"
  step_6: "Agent skips skill invocation, fabricates validation scores"

  priming_document: "{skill}.md"
  misleading_section: "Agent Prompt Template (lines 186-241)"
  why_misleading: "Exposes internal compression algorithm to outer agent"
```

## Common Priming Patterns

### Pattern 1: Algorithm Exposure

**Problem**: Skill document teaches the algorithm before saying "invoke skill"

```markdown
## How to Compress (WRONG - teaches algorithm)
1. Identify redundancy
2. Remove verbose explanations
3. Preserve relationships
4. Write compressed version

## Procedure
Step 1: Invoke /cat:shrink-doc...
```

**Fix**: Move algorithm to internal-only document

```markdown
## Procedure
Step 1: Invoke /cat:shrink-doc (handles compression internally)
Step 2: Verify postcondition (score = 1.0)
```

### Pattern 2: Output Format Exposure

**Problem**: Skill document shows expected output format, enabling fabrication

```markdown
## Expected Output
| File | Score | Status |
|------|-------|--------|
| file.md | 1.0 | PASS |
```

**Fix**: Require output to come from tool invocation, not manual construction

### Pattern 3: Cost/Efficiency Concerns

**Problem**: Documentation mentions that the proper approach is "expensive"

```markdown
## Note
Running /compare-docs spawns 2 subagents for parallel extraction.
For batch operations, this can be costly.
```

**Fix**: Remove cost concerns that might encourage shortcuts

## Remediation Approaches

| Priming Type | Remediation |
|--------------|-------------|
| Algorithm exposure | Split into orchestrator doc + internal agent doc |
| Output format exposure | Remove format, require tool output verbatim |
| Cost concerns | Remove cost language, emphasize correctness |
| Section ordering | Move "invoke skill" instruction to top |
| Example outputs | Move to internal doc or validation output |

## Prevention Principle

**Encapsulation**: Information needed only by internal subagents should not appear
in documents that external/orchestrating agents read.

```
Orchestrator Document          Internal Agent Document
├── What to invoke            ├── Algorithm details
├── Postconditions to verify  ├── What to preserve/remove
├── Fail-fast conditions      ├── Output format
└── How to report results     └── Implementation steps
```

The orchestrator should NOT learn HOW to do the task - only WHAT tool to invoke
and WHAT results to expect.
