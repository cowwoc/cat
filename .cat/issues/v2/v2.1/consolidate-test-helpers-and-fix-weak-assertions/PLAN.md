# Plan: Consolidate Test Helpers and Fix Weak Assertions

## Goal
Eliminate duplicate test helper methods by moving them into `TestUtils`, and strengthen or remove tests that don't
validate meaningful business logic.

## Satisfies
Code quality improvement identified during test audit.

## Background

An audit of the 127 test files in `client/src/test/` revealed:

1. **Duplicate `createTempDir()` methods** — Four files define identical methods that wrap
   `Files.createTempDirectory()` with `WrappedCheckedException`:
   - `WriteAndCommitTest.java:32`
   - `ConfigTest.java:1140`
   - `LicenseValidatorTest.java:576`
   - `MergeAndCleanupTest.java:32`

2. **Duplicate `createTempProject()` methods** — Four files define near-identical methods that create a temp directory
   with `.cat/` structure:
   - `IssueDiscoveryTest.java:1316` (creates `.cat/issues/`)
   - `IssueLockTest.java:1013` (creates just a temp dir)
   - `IssueLockCliTest.java:374` (creates `.cat/`)
   - `GetIssueCompleteOutputTest.java:714` (creates `.cat/issues/`)

3. **Weak `toJson` serialization tests** — Tests in `IssueLockTest` (lines 657-738), `ExistingWorkCheckerTest`
   (lines 242-270), and `GetSubagentStatusOutputTest` (lines 331-336) only verify that JSON output `contains()` field
   names. They don't validate field values, structure, or roundtrip correctness — they essentially test that Jackson
   works.

4. **Missing `expectedExceptionsMessageRegExp`** — Several tests use `@Test(expectedExceptions = ...)` without a
   message pattern, violating the convention in `java.md` that both exception type AND message should be validated.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Refactoring test helpers could break tests if method signatures change
- **Mitigation:** Each change is mechanical; run full test suite after each step

## Constraints
- Per `java.md` rule #5: "No TestBase classes — each test method must inline its own setup." All consolidation must use
  static utility methods in `TestUtils`, not base classes or `@BeforeMethod` setup.

## Files to Modify

### Step 1: Add `createTempDir()` to TestUtils
- `client/src/test/java/.../test/TestUtils.java` — Add `public static Path createTempDir(String prefix)`
- `client/src/test/java/.../test/WriteAndCommitTest.java` — Remove private method, use `TestUtils.createTempDir()`
- `client/src/test/java/.../test/ConfigTest.java` — Remove private method, use `TestUtils.createTempDir()`
- `client/src/test/java/.../test/LicenseValidatorTest.java` — Remove private method, use `TestUtils.createTempDir()`
- `client/src/test/java/.../test/MergeAndCleanupTest.java` — Remove private method, use `TestUtils.createTempDir()`

### Step 2: Add `createTempCatProject()` to TestUtils
- `client/src/test/java/.../test/TestUtils.java` — Add `public static Path createTempCatProject(String prefix)`
  (creates `.cat/issues/` structure)
- `client/src/test/java/.../test/IssueDiscoveryTest.java` — Remove private method, use `TestUtils.createTempCatProject()`
- `client/src/test/java/.../test/IssueLockCliTest.java` — Remove private method, use `TestUtils.createTempCatProject()`
- `client/src/test/java/.../test/GetIssueCompleteOutputTest.java` — Remove private method, use
  `TestUtils.createTempCatProject()`

### Step 3: Strengthen weak toJson tests
- `client/src/test/java/.../test/IssueLockTest.java` — Replace 4 weak `toJson` tests with roundtrip or exact-value
  assertions
- `client/src/test/java/.../test/ExistingWorkCheckerTest.java` — Strengthen 2 `toJson` tests
- `client/src/test/java/.../test/GetSubagentStatusOutputTest.java` — Strengthen 1 `toJson` test

### Step 4: Add `expectedExceptionsMessageRegExp` to exception tests
- Audit all `@Test(expectedExceptions = ...)` without `expectedExceptionsMessageRegExp` and add message patterns

## Post-Conditions
- [ ] No duplicate `createTempDir()` or `createTempProject()` methods exist outside `TestUtils`
- [ ] All `toJson` tests validate actual field values, not just field name presence
- [ ] All `@Test(expectedExceptions = ...)` include `expectedExceptionsMessageRegExp`
- [ ] `mvn -f client/pom.xml verify` passes with zero failures

## Fix Steps (Iteration 1)

### Fix Step 5: Remove `IssueLockTest.createTempProject()` and migrate to `TestUtils.createTempDir()`

The Background section identified `IssueLockTest.java:1013` as having a `createTempProject()` method, but Step 2
excluded it because it only creates a plain temp directory (not `.cat/issues/` structure). Its body is
functionally identical to the `createTempDir()` already added to `TestUtils` in Step 1. The method name
`createTempProject` is misleading — it should be removed and all call sites updated to use
`TestUtils.createTempDir("issue-lock-test")`.

- `client/src/test/java/.../hooks/test/IssueLockTest.java` — Replace all calls to `createTempProject()` with
  `TestUtils.createTempDir("issue-lock-test")` and remove the private `createTempProject()` method.

### Fix Step 6: Add `expectedExceptionsMessageRegExp` to all remaining exception tests

Step 4 audited and fixed only `HookEntryPointTest` (8 tests). The post-condition requires ALL
`@Test(expectedExceptions = ...)` tests project-wide to include `expectedExceptionsMessageRegExp`. An estimated 327
additional tests across `client/src/test/` still lack this attribute.

- Audit all `@Test(expectedExceptions = ...)` annotations in `client/src/test/` that do not already have
  `expectedExceptionsMessageRegExp`.
- For each such test, inspect the production code to determine the actual exception message thrown and add a
  `expectedExceptionsMessageRegExp` pattern that matches it.
- Run `mvn -f client/pom.xml verify` after each file to confirm no regressions.
