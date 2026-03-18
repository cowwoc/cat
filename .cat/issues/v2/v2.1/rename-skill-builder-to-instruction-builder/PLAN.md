# rename-skill-builder-to-instruction-builder

## Goal

Replace all remaining references to the old name "skill-builder" with "instruction-builder" across
active plugin source files.

## Why

The skill was renamed from `skill-builder` to `instruction-builder` but stale references remain in
plugin skill files and concept docs. These stale names create confusion for users reading the
documentation.

## Scope

Files to update (all under `/workspace/plugin/`):

- `plugin/skills/work-merge-agent/first-use.md` — 8 references (invocation instructions say `cat:skill-builder`)
- `plugin/skills/work-with-issue-agent/first-use.md` — 1 reference
- `plugin/skills/instruction-builder-agent/first-use.md` — 2 self-references
- `plugin/skills/instruction-builder-agent/skill-conventions.md` — 1 reference
- `plugin/skills/instruction-builder-agent/validation-protocol.md` — 1 reference
- `plugin/skills/instruction-builder-agent/compression-protocol.md` — 1 reference
- `plugin/skills/learn/phase-investigate.md` — 3 references
- `plugin/skills/learn/phase-prevent.md` — 4 references
- `plugin/concepts/skill-benchmarking.md` — 5 references
- `plugin/agents/skill-analyzer-agent/SKILL.md` — 2 references
- `plugin/agents/skill-grader-agent/SKILL.md` — 1 reference

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

## Acceptance Criteria

- [ ] `grep -r "skill-builder" plugin/` returns zero matches (excluding any legitimate compound uses
  unrelated to the skill name)
- [ ] All modified files still render correctly (no broken references)
- [ ] Commit message: `refactor: rename skill-builder references to instruction-builder`
