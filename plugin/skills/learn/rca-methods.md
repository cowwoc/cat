<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Root Cause Analysis Methods

Reference document for RCA methods used in `/cat:learn`.

## Method Assignment

Use mistake ID modulo 3:
- IDs where `N mod 3 = 0` → Method A (5-Whys)
- IDs where `N mod 3 = 1` → Method B (Taxonomy)
- IDs where `N mod 3 = 2` → Method C (Causal Barrier)

## Method A: 5-Whys (Control)

Ask "why" iteratively until reaching fundamental cause (typically 5 levels):

```yaml
five_whys:
  - why: "Why did this happen?"
    answer: "Immediate cause of the mistake"
  - why: "Why [previous answer]?"
    answer: "Deeper contributing factor"
  - why: "Why [previous answer]?"
    answer: "Organizational or process factor"
  - why: "Why [previous answer]?"
    answer: "Systemic or environmental factor"
  - why: "Why [previous answer]?"
    answer: "Root cause - fundamental issue"

root_cause: "The fundamental issue identified at deepest 'why'"
category: "Select from category reference"
rca_method: "A"
```

**Check against common patterns:**
- Assumption without verification?
- Completion bias (rationalized ignoring rules)?
- Memory reliance (didn't re-verify)?
- Environment state mismatch?
- Documentation ignored (rule existed)?

## Method B: Modular Error Taxonomy

Based on [AgentErrorTaxonomy](https://arxiv.org/abs/2509.25370).

```yaml
taxonomy_analysis:
  # Step 1: Classify into module
  module: MEMORY | PLANNING | ACTION | REFLECTION | SYSTEM
  module_definitions:
    MEMORY: "Failed to retain/recall earlier context"
    PLANNING: "Poor issue decomposition or sequencing"
    ACTION: "Incorrect tool use or execution"
    REFLECTION: "Failed to detect/correct own error"
    SYSTEM: "Environment, tooling, or integration failure"

  # Step 2: Identify failure mode within module
  failure_mode: "What specific capability failed?"
  failure_type: FALSE_POSITIVE | FALSE_NEGATIVE

  # Step 3: Check for cascading
  cascading:
    caused_downstream: true | false
    is_symptom_of: null | "earlier failure description"

  # Step 4: Corrective feedback
  corrective_feedback: "What specific guidance would have prevented this?"
  intervention_point: "At what step should intervention have occurred?"

root_cause: "..."
category: "..."
rca_method: "B"
```

## Method C: Causal Barrier Analysis

Based on [causal reasoning research](https://www.infoq.com/articles/causal-reasoning-observability/).

```yaml
causal_barrier_analysis:
  # Step 1: List ALL candidate causes
  candidates:
    - cause: "Knowledge gap - didn't know correct approach"
      expected_symptoms: ["asked questions", "explored alternatives"]
      observed: false
      likelihood: LOW
    - cause: "Compliance failure - knew rule, didn't follow"
      expected_symptoms: ["rule exists in docs", "no confusion expressed"]
      observed: true
      likelihood: HIGH
    - cause: "Tool limitation - tool couldn't do what was needed"
      expected_symptoms: ["error messages", "tried alternatives"]
      observed: false
      likelihood: LOW

  # Step 2: Select most likely cause
  selected_cause: "Compliance failure"
  confidence: HIGH | MEDIUM | LOW
  evidence: "Rule documented in X, no exploration attempts observed"

  # Step 3: Verify cause vs symptom
  verification:
    question: "If we fixed this, would the problem definitely not recur?"
    answer: "Yes, if enforcement hook blocks the incorrect behavior"
    is_root_cause: true

  # Step 4: Barrier analysis
  barriers:
    - barrier: "Documentation in CLAUDE.md"
      existed: true
      why_failed: "Agent did not read/follow it"
    - barrier: "PreToolUse hook"
      existed: false
      should_exist: true
      strength_if_added: "Would block incorrect behavior"

  minimum_effective_barrier: "hook (level 2)"

root_cause: "..."
category: "..."
rca_method: "C"
```

## Prevention Strength Gate

This gate runs **after completing any RCA method** (A, B, or C) and **before entering `phase-prevent.md`**.

### Trigger Condition

The gate activates **only when `recurrence_of` is non-null** — meaning a prior rule or prevention already existed
for this mistake. First-time occurrences are fully exempt from this gate.

### Step 1 — Classify the Recurrence Cause

Before the gate applies, classify WHY the prior prevention failed. The four valid cause types are:

| Cause Type | Description |
|---|---|
| `unenforced` | Prior rule/doc was correct but agent did not follow it |
| `biased_rca` | Prior RCA was compromised (priming doc, wrong methodology, or misidentified root cause) |
| `too_weak` | Prior prevention was correctly chosen but insufficient for the violation level |
| `pending_unloaded` | Prior prevention was applied to source but not yet loaded into the running plugin cache |

**If the cause type cannot be determined:** halt and require explicit classification before continuing.
Do NOT default to any tier or assume a cause type. Record your uncertainty and ask for input.

### Step 2 — Apply the Cause-Aware Decision Tree

**Case 1 — `unenforced`:** The prior rule or document was correct, but the agent did not follow it.

- Required action: escalate to hook-level enforcement (level 2 or stronger).
- Documentation-only prevention is **blocked** for this cause type.
- Rationale: if the agent ignored a documented rule once, documenting it again will not prevent recurrence.

**Case 2 — `biased_rca`:** The prior RCA was compromised by a priming document, incorrect methodology, or
a misidentified root cause that directed prevention at the wrong target.

- Required action: fix the analysis pipeline — correct the priming source, revise the RCA methodology, or
  rerun the RCA against the correct evidence.
- Fixing the analysis pipeline counts as a valid high-strength prevention even when the target is a
  documentation file, because the failure is in the analysis process itself, not in enforcement level.

**Case 3 — `too_weak`:** The prior prevention was implemented but was categorically weaker than needed
(e.g., documentation when a hook was required, a hook when a code fix was required).

- Required action: escalate prevention level (e.g., documentation → hook, hook → code_fix).
- The new prevention must be at least one level stronger than the prior prevention.

**Case 4 — `pending_unloaded`:** The prior prevention was implemented in a previous session and exists on disk,
but the running plugin instance has not yet loaded it (plugin not reinstalled or reloaded). The recurrence
occurred because the prevention was never active, not because it was insufficient.

- Required action: no new prevention required. The existing prevention is correct but not yet active.
- Mark prevention as pending and defer evaluation until after the next plugin cache refresh.
- If the recurrence persists after the cache is refreshed, reclassify the cause as one of the other three types
  (`unenforced`, `biased_rca`, or `too_weak`).

### Step 3 — Verify File Modification Before Marking Prevention Applied

Prevention is **not** considered applied in the current session unless at least one file was actually
modified via an Edit or Write tool call.

**Exception — `pending_unloaded` cause type:** When the cause is classified as `pending_unloaded` in Step 1,
the file-modification requirement is automatically waived. The decision tree in Step 2 (Case 4) already handles
this case — no additional exception logic is needed here.

### Gate Summary Table

| Condition | Required Action |
|---|---|
| First-time occurrence | Exempt — gate does not activate |
| Cause type unknown | Halt — require explicit classification |
| `unenforced` | Escalate to hook-level or stronger; docs blocked |
| `biased_rca` | Fix analysis pipeline; docs allowed if pipeline is the target |
| `too_weak` | Escalate at least one prevention level |
| `pending_unloaded` | No new prevention; defer to next cache refresh |
| No file modified in session | Block marking prevention as applied |

## Cause Signature Vocabulary

A `cause_signature` is a structured triple `<cause_type>:<barrier_type>:<context>` that identifies the root cause
pattern independently of wording variations. Use this field to link mistakes that share the same underlying failure
even when they manifest in different tools or sessions.

### Format

```
cause_signature: "<cause_type>:<barrier_type>:<context>"
```

### Controlled Vocabulary

**Cause Types** — why the agent took the wrong action:

| Value | Meaning |
|---|---|
| `compliance_failure` | Agent knew the rule but did not follow it |
| `knowledge_gap` | Agent lacked information needed to act correctly |
| `tool_limitation` | Tool could not perform the needed operation |
| `environment_mismatch` | Runtime state (cwd, branch, config) did not match expectation |
| `context_degradation` | Long session caused agent to lose track of earlier constraints |
| `architectural_conflict` | Task requires the agent to fight its own training |

**Barrier Types** — what prevention mechanism was missing or failed:

| Value | Meaning |
|---|---|
| `hook_absent` | No PreToolUse/PostToolUse hook existed to block the wrong action |
| `hook_bypassed` | Hook existed but was circumvented (e.g., --no-verify, wrong condition) |
| `doc_missing` | No documentation described the correct behavior |
| `doc_ignored` | Documentation existed but agent did not follow it |
| `validation_absent` | No automated check caught the bad state |
| `config_wrong` | Configuration value was incorrect or absent |
| `skill_incomplete` | Skill instructions did not cover this scenario |
| `process_gap` | Workflow steps did not enforce the required ordering |

**Context** — which subsystem or phase produced the failure:

| Value | Meaning |
|---|---|
| `pre_tool_use` | Failure occurred in a PreToolUse hook or before tool execution |
| `post_tool_use` | Failure occurred in a PostToolUse hook or after tool execution |
| `plugin_rules` | Failure related to plugin behavioral rules (CLAUDE.md, rules/) |
| `skill_execution` | Failure occurred during skill step execution |
| `git_operations` | Failure in git commands (commit, rebase, push, etc.) |
| `file_operations` | Failure in file read/write/edit operations |
| `subagent_delegation` | Failure in spawning or interpreting subagent results |
| `issue_workflow` | Failure in issue lifecycle (lock, worktree, merge) |
| `rca_process` | Failure in the learn/RCA workflow itself |

### Canonical Examples

| Signature | When to Use |
|---|---|
| `compliance_failure:hook_absent:pre_tool_use` | Agent violated a rule; no hook existed to block the attempt |
| `compliance_failure:doc_ignored:plugin_rules` | Rule existed in CLAUDE.md; agent did not follow it |
| `knowledge_gap:doc_missing:skill_execution` | Skill instructions did not cover the needed scenario |
| `knowledge_gap:skill_incomplete:subagent_delegation` | Delegation prompt omitted information the subagent needed |
| `context_degradation:process_gap:issue_workflow` | Late-session context loss caused workflow step to be skipped |
| `architectural_conflict:hook_absent:plugin_rules` | LLM training conflicts with required verbatim/mechanical output |
| `environment_mismatch:validation_absent:git_operations` | Git operation ran in wrong worktree; no path check existed |
| `compliance_failure:hook_bypassed:git_operations` | Agent used --no-verify or equivalent to skip enforcement |

### Selection Process

After completing RCA (any method), select `cause_signature` by answering in sequence:

1. **Cause type:** Why did the agent take the wrong action? (pick from cause types above)
2. **Barrier type:** What was the weakest or missing prevention mechanism? (pick from barrier types above)
3. **Context:** In which subsystem did the failure occur? (pick from context values above)

If no single value fits perfectly, choose the closest match and note the discrepancy in `root_cause`.

**Validation responsibility:** Format validation (triple structure, controlled vocabulary values)
is enforced at the skill level (phase-analyze.md Step 3a). The Java persistence layer
(RecordLearning) stores the value as-is without re-validating format.

### Recurrence Detection

Before recording a new mistake, compare its candidate `cause_signature` against existing entries in
`mistakes-YYYY-MM.json`. If any existing entry shares the same signature:

1. This is likely a recurrence of the same root cause.
2. Set `recurrence_of` to the ID of the earliest matching entry (or the most recent if chain is long).
3. Apply the Prevention Strength Gate (see above) — the prior prevention failed.

## Recording Format

Include method and signature in JSON entry:
```json
{
  "rca_method": "A|B|C",
  "rca_method_name": "5-whys|taxonomy|causal-barrier",
  "cause_signature": "<cause_type>:<barrier_type>:<context>"
}
```

The `cause_signature` field is optional. Existing entries without it are treated as unclassified and excluded from
signature-based recurrence detection.
