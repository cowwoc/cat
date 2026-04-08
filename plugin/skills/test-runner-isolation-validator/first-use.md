<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Test Runner Isolation Validator

Validate test isolation by checking that tests don't share mutable state, don't interfere with concurrent runs, and properly clean up temporary resources.

## When to Use

Use this skill when:
- Setting up a new test suite and need to verify isolation patterns
- Debugging test failures that may result from shared state or concurrent interference
- Reviewing test code to ensure compliance with isolation requirements
- Implementing concurrent test execution and need pre-flight checks

## Arguments

| Format | Example | Behavior |
|--------|---------|----------|
| Directory path | `"client/src/test"` or `"/workspace/plugin/tests"` | Analyze test files in the specified directory |
| Empty/interactive | (no arguments) | Prompt for test directory path |

## Procedure

### Step 1: Gather Test Directory Context

If test_directory_path argument is empty, prompt interactively:

```
question: "Which test directory should I analyze for isolation issues?"
options:
  - "Java test directory (client/src/test/java)"
  - "Plugin test directory (plugin/tests)"
  - "Bash test directory (plugin/tests/bash)"
  - "Other location (specify path)"
```

Store result as `TEST_DIR_PATH` (absolute path).

If argument is provided, use it directly as `TEST_DIR_PATH`.

### Step 2: Verify Test Directory Exists

```bash
if [[ ! -d "${TEST_DIR_PATH}" ]]; then
  echo "ERROR: Test directory not found: ${TEST_DIR_PATH}"
  exit 1
fi
```

### Step 3: Analyze for Shared Mutable State

**Objective:** Detect patterns that indicate shared mutable state between tests (globals, static fields, shared file paths, singleton mutations).

**For Java tests**, search for anti-patterns:

```bash
cd "${TEST_DIR_PATH}" && find . -name "*.java" -type f | while read file; do
  # Check for static fields (except constants)
  if grep -E "^\s*private static\s+(?!final)" "$file" | grep -v "final"; then
    echo "FOUND: Mutable static field in $file"
  fi
  
  # Check for hardcoded file paths (not in temp directories)
  if grep -E '"/tmp/[a-zA-Z0-9_-]+"' "$file" | grep -v "mktemp"; then
    echo "FOUND: Hardcoded temp path in $file (should use mktemp)"
  fi
  
  # Check for singleton patterns without cleanup
  if grep -E "getInstance\(\)" "$file"; then
    echo "FOUND: Singleton usage in $file - verify cleanup between tests"
  fi
done
```

**For Bash tests**, search for anti-patterns:

```bash
cd "${TEST_DIR_PATH}" && find . -name "*.sh" -o -name "*.bats" | while read file; do
  # Check for hardcoded paths (not mktemp)
  if grep -E '/tmp/[a-zA-Z0-9_-]+' "$file" | grep -v 'mktemp'; then
    echo "FOUND: Hardcoded temp path in $file"
  fi
  
  # Check for global variable mutations
  if grep -E '^[A-Z_]+=.*' "$file" | grep -v 'readonly'; then
    echo "FOUND: Global variable mutation in $file"
  fi
  
  # Check for missing cleanup
  if grep -E 'mkdir|mktemp' "$file" && ! grep -E 'rm -rf|rm -r' "$file"; then
    echo "FOUND: No cleanup for created directories in $file"
  fi
done
```

Record findings as `SHARED_STATE_ISSUES` (list of file paths with issues).

### Step 4: Analyze for Concurrent Interference

**Objective:** Verify that concurrent test runs won't interfere (temp file uniqueness, lock isolation, process separation).

**Check for temp file uniqueness:**

```bash
# Look for mktemp usage and verify it includes session ID or UUID
cd "${TEST_DIR_PATH}" && find . \( -name "*.java" -o -name "*.sh" -o -name "*.bats" \) -type f | while read file; do
  if grep -E 'mktemp|/tmp/' "$file"; then
    # Verify temp paths include session ID or randomization
    if ! grep -E '\${CLAUDE_SESSION_ID}|\$RANDOM|UUID|mktemp' "$file"; then
      echo "FOUND: Temp path without session/random isolation in $file"
    fi
  fi
done
```

**Check for lock files:**

```bash
# Verify lock files include session ID
cd "${TEST_DIR_PATH}" && find . \( -name "*.java" -o -name "*.sh" -o -name "*.bats" \) -type f | while read file; do
  if grep -E '\.lock|lockfile' "$file"; then
    if ! grep -E '\${CLAUDE_SESSION_ID}|UUID' "$file"; then
      echo "FOUND: Lock file without session isolation in $file"
    fi
  fi
done
```

**Check for process isolation:**

```bash
# For Bash: verify background processes have per-test pids
cd "${TEST_DIR_PATH}" && find . -name "*.sh" -o -name "*.bats" | while read file; do
  if grep -E '&$' "$file"; then  # Background process
    if ! grep -E 'wait|trap.*kill|\$!' "$file"; then
      echo "FOUND: Background process without cleanup in $file"
    fi
  fi
done

# For Java: verify test isolation (no shared thread pools without cleanup)
cd "${TEST_DIR_PATH}" && find . -name "*.java" | while read file; do
  if grep -E 'ExecutorService|Executors\.newFixedThreadPool' "$file"; then
    if ! grep -E 'shutdown|awaitTermination' "$file"; then
      echo "FOUND: Unmanaged ExecutorService in $file"
    fi
  fi
done
```

Record findings as `CONCURRENT_ISSUES` (list of file paths with issues).

### Step 5: Analyze for Cleanup Compliance

**Objective:** Verify temporary files and resources are cleaned up after each test.

**For Java tests**, check for teardown/cleanup:

```bash
cd "${TEST_DIR_PATH}" && find . -name "*.java" -type f | while read file; do
  # Check for @After or @TearDown
  if grep -E '@Test' "$file"; then
    if ! grep -E '@After|@TearDown|tearDown\(\)|cleanup\(\)' "$file"; then
      echo "FOUND: Test class with @Test but no @After cleanup in $file"
    fi
  fi
  
  # Check for File.delete() or rm -rf calls in cleanup
  if grep -E '@After|@TearDown|tearDown|cleanup' "$file"; then
    if ! grep -E 'delete\(\)|deleteOnExit|rm -rf|Files\.delete' "$file"; then
      echo "FOUND: Cleanup method exists but may not delete resources in $file"
    fi
  fi
done
```

**For Bash tests**, check for cleanup:

```bash
cd "${TEST_DIR_PATH}" && find . -name "*.sh" -o -name "*.bats" | while read file; do
  # Check for trap cleanup
  if grep -E 'setup|setup_file' "$file"; then
    if ! grep -E 'trap.*cleanup|teardown' "$file"; then
      echo "FOUND: Setup exists but no trap cleanup in $file"
    fi
  fi
  
  # Check for explicit rm calls
  if grep -E 'mktemp|mkdir' "$file"; then
    if ! grep -E 'rm -rf' "$file"; then
      echo "FOUND: Temporary directory created but not deleted in $file"
    fi
  fi
done
```

Record findings as `CLEANUP_ISSUES` (list of file paths with issues).

### Step 6: Generate Comprehensive Report

Display isolation analysis results:

```
========================================
TEST ISOLATION VALIDATION REPORT
========================================

Test Directory: ${TEST_DIR_PATH}
Analyzed: $(find "${TEST_DIR_PATH}" -type f \( -name "*.java" -o -name "*.sh" -o -name "*.bats" \) | wc -l) test files

CATEGORY 1: SHARED MUTABLE STATE
$(if [[ -n "${SHARED_STATE_ISSUES}" ]]; then
  echo "❌ ISSUES FOUND:"
  echo "${SHARED_STATE_ISSUES}"
else
  echo "✅ No shared mutable state detected"
fi)

CATEGORY 2: CONCURRENT INTERFERENCE
$(if [[ -n "${CONCURRENT_ISSUES}" ]]; then
  echo "❌ ISSUES FOUND:"
  echo "${CONCURRENT_ISSUES}"
else
  echo "✅ Concurrent isolation appears safe"
fi)

CATEGORY 3: CLEANUP COMPLIANCE
$(if [[ -n "${CLEANUP_ISSUES}" ]]; then
  echo "❌ ISSUES FOUND:"
  echo "${CLEANUP_ISSUES}"
else
  echo "✅ Cleanup patterns look good"
fi)

OVERALL ISOLATION STATUS:
$(if [[ -z "${SHARED_STATE_ISSUES}${CONCURRENT_ISSUES}${CLEANUP_ISSUES}" ]]; then
  echo "✅ PASS - Tests appear properly isolated"
else
  echo "❌ FAIL - Isolation issues detected"
  echo "Remediation: See detailed findings above"
fi)
```

### Step 7: Provide Remediation Guidance

If issues were found, provide specific remediation for each category:

**For Shared Mutable State Issues:**
- Replace static fields with instance fields scoped to test lifecycle
- Use `@TempDir` annotation (Java) or `mktemp` (Bash) for file paths
- Avoid singleton patterns; use dependency injection with test-scoped instances
- Example: `mktemp -d` creates unique temp directory; store path in `TEMP_DIR` variable

**For Concurrent Interference Issues:**
- Always include session ID or random component in temp file paths: `${TEMP_DIR}/test-${CLAUDE_SESSION_ID}-${RANDOM}.txt`
- Lock files must include session ID: `.lock.${CLAUDE_SESSION_ID}`
- Background processes must be captured and cleaned: `(long_task &); PID=$!; trap "kill $PID" EXIT`
- Thread pools must be shutdown: `executor.shutdown(); executor.awaitTermination(10, TimeUnit.SECONDS);`

**For Cleanup Issues:**
- Add `@After` method to every test class that creates resources
- Bash tests: Add `trap 'rm -rf "${TEMP_DIR}"' EXIT` at top of test
- Use try-finally or try-with-resources for resource cleanup
- Verify cleanup removes all artifacts: temp files, directories, lock files, temp databases

### Step 8: Offer Next Steps

```
question: "What would you like to do next?"
options:
  - "Review detailed file-by-file breakdown"
  - "Generate fix templates for identified issues"
  - "Export report to file"
  - "Done - return to main workflow"
```

## Verification

- [ ] Test directory path was provided or gathered interactively
- [ ] Test directory exists and contains test files
- [ ] Shared mutable state patterns were searched (static fields, hardcoded paths, singletons)
- [ ] Concurrent interference patterns were checked (temp file uniqueness, locks, process isolation)
- [ ] Cleanup patterns were verified (@After, trap cleanup, explicit rm calls)
- [ ] Report was generated showing pass/fail for each category
- [ ] Remediation guidance was provided for any issues found
- [ ] User was offered next-step options

## Related Concepts

- Test isolation patterns in `plugin/rules/test-isolation.md`
- Temp file handling best practices in `plugin/rules/shell-efficiency.md`
- Multi-instance safety requirements in `plugin/rules/multi-instance-safety.md`
