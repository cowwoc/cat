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

### Wave 2 (fix stakeholder review concerns)
- **[CRITICAL/testing] Add test coverage for Step 4.4** — Write tests covering: (a) the execution guard
  (Step 4.4 only runs when `overall_decision="Reject"`), (b) session-analyzer invocation with correct arguments,
  (c) batch contamination detection logic, (d) conclusion classification logic (genuine defect / test artifact /
  inconclusive), (e) output format correctness, and (f) error handling when session-analyzer fails or returns no
  results. Tests live in the appropriate TestNG test class for the instruction-builder skill.
- **[HIGH/ux] Fix investigation output ordering and replace placeholder text** — Move the investigation findings
  block (`SPRT FAILURE INVESTIGATION`) so it appears *after* the SPRT per-test-case results table, not before.
  Replace all placeholder text (e.g., `<run-ids>`, `<describe what the agent did…>`, `<list any priming text…>`)
  in `plugin/skills/instruction-builder-agent/first-use.md` Step 4.4 with concrete instructions that tell the agent
  what to fill in and how to derive each value from the session-analyzer output.
- **[MEDIUM/architecture] Fix undefined `${AGENT_ID}` variable** — In Step 4.4 sub-step 3 of
  `plugin/skills/instruction-builder-agent/first-use.md` (lines 540-542), the bash snippet references
  `${AGENT_ID}` which is never assigned. Update sub-step 2 to capture the agent IDs returned by
  `session-analyzer analyze` into a variable (e.g., `AGENT_ID`) and pass it to sub-step 3, completing the
  data-flow between the two sub-steps.
- **[MEDIUM/requirements] Invoke `cat:get-history-agent` per post-condition 2** — Post-condition 2 requires
  `cat:get-history-agent` / `session-analyzer` to examine raw subagent conversations. Add an explicit instruction in
  Step 4.4 of `plugin/skills/instruction-builder-agent/first-use.md` to invoke `cat:get-history-agent` (in addition
  to the existing session-analyzer bash commands) so that transcript examination is performed via the skill, satisfying
  the post-condition.
- **[MEDIUM/design] Complete the bash snippet for discovering subagent IDs** — In Step 4.4 sub-step 2 of
  `plugin/skills/instruction-builder-agent/first-use.md` (lines 529-534), the bash snippet shows a comment about
  filtering benchmark-run subagents but omits the actual filter command. Replace the comment with a concrete command
  that extracts the relevant subagent IDs from `session-analyzer analyze` output using `grep`/`sed`/`awk` (no `jq`),
  scoped to the rejected test cases.
- **[MEDIUM/ux] Add per-conclusion-type guidance and next steps** — In Step 4.4 of
  `plugin/skills/instruction-builder-agent/first-use.md` (lines 572-585), each conclusion type currently has no
  guidance on implications or follow-up actions. Add explicit next-step instructions for each conclusion type:
  (a) "Genuine skill defect" → proceed to skill-analyzer-agent with the identified failure pattern; (b) "Test
  environment artifact" → recommend rerunning the benchmark with fresh subagents before modifying the skill; (c)
  "Inconclusive" → describe what additional evidence to gather before deciding.
- **[MEDIUM/ux] Clarify automatic vs. manual steps in Step 4.4** — In `plugin/skills/instruction-builder-agent/first-use.md`
  lines 528-542, bash commands and prose instructions are interleaved without distinguishing which steps the agent
  executes automatically and which require manual interpretation. Label each sub-step clearly (e.g., "Run the
  following command:" vs. "Interpret the output and identify…") so the agent knows when to execute vs. when to reason.
- **[LOW/requirements] Add thinking block examination to Step 4.4** — The plan's Approach section (item 3) specifies
  examining thinking blocks for evidence of the agent rationalizing follow-up questions. Add an explicit sub-step in
  Step 4.4 of `plugin/skills/instruction-builder-agent/first-use.md` instructing the agent to search for `<thinking>`
  content in the subagent transcripts using `session-analyzer search` with a pattern matching thinking-block markers,
  and to include thinking block findings in the investigation report.

### Wave 3 (fix second-round stakeholder review concerns)

#### first-use.md fixes

- **[HIGH+MEDIUM/architecture] Eliminate duplicate session-analyzer invocation and fix display-order instruction** —
  Sub-step 5 re-invokes `session-analyzer analyze ${CLAUDE_SESSION_ID}` (line 581), duplicating sub-step 2. Remove the
  re-invocation in sub-step 5; instead reuse the `ANALYZE_OUTPUT` variable captured in sub-step 2. Separately, revise
  the "Display SPRT results first" instruction (lines 627–629) to: "Do NOT re-display the SPRT benchmark summary
  (already presented at end of Step 4.3). Present ONLY the investigation report." This resolves concerns 12, 13, and
  17.

- **[HIGH/architecture] Close the data-flow gap between sub-step 2 and sub-step 3** — The `cat:get-history-agent`
  invocation in sub-step 3 (line 553) uses the literal placeholder `<AGENT_ID>` which is never bound to the `AGENT_IDS`
  loop from sub-step 2. Add an explicit `for agent_id in ${AGENT_IDS}; do ... done` loop construct around sub-steps
  3–7 so the data flow from sub-step 2 to sub-steps 3–7 is closed. Standardize placeholder syntax to angle brackets for
  agent-substituted values: use `<claude_session_id>` and `<agent_id>` instead of mixing `${CLAUDE_SESSION_ID}` (shell
  var) and `<AGENT_ID>` (angle bracket) in the same invocation block. Add an explicit loop-variable label: "For each
  `agent_id` in `AGENT_IDS` (one per iteration):" to eliminate plural/singular ambiguity. Resolves concerns 2, 14, 29.

- **[HIGH/architecture] Document session-analyzer output format inline** — The awk filter `awk '{print $1}'` in
  sub-step 2 (line 539) and the sub-step 5 contamination check both assume a specific column layout with no documented
  schema. Add an inline comment immediately before the grep/awk line explaining the expected output format of
  `session-analyzer analyze` (e.g., "Output format: `<agent_id> <status> <description>` — one subagent per line; $1 is
  the agent ID"). Resolves concerns 1, 27.

- **[HIGH/ux] Add auto/manual step labels and header for sub-steps 2–8** — No indication distinguishes which bash
  commands run automatically versus require manual interpretation. Add a header before sub-step 2: "Sub-steps 2–7 are
  automated tool invocations. Sub-step 8 synthesizes findings into a human-readable report." Verify each sub-step
  already carries an `(automatic)` or `(interpret)` label; add the label where missing. Resolves concerns 7, 22.

- **[HIGH/ux] Restructure batch contamination sub-step** — Sub-step 5 (lines 573–585) mixes manual heuristics with an
  automated verify step ambiguously. Restructure: first present the reused `ANALYZE_OUTPUT` from sub-step 2 as the
  automated data source, then list the interpretation criteria (resume: true, shared subagent ID, correlated failure
  rate) as a separate bulleted "Interpret:" block, so the boundary between automated check and manual reasoning is
  explicit. Resolves concern 8.

- **[LOW/architecture] Revise execution guard to use `Continue to step:` routing convention** — The guard text in
  lines 516–517 uses prose format ("proceed to Step 4.5"). Revise to match the skill's `Continue to step: X`
  routing convention: "If `overall_decision = 'Accept'`, continue to Step 4.5." Resolves concern 25.

- **[MEDIUM/ux] Define all variables upfront and simplify grep guidance** — In sub-step 2 (lines 537–545), variables
  such as `SESSION_ANALYZER` are defined inline mid-paragraph rather than at the start. Move all variable definitions
  to the top of sub-step 2 before first use. Simplify the grep/awk guidance: instead of prescribing an exact grep
  command, instruct: "Parse `ANALYZE_OUTPUT` to identify subagents spawned during benchmark runs. Store their IDs
  (one per line) in `AGENT_IDS`." Resolves concerns 21, 30.

- **[MEDIUM/ux] Add decision logic before the conclusion types example** — The conclusion criteria (why "Genuine skill
  defect" vs. "Test environment artifact" vs. "Inconclusive" is chosen) are not explained before the report example
  (lines 643–667). Add a decision logic block immediately before the example: "If batch contamination detected →
  conclude 'Test environment artifact'. If compliance failures found but no contamination → conclude 'Genuine skill
  defect'. If evidence is contradictory or unclear → conclude 'Inconclusive'." Resolves concern 23.

- **[MEDIUM/ux] Clarify artifact handling and post-investigation routing** — Lines 516–517 and 670–671 leave the
  routing ambiguous. Revise to: "If conclusion is 'Test environment artifact': do NOT proceed to Step 4.5; recommend
  rerunning after fixing the artifact source. If conclusion is 'Genuine skill defect' or 'Inconclusive': proceed to
  Step 4.5." Resolves concern 24.

- **[MEDIUM/performance] Cap AGENT_IDs to 5 and note search parallelization** — Sub-steps 3–7 run one serial
  `cat:get-history-agent` call per AGENT_ID with no upper bound (line 547). Add: "Cap at a maximum of 5 AGENT_IDs per
  rejected test case to bound investigation cost." The three separate `session-analyzer search` commands in sub-steps
  4, 6, and 7 run sequentially against the same transcript. Add a note: "Sub-steps 4, 6, and 7 search the same
  transcript; they may be executed in parallel or in a single pass to reduce elapsed time." Resolves concerns 15, 16.

- **[LOW/security] Delimit verbatim transcript quotes** — Sub-step 8 instructs quoting exact text from transcripts
  (lines 619–624) without delimiting them, risking adversarially crafted transcript content blending into the agent's
  analysis. Add instruction: "Surround all verbatim transcript quotes with triple backticks to clearly delimit them
  from the agent's own analysis." Resolves concern 28.

#### test-cases.json fixes

- **[MEDIUM/architecture] Fix TC2 guard assertion to require explicit skip message** — TC2's assertion
  `TC2_det_1` checks only for "Step 4.5" mention (line 26–28), which is too weak and could be satisfied by unrelated
  output. Add an additional deterministic assertion requiring the agent to output an explicit skip statement, e.g.,
  "Skipping Step 4.4" or "overall_decision is Accept — skipping investigation". Resolves concern 10.

- **[MEDIUM/architecture] Replace unexpanded shell variables in TC3 prompt** — TC3 (line 43) embeds
  `${CLAUDE_PLUGIN_ROOT}` and `${CLAUDE_SESSION_ID}` as unexpanded shell variable syntax in a JSON string. Replace
  with concrete example values (e.g., `/opt/cat/client/bin/session-analyzer` and `sess-abc123`). Resolves concern 9.

- **[MEDIUM/testing] Enhance TC3/TC4/TC6 pattern assertions to verify exact regex patterns** — Current assertions
  verify tools are invoked but not that the correct regex patterns are passed to `session-analyzer search`. Enhance
  assertions in TC3, TC4, and TC6 to verify the agent uses the expected search patterns (e.g.,
  `Would you like|What would you|follow.up`, `<thinking>`, `unless|except|if user|may|optional`). Resolves concern 19.

- **[HIGH+MEDIUM/testing] Add TC8: session-analyzer failure / empty results** — No test verifies graceful degradation
  when `session-analyzer` is unavailable or returns no output. Add TC8 with prompt: session-analyzer returns an error
  for all AGENT_IDs. Assertions: (a) investigation continues without aborting, (b) report records
  "session-analyzer unavailable" for affected fields, (c) investigation still produces a conclusion section. Resolves
  concerns 3, 6, 18.

- **[HIGH/testing] Add TC9: cat:get-history-agent invocation** — No test validates correct skill construction or
  transcript retrieval via `cat:get-history-agent`. Add TC9 with prompt: AGENT_IDS contains two subagent IDs.
  Assertions: (a) `cat:get-history-agent` is invoked for each agent ID, (b) the invocation args include the session
  ID and agent ID in `<claude_session_id>/<agent_id>` format. Resolves concern 4.

- **[HIGH/testing] Add TC10: Inconclusive conclusion type** — The "Inconclusive" conclusion type has zero test
  coverage. Add TC10 with prompt: findings show compliance failures present but evidence is contradictory (some runs
  fail with, some without contamination; thinking blocks unclear). Assertions: (a) agent concludes "Inconclusive",
  (b) next-step recommendation is to gather additional evidence before modifying the skill. Resolves concern 5.

- **[MEDIUM/testing] Add report quote extraction assertions** — No existing test validates that verbatim transcript
  quotes appear in the expected format. Add assertions to TC3 (or a new TC11) verifying that the failure pattern
  section contains a quoted text block in the format:
  `TC<n>, run <m> (agent <id>): [exact quote]`. Resolves concern 20.

- **[LOW/design] Document TC1 semantic_unit_id naming convention** — TC1 uses a generic sequential
  `semantic_unit_id` ("unit_1") while TC2–TC7 use domain-specific names. Add a `"description"` field or
  comment-equivalent (in the test suite's README or as a `"naming_convention"` key in the JSON root) documenting the
  naming convention: domain-specific IDs (e.g., `unit_step44_guard`) are preferred over sequential ones. Resolves
  concern 26.

## Post-conditions

- [x] `plugin/skills/instruction-builder-agent/first-use.md` Step 4.4 contains concrete investigation procedures that run automatically on SPRT Reject
- [x] The investigation step uses `cat:get-history-agent` / `session-analyzer` to examine raw subagent conversations
- [x] The investigation identifies batch contamination, thinking block patterns, and instruction priming sources
- [x] Investigation findings are presented to the user after SPRT results and before asking whether to improve the skill
- [x] Execution guard ensures Step 4.4 only runs when overall_decision="Reject"
- [x] Test coverage added for Step 4.4 (7 test cases covering guard, rejection, contamination, defect, thinking blocks, output ordering)
