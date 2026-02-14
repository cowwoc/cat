<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Phase 1: Investigate

This phase verifies the event sequence and analyzes the documentation path to understand what caused the mistake.

## Your Task

Complete the investigation phase for the learn skill. Your final message must be ONLY the JSON result object with no
surrounding text or explanation. The parent agent parses your response as JSON.

## Step 1: Verify Event Sequence (MANDATORY)

**CRITICAL: Do NOT rely on memory for root cause analysis.**

Verify actual event sequence using get-history:

```bash
/cat:get-history
# Look for: When stated? Action order? User corrections? Actual trigger?
```

**Anti-Pattern:** Root cause analysis based on memory without get-history verification.
Memory is unreliable for causation, timing, attribution.

**If get-history unavailable:** Document analysis based on current context only, may be incomplete.

## Step 1b: Analyze Documentation Path

**CRITICAL: ALWAYS check documentation path FIRST after collecting history.**

**MANDATORY FIRST STEP:** Before any other analysis, identify what documents/skills the agent
read and check if they caused the mistake. Do NOT skip to "agent error" conclusions without first
checking if documentation primed the wrong behavior.

Using the session history from Step 1, identify all documents the agent read.

**NOTE:** `CLAUDE_SESSION_ID` is available in skill preprocessing but NOT exported to bash.
You must substitute the actual session ID value in bash commands, not use the variable reference.

```bash
# Replace with actual session ID - do NOT use ${CLAUDE_SESSION_ID} in bash
SESSION_FILE="/home/node/.config/claude/projects/-workspace/YOUR-SESSION-ID-HERE.jsonl"

# Note: Tool uses are nested inside assistant messages as content blocks
# Structure: {type: "assistant", message: {content: [{type: "tool_use", name: "...", input: {...}}]}}

# Find documents read
echo "=== Documents Read ==="
grep '"type":"assistant"' "$SESSION_FILE" | \
  jq -r '.message.content[]? | select(.type == "tool_use") |
    select(.name == "Read" or .name == "Skill") |
    if .name == "Read" then .input.file_path
    else "skill:" + .input.skill end' 2>/dev/null | sort -u

# Find skill invocations vs expected
echo "=== Skill Invocations ==="
grep '"type":"assistant"' "$SESSION_FILE" | \
  jq -r '.message.content[]? | select(.type == "tool_use" and .name == "Skill") |
    .input.skill + " " + (.input.args // "")' 2>/dev/null

# Find Issue prompts (delegation prompts are documents too!)
echo "=== Issue Delegation Prompts ==="
grep '"type":"assistant"' "$SESSION_FILE" | \
  jq -r '.message.content[]? | select(.type == "tool_use" and .name == "Issue") |
    "Issue: " + .input.description + "\n" + .input.prompt' 2>/dev/null
```

**For each document, check for priming patterns:**

| Pattern | Example | Risk |
|---------|---------|------|
| Algorithm before invocation | "How to compress: 1. Remove redundancy..." | Agent bypasses skill |
| Output format with values | "validation_score: 1.0 (required)" | Agent fabricates output |
| Cost/efficiency language | "This spawns 2 subagents..." | Agent takes shortcuts |
| Conflicting general guidance | "Be concise" + "copy verbatim" | General overrides specific |

**SKILL EXECUTION FAILURES - Use skill-builder:**

When the mistake involves an agent failing to execute a **skill** correctly (wrong output, skipped steps,
manual construction instead of preprocessing), analyze the skill using skill-builder:

```
Read the skill file and apply skill-builder's Priming Prevention Checklist:
- Information Ordering Check (does skill teach HOW before WHAT to invoke?)
- Output Format Check (does output format contain expected values?)
- Cost/Efficiency Language Check (does skill suggest proper approach is "expensive"?)
- Reference Information Check (does skill contain "for reference only" info?)
- No Embedded Box Drawings (does skill show visual examples that prime manual construction?)
```

**Reference:** See `/cat:skill-builder` § "Priming Prevention Checklist" for the complete checklist.
If skill has structural issues, fix the SKILL as part of prevention, not just add behavioral guidance.

**MANDATORY CHECK:** After checking documents read, ALSO ask:
1. **Could agent do the right thing?** Search for documentation of the CORRECT approach
2. **If no documentation exists:** Root cause is `missing_documentation`, not `assumption`
3. **If wrong approach is documented but right approach isn't:** Fix BOTH (remove priming AND add guidance)

**CHECK FOR CONFLICTING GUIDANCE:** Also check if general instructions conflict with specific requirements:
- Does system prompt say "be concise" while skill requires verbatim output?
- Does critical thinking prompt say "analyze" while skill requires copy-paste?
- Does general guidance favor interpretation while skill needs literal execution?
When found: The specific skill requirement should take precedence, but add enforcement (hook) since general
guidance may override documented specific requirements.

**For tool invocation errors:**

When a mistake involves invoking a tool/skill with wrong parameters:
1. Read the tool's actual interface (Parameters section, supported flags)
2. Compare against what was invoked
3. Check what documentation showed similar-looking parameters that may have primed the incorrect usage
4. The cause is often "saw parameter X used somewhere, assumed it applies to tool Y"

**For subagent mistakes, ALSO check the Issue prompt that spawned it:**

The delegation prompt IS the primary "document" the subagent received. Check it for:
- Expected values embedded in output format (e.g., "score: 1.0 (required)")
- Outcome requirements that conflict with reality (e.g., "MUST be 1.0")
- Any content telling the subagent what to report vs what to measure

**CHECK FOR TECHNICALLY IMPOSSIBLE INSTRUCTIONS:**

When a subagent fails to follow instructions, check whether the instructions were **technically possible** given Claude
Code's subagent architecture:

| Subagent Capability | Available? | Evidence |
|---------------------|------------|----------|
| Spawn nested subagents (Task tool) | **NO** | Task tool not exposed to subagents |
| Invoke skills dynamically (Skill tool) | **NO** | Skill tool not available to subagents |
| Read/Write/Edit files | YES | Standard file tools available |
| Run bash commands | YES | Bash tool available |
| Web search/fetch | YES | Available to subagents |

**If instructions required unavailable capabilities:**

```yaml
technically_impossible_check:
  instruction_required: "Invoke /cat:{skill-name} for each item"
  capability_needed: "Skill tool"
  available_to_subagent: false
  conclusion: "IMPOSSIBLE - instruction cannot be executed as written"
  root_cause: "architectural_flaw"
  fix_type: "Redesign workflow to invoke skills at main agent level"
```

**Common patterns of impossible instructions:**

| Instruction Pattern | Why Impossible | Correct Design |
|--------------------|----------------|----------------|
| "Subagent must invoke /cat:skill" | Skill tool unavailable | Main agent invokes skill before/after delegation |
| "Spawn reviewer subagents" | Task tool unavailable | Main agent spawns reviewers directly |
| "Delegate to sub-subagent" | Max depth is 1 | Flatten to single delegation level |
| "Use parallel-execute skill" | Skill tool unavailable | Main agent handles parallelization |

**When this check identifies impossible instructions:**

1. Root cause is `architectural_flaw` (not agent error)
2. Prevention must redesign the WORKFLOW, not add guidance
3. The skill/workflow documentation is the source of the bug
4. Do NOT add "agent should have..." instructions - they cannot help

**CHECK FOR MISSING SKILL PRELOADING:**

When a subagent fails to follow skill-based guidance correctly, check whether the subagent would
have benefited from having skills preloaded via frontmatter.

**Claude Code `skills` frontmatter field:**

Agents defined in `plugin/agents/` can specify skills to preload:

```yaml
---
name: work-merge
description: Merge phase for /cat:work
tools: Read, Bash, Grep, Glob
model: haiku
skills:
  - git-squash
  - git-rebase
  - git-merge-linear
---
```

The `skills` field causes Claude Code to inject the listed skill content into the subagent's
context at startup - the subagent receives the knowledge without needing to invoke the Skill tool.

**Questions to ask when subagent makes a mistake:**

| Question | If YES |
|----------|--------|
| Did subagent need skill knowledge it didn't have? | Consider adding skill to frontmatter |
| Was `general-purpose` subagent used for domain-specific work? | Create dedicated agent type |
| Did subagent try to invoke a skill (and fail)? | Move skill knowledge to frontmatter |
| Would preloaded guidance have prevented the mistake? | Add skill to agent's `skills` field |

**If general-purpose agent was used and skills would help:**

```yaml
subagent_skills_analysis:
  subagent_type_used: "general-purpose"
  domain_knowledge_needed: ["git-squash", "git-rebase"]
  skill_invocation_attempted: true
  skill_invocation_succeeded: false  # Skill tool not available to subagents

  recommendation:
    action: "Create dedicated agent type"
    agent_name: "{domain}-agent"
    skills_to_preload: ["skill-1", "skill-2"]
    rationale: "Subagent needs domain knowledge but cannot invoke skills"
```

**Prevention pattern for skill preloading issues:**

1. Identify the skills the subagent needed
2. Check if a dedicated agent type already exists (check `plugin/agents/`)
3. If yes: Use that agent type instead of `general-purpose`
4. If no: Create new agent in `plugin/agents/{name}.md` with `skills` frontmatter
5. Update the delegation code to use the new agent type

**Record in mistake entry:**

```json
{
  "category": "architectural_flaw",
  "root_cause": "Subagent lacked skill knowledge; general-purpose agent used for domain work",
  "prevention_type": "config",
  "prevention_path": "plugin/agents/{new-agent}.md",
  "subagent_skills_needed": ["skill-1", "skill-2"]
}
```

**CRITICAL: Trace the FULL priming chain:**

When the main agent wrote a bad delegation prompt, ask: **What primed the MAIN AGENT to write that prompt?**

Common priming sources for main agent decisions:
1. **Previous subagent failure messages** - "excessive nesting" or "token budget" may prime bypasses
2. **Error messages from tools** - May suggest workarounds that violate protocols
3. **Cost/efficiency concerns in skill docs** - "This spawns N subagents" primes shortcuts

**Trace the chain backwards:**

```
Main agent wrote bad prompt
  ↑ WHY?
Previous subagent returned FAILED with message
  ↑ WHY did that message prime a bad decision?
Message described problem without actionable guidance
  ↑ FIX: Improve failure message guidance, not just main agent behavior
```

**Search session history for failure messages:**

```bash
# Find subagent failure messages that preceded the bad decision
grep '"type":"tool_result"' "$SESSION_FILE" | \
  jq -r 'select(.content | type == "array") | .content[]? |
    select(.text? | contains("FAILED") or contains("excessive") or contains("nesting"))' 2>/dev/null

# Find Task tool results with failure status
grep -B5 "do NOT invoke" "$SESSION_FILE" | head -50  # Find context before bypass instruction
```

**If a subagent failure message primed the main agent:**

The fix must address BOTH:
1. The main agent's behavior (don't bypass skills)
2. The failure message's guidance (provide actionable alternatives, not just problem description)

**If priming found:**

```yaml
documentation_priming:
  document: "{path to document OR 'Issue prompt'}"
  misleading_section: "{section name and line numbers OR 'OUTPUT FORMAT section'}"
  priming_type: "algorithm_exposure | output_format | cost_concern | internal_prompt | expected_value"
  how_it_misled: "Agent learned X, then applied it directly instead of invoking Y"
  fix_required: "Move content to internal-only document / Remove section / Restructure"
```

**Reference:** See [documentation-priming.md](documentation-priming.md) for detailed analysis patterns.

## Output Format

Your final message MUST be ONLY this JSON (no other text):

```json
{
  "phase": "investigate",
  "status": "COMPLETE",
  "user_summary": "1-3 sentence summary of what this phase found (for display to user between phases)",
  "event_sequence": {
    "timeline": ["Event 1", "Event 2", ...],
    "mistake_trigger": "What actually triggered the mistake"
  },
  "documents_read": [
    {"path": "/path/to/file", "type": "skill|doc|prompt"}
  ],
  "priming_analysis": {
    "priming_found": true,
    "document": "path or description",
    "priming_type": "algorithm_exposure|output_format|cost_concern|conflicting_guidance|impossible_instruction|missing_skill_preload",
    "how_it_misled": "explanation"
  },
  "session_id": "actual-session-id"
}
```
