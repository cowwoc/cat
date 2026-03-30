# Plan

## Goal

Update all non-planning MD files to use the current config option names: `verify`→`caution`, `effort`→`curiosity`,
`patience`→`perfection`. Also rename `docs/patience.md` to `docs/perfection.md` and rename the `effort` skill
argument in `plan-builder-agent` to `curiosity`.

Files to update include: `README.md`, `docs/patience.md`, `plugin/skills/` first-use.md files,
`.claude/rules/common.md`. The `.cat/issues/` planning files are excluded (historical records).

## Pre-conditions

(none)

## Post-conditions

- [ ] All non-planning MD files with `verify`, `effort`, `patience` as config option names updated to `caution`, `curiosity`, `perfection`
- [ ] `docs/patience.md` renamed to `docs/perfection.md` with contents updated
- [ ] `README.md` link to `docs/patience.md` updated to `docs/perfection.md`
- [ ] `plan-builder-agent` `effort` skill argument renamed to `curiosity` throughout
- [ ] `.claude/rules/common.md` config options table updated to list current option names
- [ ] Tests passing, no regressions
- [ ] E2E: no remaining `effort`, `verify` (as config key), or `patience` config key references in `plugin/skills/`, `docs/`, or `README.md`
- [ ] Open issue `2.1-fix-instruction-builder-effort-gate` is covered and can be closed after this issue merges

## Research Findings

Scanned all non-planning MD files for outdated config key references. Authoritative descriptions come from Java
enums `CautionLevel`, `CuriosityLevel`, and `PerfectionLevel`.

### Files requiring changes

**Job 1 — docs/ and README.md (docs: commit):**

`README.md` (lines 251–308):
- Config JSON example: `verify`/`effort`/`patience` → `caution`/`curiosity`/`perfection` with default `medium`
- Options table: replace 3 rows (verify, effort, patience) with (caution, curiosity, perfection)
- Detailed option descriptions: rewrite caution/curiosity/perfection sections from enum Javadoc
- Stakeholder Reviews: `When \`verify\` is \`changed\` or \`all\`` → `When \`caution\` is \`medium\` or \`high\``
- Link: `[patience details](docs/patience.md)` → `[perfection details](docs/perfection.md)`

`docs/patience.md` — rename to `docs/perfection.md`:
- Title: "Patience Decision Matrix" → "Perfection Decision Matrix"
- Decision rule: `patience_multiplier` → `perfection_multiplier`
- Config key: `patience` → `perfection` (in text and JSON example)
- All prose references to `patience` config → `perfection`

`docs/severity.md` (lines 121–132):
- Line 121: `` `patience` (see [patience.md](patience.md)) `` → `` `perfection` (see [perfection.md](perfection.md)) ``
- Line 124: `patience fix/defer decision` → `perfection fix/defer decision`
- Line 126: `### minSeverity vs patience` → `### minSeverity vs perfection`
- Lines 132: `patience: "high"` → `perfection: "high"`

**Job 2 — plugin/skills/ (refactor: commit):**

`plugin/skills/work-review-agent/first-use.md` (line 84):
- Old: `The values of \`trust\`, \`verify\`,`
- New: `The values of \`trust\`, \`caution\`,`

`plugin/skills/collect-results-agent/first-use.md` (line 158):
- Old: `**Important:** The main agent handles these issues based on the \`patience\` setting`
- New: `**Important:** The main agent handles these issues based on the \`perfection\` setting`

`plugin/skills/tdd-implementation-agent/first-use.md` (line 184):
- Old: `**Effort gate:** Read \`effort\` from \`${CLAUDE_PROJECT_DIR}/.cat/config.json\`. If \`effort = low\`, skip`
- New: `**Curiosity gate:** Read \`curiosity\` from the effective config (\`get-config-output effective\`). If \`curiosity = low\`, skip`

`plugin/skills/instruction-builder-agent/first-use.md` (line 1382):
- Old: `**Effort-gate note:**`
- New: `**Curiosity gate note:**`

`plugin/skills/plan-builder-agent/first-use.md`:
- Line 37: `read CAT_AGENT_ID EFFORT MODE` → `read CAT_AGENT_ID CURIOSITY MODE`
- Line 55: `based on \`$EFFORT\`` → `based on \`$CURIOSITY\``
- Arguments table row 2: `| 2 | effort |` → `| 2 | curiosity |`
- Same read pattern in revise mode description

`plugin/skills/plan-builder-agent/SKILL.md`:
- argument-hint: `<effort>` → `<curiosity>`
- description: "effort-based planning depth" → "curiosity-based planning depth"

`plugin/skills/work-merge-agent/first-use.md` (lines 218–220):
- `**MEDIUM:** Read EFFORT from config` → `**MEDIUM:** Read CURIOSITY from config`
- `${CAT_AGENT_ID} ${EFFORT} revise` → `${CAT_AGENT_ID} ${CURIOSITY} revise`

**Job 3 — .claude/rules/common.md (config: commit):**

`.claude/rules/common.md`:
- Line 250: `trust level, verify level, effort, patience` → `trust level, caution level, curiosity, perfection`
- Line 257 (Sources of truth table): `\`trust\`, \`verify\`, \`effort\`` → `\`trust\`, \`caution\`, \`curiosity\`, \`perfection\``

## Jobs

### Job 1

Update `README.md`, rename `docs/patience.md` to `docs/perfection.md`, and update `docs/severity.md`.

**README.md** — replace the Configuration section config JSON example, options table, and descriptions:

Replace the config JSON block (lines 251–260):
```json
{
  "trust": "medium",
  "caution": "medium",
  "curiosity": "medium",
  "perfection": "medium",
  "fileWidth": 120,
  "displayWidth": 120
}
```

Replace the options table rows for verify/effort/patience with:
```
| `caution` | string | `medium` | How cautiously CAT validates changes before the approval gate |
| `curiosity` | string | `medium` | How broadly stakeholder review considers system context |
| `perfection` | string | `medium` | How much CAT pursues perfection in the current task |
```

Replace the **verify** description block with:
```
**caution** — How cautiously CAT validates changes before the approval gate:
- `low` — Compile only (fastest feedback)
- `medium` — Compile and unit tests (default)
- `high` — Compile, unit tests, and E2E tests (maximum confidence)
```

Replace the **effort** description block with:
```
**curiosity** — How broadly stakeholder review and research considers system context:
- `low` — Skip automatic stakeholder review; review only runs if explicitly invoked
- `medium` — Run automatic stakeholder review scoped to changed files and direct dependencies
- `high` — Run automatic stakeholder review with holistic system integration scope
```

Replace the **patience** description block (including the `See [patience details]...` link) with:
```
**perfection** — How much CAT pursues perfection in the current task:
- `low` — Stay focused on the primary goal, defer tangential improvements
- `medium` — Fix issues that are easy to address, defer complex ones
- `high` — Fix every issue encountered, even if tangential to the primary goal

See [perfection details](docs/perfection.md) for the full cost/benefit framework and decision matrix.
```

Replace the Stakeholder Reviews trigger line:
- Old: `When \`verify\` is \`changed\` or \`all\`, CAT runs multi-perspective stakeholder reviews before merge:`
- New: `When \`caution\` is \`medium\` or \`high\`, CAT runs multi-perspective stakeholder reviews before merge:`

**docs/patience.md → docs/perfection.md**:
1. Use `git mv docs/patience.md docs/perfection.md` in the worktree
2. In `docs/perfection.md`, replace all occurrences of `patience` with `perfection` and
   `patience_multiplier` with `perfection_multiplier`, including:
   - Title: "Patience Decision Matrix" → "Perfection Decision Matrix"
   - Decision rule: `patience_multiplier` → `perfection_multiplier`
   - Config JSON: `"patience": "medium"` → `"perfection": "medium"`
   - All prose references to `patience` config option

**docs/severity.md** — update `patience` references to `perfection`:
- Line 121: `` `patience` (see [patience.md](patience.md)) `` → `` `perfection` (see [perfection.md](perfection.md)) ``
- Line 124: `patience fix/defer decision` → `perfection fix/defer decision`
- Line 126: `### minSeverity vs patience: a concrete example` → `### minSeverity vs perfection: a concrete example`
- Line 132: `patience: "high"` → `perfection: "high"`

Commit message: `docs: update config option names (verify→caution, effort→curiosity, patience→perfection)`

### Job 2

Update plugin skill files to use current config option names.

**plugin/skills/tdd-implementation-agent/first-use.md** (line 184):
- Replace the entire "Effort gate" sentence:
  - Old: `**Effort gate:** Read \`effort\` from \`${CLAUDE_PROJECT_DIR}/.cat/config.json\`. If \`effort = low\`, skip`
  - New: `**Curiosity gate:** Read \`curiosity\` from the effective config (\`get-config-output effective\`). If \`curiosity = low\`, skip`

**plugin/skills/instruction-builder-agent/first-use.md** (line 1382):
- Replace label:
  - Old: `**Effort-gate note:**`
  - New: `**Curiosity gate note:**`

**plugin/skills/plan-builder-agent/first-use.md**:
- Line 37: Replace `read CAT_AGENT_ID EFFORT MODE ISSUE_PATH REVISION_CONTEXT <<< "$ARGUMENTS"`
  with `read CAT_AGENT_ID CURIOSITY MODE ISSUE_PATH REVISION_CONTEXT <<< "$ARGUMENTS"`
- Line 55: Replace `Apply the following depth to plan.md content based on \`$EFFORT\`:`
  with `Apply the following depth to plan.md content based on \`$CURIOSITY\`:`
- Arguments table: Replace `| 2 | effort | Planning depth: \`low\`, \`medium\`, or \`high\` |`
  with `| 2 | curiosity | Planning depth: \`low\`, \`medium\`, or \`high\` |`
- In revise mode description: Replace the `read CAT_AGENT_ID EFFORT MODE` occurrence in
  the bash block with `read CAT_AGENT_ID CURIOSITY MODE`

**plugin/skills/plan-builder-agent/SKILL.md**:
- Replace `argument-hint: "<cat_agent_id> <effort> <mode> <contextPath> [revision-context]"`
  with `argument-hint: "<cat_agent_id> <curiosity> <mode> <contextPath> [revision-context]"`
- In `description:` field: replace `effort-based planning depth` with `curiosity-based planning depth`

**plugin/skills/work-merge-agent/first-use.md** (lines 218–220):
- Replace `**MEDIUM:** Read EFFORT from config, then invoke:`
  with `**MEDIUM:** Read CURIOSITY from config, then invoke:`
- Replace `"${CAT_AGENT_ID} ${EFFORT} revise ${ISSUE_PATH}`
  with `"${CAT_AGENT_ID} ${CURIOSITY} revise ${ISSUE_PATH}`

**plugin/skills/work-review-agent/first-use.md** (line 84):
- Replace `The values of \`trust\`, \`verify\`,`
  with `The values of \`trust\`, \`caution\`,`

**plugin/skills/collect-results-agent/first-use.md** (line 158):
- Replace `**Important:** The main agent handles these issues based on the \`patience\` setting (see`
  with `**Important:** The main agent handles these issues based on the \`perfection\` setting (see`

Commit message: `refactor: rename effort→curiosity and effort-gate→curiosity-gate in plugin skills`

### Job 3

Update `.claude/rules/common.md` and close the issue.

**.claude/rules/common.md**:
- Line 250: Replace `trust level, verify level, effort, patience`
  with `trust level, caution level, curiosity, perfection`
- Line 257: Replace `| \`trust\`, \`verify\`, \`effort\` | \`.cat/config.json\` field values |`
  with `| \`trust\`, \`caution\`, \`curiosity\`, \`perfection\` | \`.cat/config.json\` field values |`

Commit: `config: update config option names in dev convention docs`

Then update `index.json` in the issue directory to set `"status": "closed"` and include it
in the same commit.

### Job 4

Fix remaining stale `<verify>` argument-hints in SKILL.md files and residual `effort` prose in
`plan-builder-agent/first-use.md`.

**plugin/skills/work-review-agent/SKILL.md**:
- In `argument-hint:`, replace `<verify>` with `<caution>` (the parameter name for the caution config key)

**plugin/skills/work-implement-agent/SKILL.md**:
- In `argument-hint:`, replace `<verify>` with `<caution>`

**plugin/skills/work-merge-agent/SKILL.md**:
- In `argument-hint:`, replace `<verify>` with `<caution>`

**plugin/skills/work-with-issue-agent/SKILL.md**:
- In `argument-hint:`, replace `<verify>` with `<caution>`

**plugin/skills/work-confirm-agent/SKILL.md**:
- In `argument-hint:`, replace `<verify>` with `<caution>`

**plugin/skills/plan-builder-agent/first-use.md** (lines 216, 218 — Iterative Completeness Review section):
- Replace `effort is \`low\`` with `curiosity is \`low\``
- Replace `effort \`medium\` or \`high\`` with `curiosity \`medium\` or \`high\``

**plugin/skills/collect-results-agent/first-use.md** (line 234 — template example output):
- Replace `patience setting` with `perfection setting`

Commit message: `refactor: fix stale <verify> argument-hints and residual effort/patience prose`
