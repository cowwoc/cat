# Plan: add-failure-investigation-to-skill-builder

## Goal

When `cat:instruction-builder-agent`'s SPRT benchmark rejects one or more test cases, automatically
run a structured failure investigation before presenting results to the user. The investigation mirrors
the methodology in `plugin/skills/learn/phase-investigate.md`: use `cat:get-history-agent` and
`session-analyzer` to examine raw conversation transcripts, thinking blocks, agent context at the time
of failure, and sources of priming (e.g., prior runs sharing subagent context, model defaults, escape
clauses in instructions).

## Background

Currently, when SPRT rejects, the instruction-builder shows aggregated pass/fail counts and asks the
user what to do next. The root cause is not investigated — the assumption is that the skill instructions
are at fault. This assumption can be wrong (see: batch contamination producing spurious TC5 failures
where runs 1-13 were 100% pass but runs 14-27 contaminated by shared context were ~7% pass).

## Approach

Add a new investigation sub-step after SPRT completes and before presenting results to the user:

1. Identify which test cases were Rejected
2. For each rejected test case, retrieve the subagent IDs for the failing runs using session-analyzer
3. Examine the subagent conversation logs: what did the agent receive as context? What was in its
   `<output>` tag injection? Were there thinking blocks showing the agent rationalizing adding follow-ups?
4. Look for priming sources:
   - Batch contamination (multiple runs in one subagent context)
   - Model-default behaviors overriding "Do not..." instructions
   - Escape clauses ("unless user requests") being exploited
   - Prior successful patterns in context being replicated
5. Present findings to the user alongside the SPRT results

## Sub-Agent Waves

### Wave 1
- Read `plugin/skills/instruction-builder-agent/SKILL.md` (worktree copy)
- Read `plugin/skills/learn/phase-investigate.md` for investigation methodology
- Design the investigation sub-step: where it fits in Step 3 of the SPRT loop, what it reads, what it outputs
- Update `plugin/skills/instruction-builder-agent/SKILL.md` to add the investigation sub-step after SPRT and before the user-facing results presentation
- The investigation should:
  - Use `session-analyzer analyze <SESSION_ID>` to discover subagent IDs for failing benchmark runs
  - Use `session-analyzer search <SESSION_ID>/subagents/agent-<ID> "Would you like|What would you"` to find failure instances
  - Report: which runs failed, what the agent output was, whether batch contamination is present (multiple runs in one subagent), and what priming sources were detected

### Wave 2 (Stakeholder Concern Fixes — iteration 2)

All 22 concerns are addressed in `plugin/skills/instruction-builder-agent/first-use.md`.

#### Security fixes

**Concern 1 — HIGH: AGENT_ID path traversal validation (prose-only)**
- In Step 4.4 step 3 of first-use.md, replace the prose-only validation note with an explicit bash guard before the
  `session-analyzer search` invocation:
  ```bash
  if [[ ! "$AGENT_ID" =~ ^[a-zA-Z0-9-]+$ ]]; then
    echo "Investigation skipped — invalid subagent ID format: ${AGENT_ID}"
    # Skip to Step 4.5
  fi
  ```
- Update the verification checklist item for AGENT_ID format validation to reference this exact guard.

**Concern 2 — MEDIUM: run_id values used as regex patterns**
- In Step 4.4 step 4 (batch contamination detection), add a note explicitly stating that run_id values are treated
  as literal strings (not regex patterns) when cross-referencing them against session logs. Specify that any
  regex-metacharacter characters in run_id values are escaped before use.

#### Architecture fixes

**Concern 3 — MEDIUM: Step 4.4 inline text parsing should be a Java CLI subcommand**
- Add a new subcommand `investigate-failures` to the existing `BenchmarkRunner` class in
  `client/src/main/java/io/github/cowwoc/cat/hooks/skills/BenchmarkRunner.java`.
- The subcommand accepts: `<benchmarkJsonPath> <sessionId>` as arguments.
- It reads `benchmark.json`, identifies rejected test cases and their run IDs, calls `session-analyzer analyze
  <sessionId>` via `ProcessRunner`, cross-references run IDs to subagent IDs, calls `session-analyzer search` per
  subagent for priming patterns, detects batch contamination (multiple run_ids in one subagent), and returns
  structured JSON:
  ```json
  {
    "status": "OK",
    "subagentInvestigations": [
      {
        "agentId": "abc123",
        "runIds": ["TC2_run_3"],
        "patternsFound": ["Would you like"],
        "batchContaminated": false
      }
    ],
    "contaminatedCount": 0
  }
  ```
- Error cases return `{"status": "ERROR", "message": "..."}` with appropriate messages.
- Update first-use.md Step 4.4 to invoke
  `${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner investigate-failures <benchmarkJsonPath> ${CLAUDE_SESSION_ID}`
  and consume the structured JSON output instead of inline ad-hoc parsing.
- Update the `Dispatches N subcommands:` Javadoc comment in `BenchmarkRunner.java` to include `investigate-failures`.
- Update first-use.md verification checklist to add an item: Step 4.4 uses
  `benchmark-runner investigate-failures` and consumes structured JSON output (not inline text parsing).

**Concern 4 — LOW: AGENT_ID format validation failure message undefined**
- The error message for invalid AGENT_ID format must be exactly:
  `"Investigation skipped — invalid subagent ID format: {AGENT_ID}"` (with the actual AGENT_ID value substituted).
- Add a verification checklist item: when AGENT_ID contains characters outside `[a-zA-Z0-9-]`, output is exactly
  `"Investigation skipped — invalid subagent ID format: <value>"` and execution continues to Step 4.5.

#### Design fixes

**Concern 5 — MEDIUM: `# Note: patterns are heuristic` renders as H1 heading**
- In first-use.md, line ~568, the comment `# Note: patterns are heuristic and case-sensitive` appears outside the
  fenced code block and renders as an H1 heading. Move this line inside the bash code block as a bash comment
  (prefixed with `#`), immediately after the search command, so it reads as code documentation rather than
  a Markdown heading.

**Concern 6 — LOW: Inconsistent binary-missing and graceful-degradation messages**
- Align all messages in first-use.md Step 4.4 to use two distinct canonical forms:
  - Binary missing: `"Investigation unavailable — session-analyzer not accessible"`
  - Run IDs not matchable: `"Investigation skipped — run IDs not matchable"`
- Replace the longer combined form `"Investigation unavailable — session-analyzer not accessible or run IDs not
  matchable"` (line ~603) with two separate messages, one for each condition.
- Update verification checklist items to reference each canonical message separately.

**Concern 7 — LOW: Duplicate trigger condition checklist entries**
- Lines ~976-977 and ~1028-1030 in first-use.md both describe the Step 4.4 trigger conditions for Accept vs Reject
  routing. Consolidate into a single checklist item that covers both paths: Reject triggers Step 4.4, Accept skips
  it entirely. Remove the redundant duplicate entry.

#### UX fixes

**Concern 8 — HIGH: Investigation report format prioritizes mechanics over actionable insights**
- Restructure the FAILURE INVESTIGATION report format in Step 4.4 step 6 of first-use.md to show:
  1. **What failed:** rejected test cases and run IDs
  2. **Root cause (selected):** one category chosen from evidence — `instruction clarity issue`,
     `batch contamination`, or `model helpfulness override` — with a confidence indicator (High/Medium/Low)
  3. **Contamination status:** explicit Yes/No with count of affected subagents
  4. **Specific next action:** one concrete recommendation based on the selected root cause
  Show evidence inline under each finding. Replace the placeholder
  `[instruction failure | batch contamination | model-default override]` with logic that selects ONE category
  based on what was found in the investigation (e.g., if contaminated count > 0 → batch contamination;
  if thinking block showed escape clause → model helpfulness override; otherwise → instruction clarity issue).

**Concern 9 — HIGH: Generic error messages with no context differentiation**
- Replace all generic error messages in Step 4.4 with context-specific messages and next-action guidance:
  - session-analyzer missing → `"Investigation unavailable — session-analyzer not accessible. This may resolve in a new session."`
  - malformed benchmark.json → `"Investigation skipped — benchmark.json malformed. This indicates a possible system issue; results may be incomplete."`
  - no subagents in benchmark.json → `"Investigation skipped — no subagents in benchmark.json. Proceed to Step 4.5."`
  - run IDs not matchable → `"Investigation skipped — run IDs not matchable in session log. Proceed to Step 4.5."`

**Concern 10 — MEDIUM: Pattern search lacks pre-search explanation**
- In Step 4.4 step 3 of first-use.md, add a prose note before the `session-analyzer search` command explaining what
  the patterns indicate: "Searching for phrases suggesting the agent rationalized overriding instructions
  ('Would you like', 'thinking', 'unless', etc.)."

**Concern 11 — MEDIUM: Root cause hypothesis uses unclear category labels**
- Replace `"instruction failure | batch contamination | model-default override"` with clearer labels:
  `"instruction clarity issue | batch contamination | model helpfulness override"`.
- Add a sentence in Step 4.4 step 6 explaining each category:
  - `instruction clarity issue`: the instruction was ambiguous or missing a required prohibition
  - `batch contamination`: prior passing runs in the same subagent context primed the agent
  - `model helpfulness override`: the model's default helpfulness instinct caused it to ignore a prohibition

**Concern 12 — MEDIUM: Batch contamination finding reported without implications or next action**
- In the Step 4.4 report format, when contamination is detected, add:
  - Explanation: "Prior passing runs in the same subagent context prime the agent to replicate successful patterns
    including disallowed behaviors."
  - Explicit recommendation: "Recommended: re-run with fresh subagents (one run per subagent) to isolate
    contamination."

#### Testing fixes (via verification checklist items)

All testing concerns (13–20) are addressed by adding explicit verification checklist items to first-use.md. Tests
themselves will be implemented in the existing `2.1-add-benchmark-runner-tests` issue.

**Concerns 13–17 — HIGH: Missing integration test coverage for Step 4.4 behaviors**
Add verification checklist items for each scenario:
- Binary existence guard: when `session-analyzer` binary is absent, output is exactly
  `"Investigation unavailable — session-analyzer not accessible"` and execution continues to Step 4.5 (no halt).
- benchmark.json missing: output is exactly `"Investigation unavailable — benchmark.json not found"` and
  execution continues to Step 4.5.
- benchmark.json malformed: output is exactly
  `"Investigation skipped — benchmark.json malformed. This indicates a possible system issue; results may be incomplete."`
  and execution continues to Step 4.5.
- benchmark.json empty subagents list: output is exactly
  `"Investigation skipped — no subagents in benchmark.json. Proceed to Step 4.5."` and execution continues.
- AGENT_ID with special characters (e.g., `../`): validation rejects it, outputs
  `"Investigation skipped — invalid subagent ID format: <value>"`, and execution continues to Step 4.5.
- Batch contamination scenario: multiple run_ids from one subagent produce a contaminated count > 0 in the report.
- Accept path: when `overall_decision = "Accept"`, Step 4.4 is completely skipped and `session-analyzer` is NOT
  invoked.
- Reject path: when `overall_decision = "Reject"`, the full investigation runs and the FAILURE INVESTIGATION report
  (matching the Step 4.4 format) is presented before the "improve or proceed?" prompt.

**Concerns 18–20 — MEDIUM: Priming source patterns, report format, conditional execution**
Add verification checklist items:
- Patterns used in `session-analyzer search` are case-sensitive and heuristic (not exhaustive); the prose note
  before the search command explains this.
- Report format fields are exactly as specified in the Step 4.4 template: What failed, Root cause (selected),
  Contamination status, Specific next action — no fields omitted.
- When `overall_decision = "Accept"`, Step 4.4 is completely skipped — no partial execution, no logging, no
  `session-analyzer` call.

#### Performance fix

**Concern 21 — LOW: session-analyzer called sequentially per subagent**
- In Step 4.4 of first-use.md, add a note after the per-subagent search loop: "Note: session-analyzer search is
  invoked once per subagent sequentially. If session-analyzer later gains bulk-search support, this loop can be
  consolidated into a single call."

## Post-conditions

- [ ] `plugin/skills/instruction-builder-agent/SKILL.md` contains an investigation sub-step that runs automatically on SPRT Reject
- [ ] The investigation step uses `cat:get-history-agent` / `session-analyzer` to examine raw subagent conversations
- [ ] The investigation identifies batch contamination, thinking block patterns, and instruction priming sources
- [ ] Investigation findings are presented to the user before asking whether to improve the skill
- [ ] No regressions to the existing SPRT benchmark loop
