## Goal

Integrate automated effort-based violation scanning into the `cat:work-verify` workflow so that
pre-existing cross-cutting rule violations in modified files are detected and reported as verification
failures, not just advisory warnings.

## Type

feature

## Context

The `plugin/concepts/work.md` document was updated (as part of issue 2.1-fix-record-learning-error-diagnosis)
to instruct developers to "actively scan for cross-cutting rule violations in surrounding code when editing any
file." However, this guidance is manual and advisory — whether it is followed depends entirely on the developer.

The cross-cutting rules that should be scanned for include:
- **Fail-fast violations**: Silent fallbacks, null returns for configuration errors, catch blocks that suppress
  errors without failing
- **Error message quality**: Missing source location, missing "what failed", missing "how to fix" context
- **Exception type specificity**: Catching `Exception` or `RuntimeException` where a specific type is more accurate
- **Comment flags**: Comments like "// fallback if X fails" or "// FIXME" in production code are red flags

Without automated enforcement, developers (including AI agents) will continue to miss pre-existing violations
even when they are in plain sight in the files being edited.

## Goal

After this issue is implemented:
1. The `cat:work-verify` subagent scans modified files for pre-existing cross-cutting rule violations
2. Scan depth is controlled by the `effort` config value:
   - `low`: Skip violation scanning entirely
   - `medium`: Scan the diff of changed files only (new and modified lines)
   - `high`: Scan changed files plus a configurable number of surrounding lines for context
3. Detected violations are reported as `INCOMPLETE` verification criteria (not warnings), causing
   the verify step to fail and requiring the fix iteration loop to address them
4. `plugin/concepts/work.md` is updated to note that automated enforcement exists and references
   the verify workflow

## Post-conditions

- [ ] `cat:work-verify` subagent includes a violation scanning step that runs after standard post-condition checks
- [ ] Scan depth is parameterized by the `effort` value from `.cat/config.json`
- [ ] At least the following violation patterns are detected:
  - Silent fallback in catch block (catch that swallows exception and returns a default value)
  - Null return from a method that should throw on error
  - Comment flags indicating known issues (`// FIXME`, `// TODO: fix`, `// fallback`, `// workaround`)
- [ ] When violations are found, they appear as `Missing` criteria in the verify output
- [ ] When no violations are found, scanning completes silently (no output noise)
- [ ] `plugin/concepts/work.md` §"When editing any file, actively scan..." is updated to reference automated enforcement
- [ ] At least one unit test validates violation detection for each pattern

## Sub-Agent Waves

### Wave 1

1. Design the violation scanner interface: create a `ViolationScanner` class in
   `client/src/main/java/io/github/cowwoc/cat/hooks/util/` that accepts a list of changed file paths
   and an effort level, and returns a list of `ViolationFinding` records
2. Implement the three required detection patterns: silent-fallback catch, null-return-on-error, comment-flag
3. Write unit tests for each detection pattern with both positive and negative cases
4. Integrate the scanner call into the `cat:work-verify` subagent skill: after post-condition checks,
   invoke the scanner and translate `ViolationFinding` results into INCOMPLETE criteria entries
5. Update `plugin/concepts/work.md` to note that the automated scanner enforces violation detection
   during `cat:work` verify phase, and cross-reference the effort setting
