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

- [ ] `## Enforcement` sections with ` ```cat-rules ` blocks are supported in `.claude/rules/*.md` convention files
- [ ] `.claude/rules/jackson.md` has cat-rules for `new ObjectMapper()` and `JsonMapper.builder()` usage
- [ ] `.claude/rules/common.md` has cat-rules for `\bjq\b` in shell scripts, `/workspace/` hardcoded paths in shell
  scripts, and comment flags (`FIXME`, `workaround`, `fallback`) in any file
- [ ] `plugin/agents/work-verify.md` implements three-step effort-based scanning using the cat-rules blocks
- [ ] At LOW effort scanning is skipped; MEDIUM greps diff lines; HIGH greps full changed file content
- [ ] LLM reviews grep hits with surrounding context; failures become Missing criteria in verify output
- [ ] `ViolationScanner.java`, `ViolationFinding.java`, `ViolationScannerTest.java` are removed
- [ ] `violation-scanner` entry removed from `client/build-jlink.sh` HANDLERS array
- [ ] `plugin/concepts/work.md` updated to reference the cat-rules enforcement mechanism

## Sub-Agent Waves

### Wave 1

1. Remove `ViolationScanner.java`, `ViolationFinding.java`, `ViolationScannerTest.java` from the worktree
2. Remove the `violation-scanner:util.ViolationScanner` entry from the HANDLERS array in `client/build-jlink.sh`
3. Add an `## Enforcement` section with cat-rules blocks to `.claude/rules/jackson.md`
4. Add an `## Enforcement` section with cat-rules blocks to `.claude/rules/common.md`
5. Rewrite the violation scanning section in `plugin/agents/work-verify.md` with the new LLM-assisted grep approach
6. Update `plugin/concepts/work.md` to reference the cat-rules enforcement mechanism
