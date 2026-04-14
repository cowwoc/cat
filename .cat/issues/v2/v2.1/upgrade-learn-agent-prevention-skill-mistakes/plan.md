# Upgrade learn-agent prevention: instruction-builder on first instruction-file mistake, SPRT on recurrence

## Goal

When a mistake occurs while executing an instruction file, the current learn-agent workflow creates a follow-up issue.
This is too passive. Instead:

1. **First occurrence** in a given instruction file → immediately invoke `cat:instruction-builder-agent` on that file
   to harden the instructions against the specific failure mode, without waiting for a separate issue cycle.
2. **Recurrence** of the same mistake in the same instruction file (detectable via learn-agent logs matching
   `cause_signature` + `prevention_path`) → add a SPRT test case targeting the exact failure scenario,
   so future regressions are automatically detected and trigger the hardening loop again.

The intent: harden first, test second, re-harden when tests fail. Automemory is explicitly not a substitute
for either step.

**Instruction files** include any file that defines agent behavior: skill files (`plugin/skills/*/SKILL.md`,
`plugin/skills/*/first-use.md`), CLAUDE.md, rule files (`.claude/rules/*.md`, `plugin/rules/*.md`),
agent prompt files (`plugin/agents/*.md`), and any other markdown file whose content is read by an agent
to govern its behavior.

## Background

The current Phase 3 (Prevent) of learn-agent:
- Commits a prevention to the responsible file when possible
- Otherwise creates a follow-up issue

There is no distinction between mistakes that affect instruction files (which can be immediately hardened via
instruction-builder) versus other files (Java source, build config, etc.) where hardening is not applicable.
And there is no escalation path from repeated failure to SPRT coverage.

## Changes Required

### 1. Phase-prevent.md: Detect instruction-file mistakes and route to instruction-builder

In Phase 3 (prevent), after identifying `prevention_path`:

- Check whether `prevention_path` refers to an instruction file. An instruction file is any of:
  - `plugin/skills/**/*.md` (skill and first-use files)
  - `CLAUDE.md` (project root or any subdirectory)
  - `.claude/rules/*.md`
  - `plugin/rules/*.md`
  - `plugin/agents/*.md`
  - Any other `.md` file whose purpose is to govern agent behavior (use judgment)
- If yes → set `prevention_type: "instruction_builder"` and flag `run_instruction_builder: true`
- If no → use existing prevention logic (commit change or create issue)

### 2. learn-agent orchestrator: invoke instruction-builder when flagged

In the orchestrator (Step 6 equivalent), when `run_instruction_builder: true`:

```
Skill tool:
  skill: "cat:instruction-builder-agent"
  args: "Fix: <mistake_description>  EXISTING_INSTRUCTION_PATH: <prevention_path>"
```

Run instruction-builder in foreground. Capture the commit hash it produces and store as
`prevention_commit_hash` in the learning record (update via `record-learning` or annotate the existing
learning entry).

### 3. Recurrence detection: check learn-agent logs for same (cause_signature, prevention_path) pair

In Phase 3, before deciding on prevention strategy, query the mistakes log:

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/record-learning" query \
  --cause-signature "<cause_signature>" \
  --prevention-path "<prevention_path>"
```

If the query returns ≥ 1 prior entry with the same `cause_signature` AND same `prevention_path`
(i.e., same mistake, same skill file, already hardened at least once), set `add_sprt_test: true`.

### 4. When add_sprt_test is true: create SPRT test case for the failure scenario

Invoke `cat:sprt-runner-agent` (or `cat:add-agent`) to add a test case that exercises the specific
failure mode:

- The test case scenario describes the situation that triggered the mistake
- The assertion checks the behavior that was wrong (e.g., "grader is spawned via claude-runner, not
  the Agent tool")
- The test case is added to the skill's `plugin/tests/skills/<skill-name>/first-use/` directory

### 5. record-learning: support `query` subcommand

Add a new subcommand `record-learning query --cause-signature X --prevention-path Y` that:
- Reads `mistakes-YYYY-MM.json` files (current month + prior months)
- Returns count and IDs of entries matching both criteria
- Exits 0 if found, exits 1 if no match (to support shell conditionals)

### 6. SPRT failure → re-trigger hardening loop

When a SPRT test added via this mechanism fails (i.e., SPRT decision reaches REJECT), the
`sprt-runner-agent` should surface this to the user and suggest re-invoking `learn-agent` on the
affected skill, which would then detect recurrence and invoke instruction-builder again.

This closes the feedback loop:
```
Mistake → instruction-builder (harden) → SPRT test added →
  SPRT REJECT → learn-agent → instruction-builder (re-harden) → SPRT re-run
```

## Acceptance Criteria

- [ ] When `prevention_path` is an instruction file (skill, CLAUDE.md, rule file, agent prompt, or other
      behavior-governing markdown), Phase 3 sets `prevention_type: "instruction_builder"` and the
      orchestrator invokes `cat:instruction-builder-agent` on that file
- [ ] `record-learning query` subcommand exists and returns prior entries matching
      `cause_signature` + `prevention_path`
- [ ] When recurrence is detected (same cause_signature + prevention_path as a prior entry),
      Phase 3 sets `add_sprt_test: true` and the orchestrator adds a SPRT test case to the skill's
      test directory
- [ ] The added SPRT test case's scenario and assertion target the specific failure mode from the
      mistake description
- [ ] When `add_sprt_test: true`, instruction-builder is also invoked (both hardening and test coverage
      are applied on recurrence)
- [ ] Automemory is explicitly NOT used as a prevention mechanism for instruction-file mistakes
- [ ] Existing learn-agent behavior for non-instruction-file mistakes is unchanged
