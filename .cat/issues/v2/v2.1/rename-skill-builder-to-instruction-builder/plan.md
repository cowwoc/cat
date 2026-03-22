# rename-skill-builder-to-instruction-builder

## Goal

Replace all remaining references to the old name "skill-builder" with "instruction-builder" across
active plugin source files.

## Why

The skill was renamed from `skill-builder` to `instruction-builder` but stale references remain in
plugin skill files and concept docs. These stale names create confusion for users reading the
documentation.

## Type

refactor

## Scope

Files to update (all under `plugin/`):

- `plugin/skills/work-merge-agent/first-use.md` — 3 references (invocation instructions say `cat:skill-builder`)
- `plugin/skills/work-with-issue-agent/first-use.md` — 1 reference
- `plugin/skills/instruction-builder-agent/first-use.md` — 2 self-references
- `plugin/skills/instruction-builder-agent/skill-conventions.md` — 1 reference
- `plugin/skills/instruction-builder-agent/validation-protocol.md` — 1 reference
- `plugin/skills/instruction-builder-agent/compression-protocol.md` — 1 reference
- `plugin/skills/learn/phase-investigate.md` — 3 references
- `plugin/skills/learn/phase-prevent.md` — 4 references
- `plugin/concepts/skill-benchmarking.md` — 5 references
- `plugin/agents/skill-analyzer-agent.md` — 2 references
- `plugin/agents/skill-grader-agent.md` — 1 reference

**Out of scope:** `.cat/issues/` planning files (historical records) and closed issue PLAN.md/STATE.md files.

## Approach

For each file, replace occurrences of `skill-builder` (referring to the skill/tool) with
`instruction-builder`. Preserve occurrences where `skill-builder` is part of a different compound
noun (e.g., issue names in `.cat/issues/`).

Specifically:
- `cat:skill-builder` → `cat:instruction-builder`
- `/cat:skill-builder` → `/cat:instruction-builder`
- `skill-builder` when used as a skill name in prose → `instruction-builder`
- "skill-builder review" → "instruction-builder review"

## Post-conditions

- [ ] `grep -r "skill-builder" plugin/` returns zero matches (excluding any legitimate compound uses
  unrelated to the skill name)
- [ ] All modified files still render correctly (no broken references)
- [ ] Commit message: `refactor: rename skill-builder references to instruction-builder`

## Execution Steps

### Step 1: Audit current occurrences

Run `grep -rn "skill-builder" plugin/` in the worktree root to list all matches and confirm completeness.
Record total match count for post-condition check.

### Step 2: Update plugin/skills/work-merge-agent/first-use.md

Replace all 3 occurrences of `skill-builder` (as skill name or in `cat:skill-builder` / `/cat:skill-builder`
form) with `instruction-builder`. Use sed or direct Edit:

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g' \
  plugin/skills/work-merge-agent/first-use.md
```

### Step 3: Update plugin/skills/work-with-issue-agent/first-use.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/work-with-issue-agent/first-use.md
```

### Step 4: Update plugin/skills/instruction-builder-agent/first-use.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/instruction-builder-agent/first-use.md
```

### Step 5: Update plugin/skills/instruction-builder-agent/skill-conventions.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/instruction-builder-agent/skill-conventions.md
```

### Step 6: Update plugin/skills/instruction-builder-agent/validation-protocol.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/instruction-builder-agent/validation-protocol.md
```

### Step 7: Update plugin/skills/instruction-builder-agent/compression-protocol.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/instruction-builder-agent/compression-protocol.md
```

### Step 8: Update plugin/skills/learn/phase-investigate.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/learn/phase-investigate.md
```

### Step 9: Update plugin/skills/learn/phase-prevent.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/skills/learn/phase-prevent.md
```

### Step 10: Update plugin/concepts/skill-benchmarking.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/concepts/skill-benchmarking.md
```

### Step 11: Update plugin/agents/skill-analyzer-agent.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/agents/skill-analyzer-agent.md
```

### Step 12: Update plugin/agents/skill-grader-agent.md

```bash
sed -i 's/cat:skill-builder/cat:instruction-builder/g; s|/cat:skill-builder|/cat:instruction-builder|g; s/skill-builder/instruction-builder/g' \
  plugin/agents/skill-grader-agent.md
```

### Step 13: Confirm zero remaining occurrences

Run `grep -r "skill-builder" plugin/` to confirm zero matches remain. If any matches remain, update
the affected files and re-run until clean.

### Step 14: Run maven tests

Run `mvn -f client/pom.xml test` from the worktree root to confirm no regressions.

### Step 15: Commit all changes

Stage all modified plugin files and commit:
```bash
git add plugin/skills/work-merge-agent/first-use.md \
  plugin/skills/work-with-issue-agent/first-use.md \
  plugin/skills/instruction-builder-agent/first-use.md \
  plugin/skills/instruction-builder-agent/skill-conventions.md \
  plugin/skills/instruction-builder-agent/validation-protocol.md \
  plugin/skills/instruction-builder-agent/compression-protocol.md \
  plugin/skills/learn/phase-investigate.md \
  plugin/skills/learn/phase-prevent.md \
  plugin/concepts/skill-benchmarking.md \
  plugin/agents/skill-analyzer-agent.md \
  plugin/agents/skill-grader-agent.md
git commit -m "refactor: rename skill-builder references to instruction-builder"
```

Update index.json in the same commit (status: closed, progress: 100%).
