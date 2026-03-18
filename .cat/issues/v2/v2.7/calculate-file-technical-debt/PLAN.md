# Plan: calculate-file-technical-debt

## Goal
Implement a jlink-bundled Java tool that calculates the technical debt score for a single file, applying the metric
defined in `define-technical-debt-metric`.

## Parent Requirements
- REQ-002

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Parsing accuracy for different file types (Java, Bash, Markdown)
- **Mitigation:** Start with Java files, extend to other types incrementally

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/TechnicalDebtCalculator.java` - Core calculation logic
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/TechnicalDebtCalculatorTest.java` - Tests
- `client/build-jlink.sh` - Register `technical-debt-calculator` launcher

## Pre-conditions
- [ ] `define-technical-debt-metric` is closed (metric definition finalized)

## Sub-Agent Waves

### Wave 1
1. Read the metric definition from `plugin/concepts/technical-debt-metric.md`
2. Write failing tests for per-file debt calculation (TDD):
   - Test scoring for a clean file (low debt)
   - Test scoring for a file with known debt signals (high complexity, TODOs, duplication)
   - Test output format (JSON with score, grade, signal breakdown)
3. Implement `TechnicalDebtCalculator.java`:
   - Accept file path as input
   - Analyze signals defined in the metric
   - Output JSON: `{"file": "path", "score": 0.35, "grade": "C", "signals": {...}}`
4. Register `technical-debt-calculator` launcher in `build-jlink.sh`
5. Run all tests: `mvn -f client/pom.xml test`
6. Rebuild jlink image: `mvn -f client/pom.xml verify`

## Post-conditions
- [ ] `TechnicalDebtCalculator.java` exists and compiles
- [ ] All tests pass
- [ ] `technical-debt-calculator` launcher registered in `build-jlink.sh`
- [ ] Tool accepts a file path and outputs JSON with score, grade, and signal breakdown
- [ ] `mvn -f client/pom.xml verify` exits 0
