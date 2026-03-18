<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Root Cause Analysis Method

Reference document for the RCA method used in `/cat:learn`.

## Causal Barrier Analysis

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
```

## Prevention Strength Gate

This gate runs **after completing the RCA method** and **before entering `phase-prevent.md`**.

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

**If the cause type cannot be determined:** record specific evidence for each candidate cause type and explain
precisely why none can be conclusively selected. Then:

1. Enumerate which of the four cause types is **most consistent** with the available evidence. Select that type
   and proceed — do NOT halt indefinitely on uncertainty alone.
2. The halt-and-request-user-classification branch may only be invoked when **at least two** cause types are
   each supported by a distinct observable artifact (not inference) AND those artifacts are genuinely
   indistinguishable in what they imply. If no cause type is supported by an observable artifact — i.e., all
   four are inference-only — proceed with the most-consistent type; halting is **not permitted** in that case.
   The halt branch exists for genuine artifact conflicts, not for absence of evidence.

**Invalidated cause types are excluded from halt-branch counting.** A cause type that has been invalidated
under any rule in this gate (e.g., `biased_rca` invalidated by the independent verifiability requirement,
`pending_unloaded` invalidated by a prior classification) loses artifact-supported status for all purposes,
including halt-branch candidate counting. An invalidated cause type may not be counted as one of the "at least
two" artifact-backed candidates required to trigger the halt branch. If invalidation reduces the artifact-backed
candidate count below two, the halt branch is unavailable; proceed with the most-consistent non-invalidated type.

Do NOT assert uncertainty without recording specific evidence. Vague or unsupported uncertainty claims must
proceed with the most-consistent cause type rather than halting.

**Evidence citation requirement:** "Specific evidence" means observable artifacts only — tool call logs, error
messages, conversation excerpts, file contents read via a Read tool call, or other verifiable outputs from the
current or prior session. Inferences restating the mistake description do not count as evidence. For each
candidate cause type, cite the artifact (e.g., "Read tool output at line 42 shows field X was present") or
explicitly note that no artifact was found. A cause type supported only by reasoning from the mistake description
itself must be labeled "inference only" and ranked below any cause type supported by an observable artifact.

**Artifact relevance requirement:** An observable artifact qualifies as supporting evidence for a cause type
only if it contains information that specifically distinguishes that cause type from the alternatives — i.e.,
information about how the prior prevention failed in a way consistent with that cause type. An artifact that
merely confirms the mistake occurred, records its description, or lists prior classifications does not
distinguish among cause types and does not elevate any candidate above "inference only." For example, a Read
tool call on the mistakes file that returns the mistake's description and classification history qualifies as
evidence only for `pending_unloaded` (prior classification check) — it does not constitute distinguishing
evidence for `unenforced`, `biased_rca`, or `too_weak`.

**Artifact indistinguishability requirement (halt branch):** Two artifacts are "genuinely indistinguishable in
what they imply" only when a neutral third party reading both artifacts verbatim — without any additional
reasoning or inference — could not determine which cause type each supports. The agent must quote both artifacts
in full and state, for each, the specific text that creates the ambiguity. If the ambiguity requires the agent
to reason beyond the literal artifact text, the artifacts are distinguishable through inference and the halt
branch is **not** available; proceed with the most-consistent cause type.

**Most-consistent-type selection when all evidence is inference-only:** When no observable artifact supports any
cause type (all four are inference-only), the most-consistent type is determined by this priority order:
`unenforced` > `too_weak` > `biased_rca` > `pending_unloaded`. Select the highest-priority type whose
description is consistent with the known facts. This priority order reflects the most conservative enforcement
default: `unenforced` requires hook-level prevention, which is the strongest default; `pending_unloaded` is
last because it defers enforcement entirely, requiring the most evidence to justify. The agent must state which
priority rank was used and why.

### Step 2 — Apply the Cause-Aware Decision Tree

**Step 2 classification is FINAL only when explicitly confirmed.** A classification is "confirmed final" only
when the agent has completed all validity checks for the selected cause type AND explicitly written the phrase
"Classification confirmed final: [cause type]" in its output. No file modification may begin before this
explicit confirmation phrase appears. Any file modification made before this phrase appears is **void** for
Step 3 purposes, regardless of any other statement in the agent's output. If a cause type is later found
invalid and a mandatory reclassification occurs, any file modifications made under the prior invalid
classification remain void.

**Current-session tool call results do not qualify as pre-existing artifacts for `biased_rca`.**
A tool call made during the current session — including Bash commands, WebFetch calls, or any other tool
invocation — produces current-session output. Such output does not qualify as an independent artifact for the
`biased_rca` independent verifiability requirement. Only artifacts that existed before the current session
began, or were produced by an external system prior to any agent action in this session, qualify.

**Mandatory reclassification is final and non-retriggering.** When a cause type is found invalid and a
mandatory reclassification occurs (e.g., invalid `biased_rca` → `unenforced`), the reclassified type is the
operating classification for Steps 2 and 3 with no further Step 1 analysis permitted. The agent may not re-run
Step 1 after a mandatory reclassification, and may not recharacterize a second classification analysis as
anything other than a Step 1 re-run. The reclassified type is binding.

**Halt branch is evaluated at the time of the halt decision and is subject to retroactive invalidation.**
If a halt is invoked based on two artifact-backed candidates and one of those candidates is subsequently found
invalid (e.g., `biased_rca` invalidated by the independent verifiability requirement), the halt is void.
The agent must re-evaluate the halt condition with the invalidated type excluded. If the remaining non-invalidated
artifact-backed candidates number fewer than two, the halt branch is unavailable; proceed with the most-consistent
non-invalidated type.

**Case 1 — `unenforced`:** The prior rule or document was correct, but the agent did not follow it.

- Required action: escalate to hook-level enforcement (level 2 or stronger).
- Documentation-only prevention is **blocked** for this cause type.
- Rationale: if the agent ignored a documented rule once, documenting it again will not prevent recurrence.

**Case 2 — `biased_rca`:** The prior RCA was compromised by a priming document, incorrect methodology, or
a misidentified root cause that directed prevention at the wrong target.

- Required action: fix the analysis pipeline — correct the priming source, revise the RCA methodology, or
  rerun the RCA against the correct evidence.
- "Analysis pipeline" is defined as: the structured RCA process itself (the method steps, methodology files,
  or priming documents that shaped the prior RCA), **not** the documentation file that recorded the prior
  conclusion. A pipeline fix must target one of: (a) a priming document that introduced incorrect framing,
  (b) the RCA methodology steps that led to the wrong root cause, or (c) re-execution of the RCA with
  corrected inputs. Editing only the prior mistake's learning record entry, plan.md, or any other
  conclusion-recording document does **not** constitute fixing the analysis pipeline.
- The `biased_rca` cause type is **invalid** if the prior RCA correctly identified the root cause and the
  agent simply failed to act on it. That is `unenforced`. `biased_rca` applies only when the pipeline
  itself produced a wrong diagnosis.
- **Mandatory prior-RCA citation:** Before classifying a recurrence as `biased_rca`, the agent **must** read
  the prior RCA entry from the mistakes file using a Read tool call and quote the prior RCA's stated root cause
  conclusion verbatim in the current output. The agent must then identify specifically which element of that
  quoted conclusion is wrong and what correct conclusion the evidence supports. An assertion that the prior RCA
  was biased without quoting its conclusion is **invalid** and must be reclassified as `unenforced`.
- **`biased_rca` independent verifiability requirement:** The claim that the prior RCA produced a wrong
  diagnosis must be supported by an observable artifact — not by the agent's re-interpretation of the same
  facts the prior RCA used. Specifically: the agent must cite at least one artifact (tool call output, file
  content, error message, or conversation excerpt) that was either (a) unavailable to the prior RCA session,
  or (b) present but demonstrably misread or ignored by the prior RCA, as evidenced by the artifact content
  itself. If no such artifact exists — i.e., the agent's sole basis for disputing the prior diagnosis is a
  different inference from the same evidence — the diagnosis dispute is unverifiable and the classification
  **must** be `unenforced`, not `biased_rca`. The bar is: a neutral reviewer reading the cited artifact
  should be able to confirm, from the artifact text alone, that the prior conclusion was incorrect.
  **The artifact must be pre-existing — it must have existed before the current session began. Current-session
  tool call results (Bash output, WebFetch output, any tool invocation made during this session) do not qualify
  as independent artifacts, regardless of whether the prior RCA session had access to them or not.**
- **`biased_rca` pipeline-section specificity:** A modification to a pipeline artifact file satisfies the
  Step 3 requirement only if the modified section is the methodology or priming instruction content — not any
  conclusion-summary, historical-record, or prior-output section of the file. A file that contains both
  methodology instructions and conclusion summaries is a pipeline artifact only with respect to its methodology
  sections. Editing a conclusion-summary section of an otherwise-legitimate pipeline artifact does **not**
  satisfy the `biased_rca` Step 3 requirement.
- Fixing the analysis pipeline counts as a valid high-strength prevention even when the target is a
  methodology or priming document, because the failure is in the analysis process itself, not in enforcement
  level. However, the modified section must be the pipeline-instruction content (methodology, priming
  instructions), not a conclusions-recording section of that file.

**Case 3 — `too_weak`:** The prior prevention was implemented but was categorically weaker than needed
(e.g., documentation when a hook was required, a hook when a code fix was required).

- Required action: escalate prevention level (e.g., documentation → hook, hook → code_fix).
- The new prevention must be at least one level stronger than the prior prevention.
- **Documentation is the minimum valid prevention level.** If the prior prevention was at the documentation
  level (i.e., a written rule, convention file entry, or other documentation artifact), `too_weak` escalation
  **must** produce a hook-level or stronger prevention — documentation-only output is blocked. A verbal note,
  informal memory entry, or undocumented reminder does not constitute a prior "documentation-level" prevention
  and does not entitle the agent to escalate to documentation; such sub-documentation priors must escalate
  directly to hook-level or stronger.
- **Hook handler modification requirement:** When the required escalation produces a hook, the hook handler
  file must be modified in the current session via Edit or Write tool call, and the modification itself (not
  merely pre-existing handler logic) must implement the recurrence-addressing control. Adding a comment,
  whitespace, or unrelated change to an existing handler does not satisfy this requirement. The modification
  must add or change logic that specifically prevents the classified recurrence.

**Case 4 — `pending_unloaded`:** The prior prevention was implemented in a previous session and exists on disk,
but the running plugin instance has not yet loaded it (plugin not reinstalled or reloaded). The recurrence
occurred because the prevention was never active, not because it was insufficient.

- Required action: no new prevention required. The existing prevention is correct but not yet active.
- **`pending_unloaded` may only be selected once per mistake, globally across all sessions.** Before selecting
  this cause type, you **must** read the mistakes JSON file using a Read tool call and quote the
  **classification history field** of the relevant mistake entry in your output. "Classification history field"
  means the field or array that records prior cause-type selections for this mistake — not the description
  field, title field, or any other field. If the mistakes file has no dedicated classification history field,
  the schema cannot confirm absence of a prior `pending_unloaded` classification; treat the check as failed
  and reclassify as `unenforced`, `biased_rca`, or `too_weak` — **do not select `pending_unloaded`** when the
  schema provides no classification history field. A verbal assertion that no prior `pending_unloaded`
  classification exists is **not** sufficient — the Read tool call and quoted classification history are
  mandatory. If the file cannot be read, treat the check as failed and reclassify as `unenforced`,
  `biased_rca`, or `too_weak`. If a prior session already classified this mistake as `pending_unloaded`, this
  selection is **invalid** — reclassify as `unenforced`, `biased_rca`, or `too_weak`.
- **Prior prevention file existence check (mandatory):** In addition to the classification history check, the
  agent **must** confirm that the prior prevention file actually exists on disk at its expected path via a Read
  or Glob tool call. Quote the tool call result in your output. If the prevention file does not exist on disk,
  `pending_unloaded` is **invalid** — the prevention was not actually implemented; reclassify as `unenforced`,
  `biased_rca`, or `too_weak`. A verbal assertion that the file exists is not sufficient.
- When deferring, update the learning record with: session ID, date, and the specific prevention that was
  applied but not yet loaded. This record is required for reclassification in the next session.
- Defer evaluation until after the next plugin cache refresh. If the recurrence persists after the cache is
  refreshed, reclassify the cause as one of the other three types (`unenforced`, `biased_rca`, or `too_weak`).

### Step 3 — Verify File Modification Before Marking Prevention Applied

Prevention is **not** considered applied in the current session unless at least one file was actually
modified via an Edit or Write tool call **and that modification implements the specific prevention action
required by Step 2 for the classified cause type.** The required action per cause type is:

| Cause Type | Required file modification |
|---|---|
| `unenforced` | A hook registration file, hook handler source file, or enforcement-mechanism file that implements the new hook-level control |
| `biased_rca` | The methodology or priming-instruction section of a pipeline artifact file — not a conclusions-recording section, and not a conclusions-recording file — modified to correct the specific pipeline flaw identified in Step 2 |
| `too_weak` | A file at the escalated prevention level (e.g., hook file if escalating from documentation; code fix file if escalating from hook), with the modification itself implementing the recurrence-addressing control |
| `pending_unloaded` | The learning record (mistakes JSON file) updated with the deferral entry |

A modification to **any other file** — including whitespace edits, punctuation changes, or updates to
unrelated documentation — does **not** satisfy this requirement, regardless of whether an Edit or Write tool
call was made. The file modified must be the artifact that implements the required prevention action.

**Hook registration completeness requirement (`unenforced` and `too_weak` hook escalations):** When the
required file modification is a hook registration file, that modification is only valid if the registered hook
handler (a) exists in the repository at the path specified in the registration, confirmed via a Read or Glob
tool call in the current session (verbal assertion or planning notes are not sufficient), and (b) was created
or modified via an Edit or Write tool call **in the current session** — an existing handler that was not
touched in the current session does not satisfy requirement (b). The handler modification must itself implement
logic that specifically addresses the classified recurrence — not merely a comment, whitespace, or unrelated
change. Registering a hook that points to a nonexistent file, an empty file, a handler not modified in this
session, or a handler whose modification does not address the recurrence does **not** satisfy the Step 3
requirement, even if the hook registration file itself was modified via Edit or Write.

**`biased_rca` pipeline-fix specificity requirement:** A modification to a pipeline artifact file satisfies
the `biased_rca` Step 3 requirement **only if** (a) the modified section is the methodology or
priming-instruction content of the file (not a conclusion-summary section), and (b) the modification
specifically corrects the pipeline flaw identified in the mandatory prior-RCA citation (Step 2). The agent
must state, in its output, which specific section or passage was changed and how it directly addresses the
cited pipeline flaw. An edit to a conclusion-summary section of a methodology file, or an edit to an unrelated
section of any pipeline artifact file, does **not** satisfy this requirement.

**Exception — `pending_unloaded` cause type:** When the cause is classified as `pending_unloaded`, the
standard prevention-file requirement is replaced by a weaker requirement: the learning record (mistakes JSON
file) **must** be updated with the deferral entry (session ID, date, specific prevention). This deferral
record constitutes the required file modification. A `pending_unloaded` classification with no file modified
at all — including no learning record update — is **invalid**.

### Gate Summary Table

| Condition | Required Action |
|---|---|
| First-time occurrence | Exempt — gate does not activate |
| Cause type unknown, halt conditions not met | Proceed with most-consistent cause type (priority order: unenforced > too_weak > biased_rca > pending_unloaded) |
| Cause type unknown, halt conditions met (2+ non-invalidated cause types each with distinct, indistinguishable observable artifacts, indistinguishability confirmed by literal artifact text without inference; halt re-evaluated after any invalidation) | Halt — require explicit classification |
| `unenforced` | Escalate to hook-level or stronger; docs blocked |
| `biased_rca` (Classification confirmed final phrase present; prior RCA conclusion quoted; wrongness shown by pre-existing artifact only; pipeline methodology/priming-instruction section modified to correct the specific identified flaw) | Fix analysis pipeline; docs-of-conclusions blocked; current-session tool output blocked as artifact |
| `biased_rca` (Classification confirmed final phrase absent; or prior RCA conclusion not quoted; or wrongness supported by current-session tool call result or re-inference; or no independent pre-existing artifact cited; or only a conclusion-section modified; or modification does not address the specific identified flaw) | Invalid — reclassify as `unenforced` |
| `biased_rca` invalidated by verifiability requirement | Reclassified as `unenforced`; invalidated type excluded from halt-branch counting; halt re-evaluated |
| `too_weak` where prior prevention was at documentation level or stronger | Escalate at least one prevention level above prior; documentation-only output blocked if prior was already at documentation level |
| `too_weak` where prior prevention was sub-documentation (verbal note, informal memory, undocumented) | Must escalate directly to hook-level or stronger; documentation-only output blocked |
| `too_weak` escalated-level file modified, modification itself implements recurrence-addressing logic (not comment/whitespace), handler exists (confirmed via Read/Glob in current session), handler modified in current session | Prevention applied |
| `too_weak` escalated-level file modified but modification is comment/whitespace/unrelated; or handler nonexistent; or unconfirmed via tool call; or not modified in current session | Invalid — modification must implement the recurrence-addressing control |
| `pending_unloaded` (first time, schema has classification history field, Read tool call confirmed no prior classification by quoting classification history field, Read/Glob tool call confirmed prior prevention file exists on disk, learning record updated) | No new prevention; defer to next cache refresh |
| `pending_unloaded` (schema has no dedicated classification history field) | Invalid — reclassify as `unenforced`/`biased_rca`/`too_weak` |
| `pending_unloaded` (previously classified, or classification history check not confirmed via Read tool call quoting classification history field, or prior prevention file existence not confirmed via Read/Glob tool call, or no learning record update) | Invalid — reclassify as `unenforced`/`biased_rca`/`too_weak` |
| File modified in session does not implement the cause-type-specific required action from Step 2 | Block marking prevention as applied |
| Mandatory reclassification occurred (including retroactive halt void) | Reclassified type is binding; Step 1 may not be re-run under any framing |

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

Include the signature in the JSON entry:
```json
{
  "cause_signature": "<cause_type>:<barrier_type>:<context>"
}
```

The `cause_signature` field is optional. Existing entries without it are treated as unclassified and excluded from
signature-based recurrence detection.
