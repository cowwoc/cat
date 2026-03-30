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
