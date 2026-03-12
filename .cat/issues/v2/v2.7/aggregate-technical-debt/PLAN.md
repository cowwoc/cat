# Plan: aggregate-technical-debt

## Goal
Extend the technical debt calculator to aggregate scores across directories, modules, and the entire product, producing
a hierarchical debt summary.

## Parent Requirements
- REQ-003

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Large codebases may have slow scan times; aggregation formula needs to handle varying file counts fairly
- **Mitigation:** Use weighted averages (not simple averages) to prevent small directories from skewing scores;
  add `--scope` flag to limit scan depth

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/TechnicalDebtAggregator.java` - Aggregation logic
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/TechnicalDebtAggregatorTest.java` - Tests
- `client/build-jlink.sh` - Register `technical-debt-aggregator` launcher

## Pre-conditions
- [ ] `calculate-file-technical-debt` is closed (per-file calculation working)

## Execution Waves

### Wave 1
1. Write failing tests for aggregation (TDD):
   - Test directory-level aggregation (weighted average of file scores)
   - Test module-level aggregation (weighted average of directory scores)
   - Test product-level aggregation (all files)
   - Test `--scope` flag to limit to a directory subtree
   - Test output format: hierarchical JSON with scores at each level
2. Implement `TechnicalDebtAggregator.java`:
   - Accept `--scope <path>` (defaults to project root)
   - Scan all supported files under scope
   - Calculate per-file scores using `TechnicalDebtCalculator`
   - Aggregate by directory, module (top-level source directories), and product
   - Output hierarchical JSON with scores and grades at each level
3. Register `technical-debt-aggregator` launcher in `build-jlink.sh`
4. Run all tests: `mvn -f client/pom.xml test`
5. Rebuild jlink image: `mvn -f client/pom.xml verify`

## Post-conditions
- [ ] `TechnicalDebtAggregator.java` exists and compiles
- [ ] All tests pass
- [ ] Aggregation works at directory, module, and product scopes
- [ ] `--scope` flag limits analysis to a subtree
- [ ] `mvn -f client/pom.xml verify` exits 0
