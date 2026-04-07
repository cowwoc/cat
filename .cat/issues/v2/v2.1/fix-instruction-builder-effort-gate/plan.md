# Plan

## Goal

Fix instruction-builder-agent effort gate to read curiosity instead of effort

## Research Findings

Three locations use stale `effort` terminology after the `rename-config-options` issue renamed `effort` → `curiosity`:

1. `plugin/skills/instruction-builder-agent/SKILL.md` line 5: `effort: high` (should be `curiosity: high`)
2. `plugin/tests/skills/instruction-builder-agent/first-use/step43-sprt-runs-when-effort-not-low.md`:
   - Line 10: "The effort level is medium." (should be "The curiosity level is medium.")
   - Line 16: "the effort level being medium" (should be "the curiosity level being medium")
   - File should be renamed to `step43-sprt-runs-when-curiosity-not-low.md`
3. `plugin/scripts/validate-plan-builder-review-loop.sh` line 57:
   `fail "first-use.md is missing effort gate keyword 'low'"` (should say "curiosity gate keyword")

`first-use.md` already correctly reads `curiosity` from the config via `get-config-output effective` — no change needed there.

No Java code processes the `effort:` frontmatter key from SKILL.md (GetSkill.java only reads first-use.md).

A Java regression test should validate that the SPRT test file for instruction-builder-agent uses `curiosity` terminology (not `effort`) in its prompts and assertions. The best place is a new test that reads the file and asserts the correct terminology, similar to how other tests validate file content conventions.

## Pre-conditions

(none)

## Post-conditions

- [ ] Bug fixed: instruction-builder-agent reads `curiosity` config key (not `effort`) for its effort gate
- [ ] Regression test added: test verifies effort gate reads `curiosity`
- [ ] No new issues introduced
- [ ] E2E verification: run instruction-builder-agent and confirm it proceeds with full workflow when `curiosity` is set to non-low value

## Jobs

### Job 1

- In `plugin/skills/instruction-builder-agent/SKILL.md`, change `effort: high` to `curiosity: high` on line 5

- Rename `plugin/tests/skills/instruction-builder-agent/first-use/step43-sprt-runs-when-effort-not-low.md`
  to `step43-sprt-runs-when-curiosity-not-low.md` (use `git mv` to preserve history), then update content:
  - Line 10: change "The effort level is medium." → "The curiosity level is medium."
  - Line 16: change "the effort level being medium" → "the curiosity level being medium"

- In `plugin/scripts/validate-plan-builder-review-loop.sh`, change line 57:
  `fail "first-use.md is missing effort gate keyword 'low'"`
  → `fail "first-use.md is missing curiosity gate keyword 'low'"`

- Add a new Java test `InstructionBuilderCuriosityGateTest.java` in
  `client/src/test/java/io/github/cowwoc/cat/hooks/test/` that reads the SPRT test file
  `plugin/tests/skills/instruction-builder-agent/first-use/step43-sprt-runs-when-curiosity-not-low.md`
  from the plugin root (using `scope.getPluginRoot()`) and asserts:
  - The file contains "curiosity level" (not "effort level")
  - The file does NOT contain "effort level"
  Pattern: use `TestClaudeTool(tempDir, pluginRoot)` where pluginRoot is the actual plugin directory.
  Wait — `TestClaudeTool` takes injected paths, not the real plugin root. Instead, read the file path
  directly from the test resources or use the real plugin path.
  
  Actually: the simpler approach is to locate the SPRT file relative to the project root. Look at how
  `InjectMainAgentRulesTest.java` resolves plugin files to understand the pattern. The test should
  use `Path.of(System.getProperty("user.dir")).resolve(...)` or read the file from a test resource.
  
  **Revised approach for regression test:** Read the SPRT test file using the actual filesystem path
  relative to the Maven project's working directory. The test verifies file content, so it reads
  the file at a known relative path from `client/` directory. Use:
  ```java
  Path sprtTestFile = Path.of("..").resolve(
    "plugin/tests/skills/instruction-builder-agent/first-use/" +
    "step43-sprt-runs-when-curiosity-not-low.md").toAbsolutePath().normalize();
  ```
  Assert that the file content contains "curiosity level" and does not contain "effort level".

  **Note on test isolation:** This test reads a file from the project directory (not a temp dir),
  which is read-only. This is acceptable since no modification occurs — the test only reads.
  For validation-only tests where execution fails before any external operation, passing `"."` for
  git commands is acceptable since no git command actually runs (per java.md conventions).

- Commit all changes with message: `bugfix: fix instruction-builder-agent curiosity gate terminology`

- Run `mvn -f client/pom.xml verify -e` to verify build passes

- Update `index.json` status to `closed`
