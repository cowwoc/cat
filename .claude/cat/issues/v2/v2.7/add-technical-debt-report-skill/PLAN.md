# Plan: add-technical-debt-report-skill

## Goal
Create a `/cat:tech-debt` skill that produces a formatted technical debt report at any scope (file, directory, module,
product), surfacing the worst offenders and providing actionable guidance.

## Satisfies
- REQ-004
- REQ-005 (partial - lays groundwork for workflow feedback by exposing debt data)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Report formatting for large codebases; balancing detail vs. noise
- **Mitigation:** Default to summary view with `--detail` flag for full breakdown; show top-N worst files

## Files to Modify
- `plugin/skills/tech-debt-agent/SKILL.md` - Skill definition
- `plugin/skills/tech-debt-agent/first-use.md` - Detailed instructions on first load
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/TechnicalDebtReporter.java` - Report formatting (Java)
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/TechnicalDebtReporterTest.java` - Tests
- `client/build-jlink.sh` - Register `technical-debt-reporter` launcher

## Pre-conditions
- [ ] `aggregate-technical-debt` is closed (aggregation working)

## Execution Waves

### Wave 1
1. Write failing tests for report formatting (TDD):
   - Test summary report (product-level score, grade, top-5 worst files)
   - Test detailed report (full hierarchical breakdown)
   - Test single-file report
   - Test output as formatted box (consistent with CAT display conventions)
2. Implement `TechnicalDebtReporter.java`:
   - Accept `--scope <path>` and `--detail` flags
   - Use `TechnicalDebtAggregator` for data
   - Format output as a CAT display box with scores, grades, and hotspot list
3. Register `technical-debt-reporter` launcher in `build-jlink.sh`

### Wave 2
4. Create `plugin/skills/tech-debt-agent/SKILL.md` skill definition
5. Create `plugin/skills/tech-debt-agent/first-use.md` with usage instructions
6. Run all tests: `mvn -f client/pom.xml test`
7. Rebuild jlink image: `mvn -f client/pom.xml verify`

## Post-conditions
- [ ] `/cat:tech-debt` skill registered and invocable
- [ ] Report shows: overall score/grade, top-N worst files, per-scope breakdown
- [ ] `--detail` flag produces full hierarchical report
- [ ] All tests pass
- [ ] `mvn -f client/pom.xml verify` exits 0
