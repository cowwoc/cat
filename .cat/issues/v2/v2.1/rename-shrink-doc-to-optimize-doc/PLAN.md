# Plan: rename-shrink-doc-to-optimize-doc

## Goal
Rename all `shrink-doc` references to `optimize-doc` across live plugin code, concepts, active issues, and test files.
Closed issues are left untouched (historical records).

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Skill registration name change breaks existing sessions until plugin cache is rebuilt; references in
  closed issues left stale could confuse future readers
- **Mitigation:** Rebuild jlink bundle after rename; add migration note to CHANGELOG; closed issues retain historical
  names by convention

## Files to Modify

### Skill Directories (rename)
- `plugin/skills/shrink-doc-agent/` → `plugin/skills/optimize-doc-agent/`
- `plugin/skills/shrink-doc/` → `plugin/skills/optimize-doc/`

### Plugin References (update content)
- `plugin/skills/compare-docs-agent/first-use.md` — update shrink-doc references
- `plugin/skills/compare-docs-agent/SKILL.md` — update description if it references shrink-doc
- `plugin/skills/work-with-issue-agent/first-use.md` — update shrink-doc references
- `plugin/skills/learn/documentation-priming.md` — update references
- `plugin/skills/learn/phase-prevent.md` — update references
- `plugin/skills/delegate-agent/first-use.md` — update references
- `plugin/skills/add/first-use.md` — update references

### Concept References (update content)
- `plugin/concepts/error-handling.md` — update references
- `plugin/concepts/subagent-delegation.md` — update references
- `plugin/concepts/work.md` — update references

### Java/Test References (update content)
- `client/src/main/java/` — grep and update any shrink-doc string references
- `tests/eval/` — update skill inventory and test cases

### Project References (update content)
- `CATALOG.md` — update skill listing
- `CHANGELOG.md` — add rename note

### Active Issue References (update content)
- `.cat/issues/v2/v2.1/shrink-doc-token-metrics/PLAN.md` — update references
- `.cat/issues/v2/v2.1/optimize-shrink-doc-workflow/PLAN.md` — update references
- `.cat/issues/v2/v2.3/compress-*/PLAN.md` — update references in active compression issues
- `.cat/issues/v2/v2.1/STATE.md` — update pending issue names

### Session Instructions (update content)
- `client/src/main/java/.../InjectSessionInstructions.java` — update if it references shrink-doc

## Pre-conditions
None

## Sub-Agent Waves

### Wave 1: Rename Skill Directories
- `git mv plugin/skills/shrink-doc-agent/ plugin/skills/optimize-doc-agent/`
- `git mv plugin/skills/shrink-doc/ plugin/skills/optimize-doc/`
- Update internal SKILL.md references in both directories to use new skill-loader name
- Files: skill directories

### Wave 2: Update Live Plugin References
- Find-and-replace `shrink-doc` → `optimize-doc` in all plugin skill files, concept files, and Java source
- Exclude closed issue directories (`.cat/issues/` files under Issues Completed)
- Files: all files listed in Plugin References, Concept References, Java/Test References sections

### Wave 3: Update Active Issues and Project Files
- Update STATE.md pending issue names
- Update active issue PLAN.md files that reference shrink-doc
- Update CATALOG.md
- Add rename entry to CHANGELOG.md
- Files: STATE.md, active PLAN.md files, CATALOG.md, CHANGELOG.md

### Wave 4: Verification
- `grep -r "shrink-doc" plugin/` should return zero matches
- `grep -r "shrink-doc" plugin/concepts/` should return zero matches
- `grep -r "shrink-doc" CATALOG.md` should return zero matches
- `mvn -f client/pom.xml test` exits 0
- Files: (verification only)

## Post-conditions
- [ ] No `shrink-doc` references remain in `plugin/` directory tree
- [ ] No `shrink-doc` references remain in `plugin/concepts/`
- [ ] No `shrink-doc` references remain in active (pending) issue PLAN.md files
- [ ] Skill directories renamed: `optimize-doc-agent/`, `optimize-doc/`
- [ ] CATALOG.md updated with new skill name
- [ ] CHANGELOG.md documents the rename
- [ ] Closed issues are NOT modified
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions
