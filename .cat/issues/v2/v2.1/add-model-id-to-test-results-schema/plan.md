# Plan

## Goal

Add fully-qualified model ID to test-results schema so cached SPRT results are invalidated when the
model changes.

## Pre-conditions

(none)

## Research Findings

### Current State

1. **Skills results.json** (`plugin/tests/skills/*/first-use/results.json`): Has a `model` field but stores
   short names like `"haiku"` instead of fully-qualified IDs like `"claude-haiku-4-5-20251001"`. The schema
   documentation (`plugin/concepts/skill-test-results.md`) already specifies `"claude-sonnet-4-6"` as the
   expected format, but the code does not comply.

2. **Instruction-test.json** (`plugin/skills/*/instruction-test/instruction-test.json`): Written by
   `InstructionTestRunner.persistArtifacts()`. Contains `session_id`, `phase`, `timestamp`, `skill` (path +
   sha256), `test_cases` (path + sha256) — but NO model field at all.

3. **Rules test-results.json** (`plugin/skills/*/test/test-results.json` and
   `plugin/tests/rules/*/test-results.json`): Contains `sprt.session_id` and `sprt.test_cases[]` with SPRT
   data — but NO model field at all.

4. **Model extraction**: `InstructionTestRunner.extractModel()` reads the `model:` field from SKILL.md
   frontmatter and returns short names (`"haiku"`, `"sonnet"`, `"opus"`). Falls back to `"haiku"` if absent.

5. **No staleness detection**: No existing logic compares stored model against current model to detect stale
   cached results.

### Approach: Model ID Resolution

Short model names from SKILL.md frontmatter must be resolved to fully-qualified model IDs. Create a
`ModelIdResolver` utility class in Java that maps short names to their fully-qualified equivalents:
- `haiku` → `claude-haiku-4-5-20251001`
- `sonnet` → `claude-sonnet-4-6`
- `opus` → `claude-opus-4-6`

This mapping is maintained as a static map in Java, easily updated when model versions change.

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Existing cached results.json files will have short model names; changing to fully-qualified
  IDs means all existing cached results become stale (model mismatch). This is the desired behavior — it
  forces re-validation after model changes.
- **Mitigation:** The migration is intentional. Existing results with short names will naturally trigger
  re-validation when compared against the new fully-qualified format.

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ModelIdResolver.java` — NEW: model name resolution utility
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java` — update `extractModel()` to return fully-qualified IDs; update `persistArtifacts()` to write model_id; update `mergeResults()` and `init-sprt` to handle model staleness
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ModelIdResolverTest.java` — NEW: tests for model resolution
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/InstructionTestRunnerModelIdTest.java` — NEW: tests for model ID in results
- `plugin/concepts/skill-test-results.md` — update schema documentation to include model_id field and staleness semantics

## Jobs

### Job 1

- Create `ModelIdResolver.java` in `client/src/main/java/io/github/cowwoc/cat/hooks/skills/`:
  - A final class with a static method `resolve(String shortName)` that maps short model names to fully-qualified model IDs
  - Mapping: `haiku` → `claude-haiku-4-5-20251001`, `sonnet` → `claude-sonnet-4-6`, `opus` → `claude-opus-4-6`
  - Case-insensitive matching on the short name
  - Throws `IllegalArgumentException` for unknown model names
  - Include proper Javadoc, license header, and validation per project conventions
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ModelIdResolver.java`

- Update `InstructionTestRunner.extractModel()` method to resolve the short model name to a fully-qualified model ID by calling `ModelIdResolver.resolve()` on the value read from SKILL.md frontmatter
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`

- Update `InstructionTestRunner.persistArtifacts()` to include a `"model_id"` field in the instruction-test.json output:
  - Add a new argument to `persistArtifacts()` for the model_id, OR extract it from the skill path (read SKILL.md frontmatter and resolve)
  - Write `root.put("model_id", modelId)` in the JSON output alongside `session_id`, `phase`, `timestamp`
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`

- Update `InstructionTestRunner.mergeResults()` to check model staleness:
  - When reading prior instruction-test.json, extract the `model_id` field
  - Compare against the current model_id (resolved from skill frontmatter)
  - If model_id differs (or is absent in prior results), treat ALL prior results as stale — do not carry forward any SPRT states
  - Log a message: "Model changed from {prior} to {current}, invalidating cached SPRT results"
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`

- Update `InstructionTestRunner` init-sprt command to check model staleness:
  - When reading prior instruction-test results for carry-forward, check model_id
  - If prior model_id differs from current, skip carry-forward entirely (start fresh SPRT)
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/InstructionTestRunner.java`

- Create `ModelIdResolverTest.java` in `client/src/test/java/io/github/cowwoc/cat/hooks/test/`:
  - Test that `resolve("haiku")` returns `"claude-haiku-4-5-20251001"`
  - Test that `resolve("sonnet")` returns `"claude-sonnet-4-6"`
  - Test that `resolve("opus")` returns `"claude-opus-4-6"`
  - Test case-insensitive matching: `resolve("HAIKU")` returns `"claude-haiku-4-5-20251001"`
  - Test that unknown names throw `IllegalArgumentException`
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/ModelIdResolverTest.java`

- Update `plugin/concepts/skill-test-results.md` schema documentation:
  - Add `model_id` field to the top-level fields table (fully-qualified model identifier, e.g., `claude-haiku-4-5-20251001`)
  - Document the `model` field as the short name from SKILL.md frontmatter (for human reference)
  - Add a "Staleness Detection" section explaining that when `model_id` in cached results differs from the current model's fully-qualified ID, all cached SPRT results are invalidated
  - Update the example JSON to include a `model_id` field
  - Files: `plugin/concepts/skill-test-results.md`

- Update index.json to status: closed, progress: 100%
  - Files: `.cat/issues/v2/v2.1/add-model-id-to-test-results-schema/index.json`

- Run `mvn -f client/pom.xml verify -e` to verify all tests pass

## Post-conditions

- [ ] Both skills results.json and rules test-results.json schemas include a fully-qualified model ID field (e.g., claude-haiku-4-5-20251001)
- [ ] Tests passing for schema validation
- [ ] Mismatch between stored model ID and current model ID is detectable and triggers re-validation
- [ ] No regressions in existing test infrastructure
- [ ] E2E verification: store a test result with model ID, change the model, verify the cached result is flagged as stale
