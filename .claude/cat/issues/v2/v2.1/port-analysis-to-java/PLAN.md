# Plan: port-analysis-to-java

## Goal
Port session analysis and retrospective migration scripts to Java classes in the hooks module.

## Current State
Two Python scripts handle session efficiency analysis and retrospective data migration.

## Target State
Java classes replacing these scripts with equivalent functionality.

## Satisfies
Parent: port-utility-scripts

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** analyze-session.py is the largest script (445 lines) with complex logic
- **Mitigation:** Thorough test coverage; compare JSON output

## Scripts to Port
- `analyze-session.py` (445 lines) - Session efficiency analysis: token usage, tool distribution,
  pattern detection, optimization recommendations
- `migrate-retrospectives.py` (228 lines) - Retrospective data migration between format versions

## Files to Create
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java`
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/RetrospectiveMigrator.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/SessionAnalyzerTest.java`
- `hooks/src/test/java/io/github/cowwoc/cat/hooks/test/RetrospectiveMigratorTest.java`

## Execution Steps
1. Read analyze-session.py to understand analysis logic, metrics, and output format
2. Create `SessionAnalyzer` class porting all analysis logic
3. Read migrate-retrospectives.py to understand migration transformations
4. Create `RetrospectiveMigrator` class porting migration logic
5. Write tests for both classes with sample data
6. Run `mvn verify` to confirm all tests pass

## Success Criteria
- [ ] analyze-session.py replaced by SessionAnalyzer.java
- [ ] migrate-retrospectives.py replaced by RetrospectiveMigrator.java
- [ ] JSON output matches original scripts
- [ ] All tests pass (`mvn verify`)
