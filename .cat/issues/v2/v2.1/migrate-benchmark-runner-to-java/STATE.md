# State

- **Status:** closed
- **Progress:** 100%
- **Resolution:** implemented (all 2503 tests passing)
- **Dependencies:** []
- **Blocks:** []
- **Target Branch:** v2.1

## Test Verification
Fixed TestUtils.getMapper() null parameter issue - all 2503 tests now pass.

## Review Concerns Addressed (Post-Merge)

All 9 stakeholder review concerns fixed in follow-up commit:
1. detect-changes test coverage (3 paths)
2. persist-artifacts test coverage (3 cases)
3. main() error handling compliance (HookOutput.block)
4. mergeResults argument usage (args[1] and args[2])
5+9. sha256Bytes helper and HexFormat optimization
6. ParsedSkill.bodyStartLine field to avoid second file read
7. initSprt Map optimization (O(1) lookups instead of O(n²))
8. StandardCopyOption import and usage

## Round 3 Stakeholder Concerns Addressed

All 8 concerns from Round 3 review fixed:
1. [HIGH] getGitOutput() correctness: Replaced with TestUtils.runGitCommandWithOutput() (checks exit codes)
2. [MEDIUM] persistArtifacts path construction: Replaced string concat with Path.resolve()
3. [MEDIUM] initSprtUsePriorBoostWithAcceptPrior test: Added test for --prior-boost with prior ACCEPT
4. [MEDIUM] 3 missing error-path tests: Added persistArtifactsRejectsCorruptBenchmarkJson, initSprtWithEmptyPrior, checkBoundaryWithZeroTestCases
5. [LOW] Javadoc fix at lines 54-55: Updated class-level Javadoc to describe actual HookOutput behavior
6. [LOW] System.err.println replacement: Changed to log.warn() in extractModel and persistArtifacts
7. [LOW] String.join+concat replacement: Changed detectChanges to use Files.write()
8. [LOW] SPRT documentation: Added math comments to 3 existing SPRT tests

## Inline Fixes (Pre-Merge)

Two HIGH concerns fixed inline instead of deferring to v2.2:
1. [HIGH] Design: git add exit code checking - Added explicit exit code checks for both git add commands in persistArtifacts() method
2. [HIGH] Design: Frontmatter parsing duplication - Refactored parseSkill() to delegate to SkillDiscovery.extractFrontmatter() instead of reimplementing boundary detection

## Post-Merge Cleanup

- Removed benchmark-runner.sh wrapper: Skills now call Java binary directly via ${CLAUDE_PLUGIN_ROOT}/client/bin/benchmark-runner
