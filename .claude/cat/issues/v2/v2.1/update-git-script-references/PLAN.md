# Plan: update-git-script-references

## Goal
Update skill files and concepts that reference bash git scripts to invoke the new Java equivalents instead.

## Satisfies
None - follow-up to 2.1-port-git-scripts infrastructure work

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Skills invoke scripts via bash commands; Java classes need different invocation patterns
- **Mitigation:** Update one skill at a time, verify each invocation pattern works

## Files to Modify
17 files reference the old bash scripts:

### Concepts (2)
- `plugin/concepts/git-operations.md`
- `plugin/concepts/merge-and-cleanup.md`

### Skills (10)
- `plugin/skills/work/anti-patterns.md`
- `plugin/skills/work/phase-review.md`
- `plugin/skills/work/phase-merge.md`
- `plugin/skills/skill-builder/content.md`
- `plugin/skills/work-merge/content.md`
- `plugin/skills/work-with-issue/content.md`
- `plugin/skills/write-and-commit/SKILL.md`
- `plugin/skills/write-and-commit/content.md`
- `plugin/skills/git-squash/SKILL.md`
- `plugin/skills/git-merge-linear/SKILL.md`

### Agents (3)
- `plugin/agents/work-merge.md`
- `plugin/agents/work-execute.md`
- `plugin/agents/README.md`

### Other (2)
- `plugin/concepts/work.md`
- `plugin/skills/learn/phase-investigate.md`

### Scripts to remove (4)
- `plugin/scripts/write-and-commit.sh`
- `plugin/scripts/git-merge-linear-optimized.sh`
- `plugin/scripts/git-squash-optimized.sh`
- `plugin/scripts/merge-and-cleanup.sh`

## Execution Steps
1. **Update each skill/concept** to reference Java class invocation instead of bash script
2. **Update invocation patterns** from `bash script.sh args` to `java -cp hooks.jar ClassName args`
3. **Remove the 4 bash scripts** from `plugin/scripts/`
4. **Run tests:** `mvn -f hooks/pom.xml test`

## Dependencies
- 2.1-port-git-scripts (must be merged first)

## Success Criteria
- [ ] All 17 files updated to reference Java classes
- [ ] 4 bash scripts removed
- [ ] All tests pass
