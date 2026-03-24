# Plan

## Goal

Define and apply an organic benchmark test design standard for skill empirical tests. The standard
replaces the current "primed" approach (where `system_reminders` lists available skills) with tests
that assign realistic work and verify both that the agent chose to use the skill AND that it followed
the skill correctly.

## Pre-conditions

(none)

## Post-conditions

- [ ] `plugin/concepts/benchmark-design.md` exists and documents the organic benchmark standard
- [ ] The standard defines:
  - [ ] No `system_reminders` that list skills — agents choose skills based on the SKILL.md
    description injected at session start
  - [ ] Prompts assign realistic work that organically requires the skill (not "use skill X" or
    "read skill X first")
  - [ ] Two verification tiers:
    - Tier 1 — Skill chosen: `must_use_tools: ["Skill"]` (agent invoked the skill)
    - Tier 2 — Skill followed: tool-usage criteria verifying the agent executed the skill's
      procedure (e.g., for file-reading skills: `must_use_tools: ["Bash"]` confirming a single
      batch operation rather than separate sequential tool calls)
  - [ ] Negative cases are clearly out of scope (search-only, single file, explicit known paths)
- [ ] `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` is updated (or created) to
  follow the organic standard:
  - [ ] No `system_reminders` field (or empty array)
  - [ ] Positive cases verify both skill choice and correct execution (Bash-based batch, not
    separate Grep then Read)
  - [ ] Negative cases cover: search-only, single file, 2 explicit known paths
- [ ] Running `empirical-test-runner` on grep-and-read-agent benchmark achieves ≥80% on each
  positive test case

## Files Modified

- `plugin/concepts/benchmark-design.md` — new reference document
- `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` — updated to organic standard

## Execution Steps

### Step 1 — Read grep-and-read-agent first-use.md

Read `plugin/skills/grep-and-read-agent/first-use.md` and `plugin/skills/grep-and-read-agent/SKILL.md`
to understand the skill's procedure and trigger conditions.

### Step 2 — Write benchmark-design.md

Create `plugin/concepts/benchmark-design.md` documenting the organic benchmark standard with:
- Rationale (why no priming)
- Test case structure (target_description, empty system_reminders, configs)
- Two-tier verification: skill chosen + skill followed correctly
- Positive case design rules
- Negative case design rules
- Example test case in the correct format

### Step 3 — Design organic test cases for grep-and-read-agent

Design test cases with no system_reminders. Positive prompts must require the agent to search for
files at unknown paths AND read their contents — the skill's exact trigger. Tier 2 criterion:
after invoking the skill, the agent should use Bash (combined search+read), not separate Grep then
Read tool calls.

Suggested positive prompts:
- "Find all Java classes that implement HookHandler and explain what each one does." — requires
  grep for implementations + read each file; paths unknown; clearly 3+ files
- "What do the test files for HookHandler verify? Read them and summarize the test coverage." —
  requires finding test files + reading them

Negative prompts:
- Search-only: "List all Java files that reference HookHandler — just the paths, not their contents."
- Single explicit file: "Read plugin/skills/grep-and-read-agent/SKILL.md."
- Two explicit known paths: "Read plugin/skills/grep-and-read-agent/SKILL.md and
  plugin/skills/grep-and-read-agent/first-use.md."

### Step 4 — Create benchmark/test-cases.json

Create `plugin/skills/grep-and-read-agent/benchmark/` directory and write `test-cases.json` with
no `system_reminders` (empty array or omit field). Each positive test case must have:
- `must_use_tools: ["Skill"]` (Tier 1)
- A second criterion verifying Bash use (Tier 2) — express as a `_metadata` note describing the
  expected execution pattern, or as `must_use_tools: ["Skill", "Bash"]` if the runner supports it

### Step 5 — Validate empirically

```bash
RUNNER="/home/node/.config/claude/plugins/cache/cat/cat/2.1/client/bin/empirical-test-runner"
"$RUNNER" \
  --config plugin/skills/grep-and-read-agent/benchmark/test-cases.json \
  --trials 3 \
  --model haiku \
  --cwd .
```

Positive cases must achieve ≥80%. Revise prompts if needed.

### Step 6 — Commit

```bash
git add plugin/concepts/benchmark-design.md \
        plugin/skills/grep-and-read-agent/benchmark/test-cases.json
git commit -m "feature: add organic benchmark design standard and grep-and-read-agent test cases"
```

## Success Criteria

- `plugin/concepts/benchmark-design.md` exists with organic standard documented
- `plugin/skills/grep-and-read-agent/benchmark/test-cases.json` has no system_reminders priming
- At least 2 positive cases (Tier 1 + Tier 2 criteria)
- At least 3 negative cases
- Empirical validation: ≥80% on positive cases
