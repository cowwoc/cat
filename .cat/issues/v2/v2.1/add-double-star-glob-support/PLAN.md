# Plan: add-double-star-glob-support

## Goal
Add `**` glob support to the glob-to-regex conversion in `WorkPrepare.java` so that patterns like
`src/**/Foo.java` match files with zero or more intermediate directory segments. Also add an explanatory
comment to the code block that performs the glob-to-regex conversion.

## Parent Requirements
None

## Approaches

### A: Split on `**` first, then split remainder on `*`
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Before splitting on `*`, detect `**` segments. Replace `**` with a
  special sentinel, split on remaining `*`, then replace the sentinel with the multi-segment
  regex `(?:[^/]+/)*` (zero or more directory segments with trailing slash) combined with
  proper separator handling. This avoids double-slash when `**` is adjacent to `/`.

### B: Replace `**` with a placeholder, then split uniformly on `*`
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Replace `**` in the glob string with a unique placeholder before the
  `split("\\*", -1)` call. After splitting on `*`, the placeholder part gets `Pattern.quote()`d
  and then the placeholder is replaced back with the multi-segment sub-pattern. However
  this complicates the logic because `Pattern.quote()` would escape the placeholder itself.

### C: Regex-based token iteration (chosen)
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Use a `Matcher` over the glob string to consume it token-by-token:
  `**`, `*`, or a run of literal characters. For each token emit the correct regex fragment.
  This cleanly handles all combinations and is straightforward to understand. Chosen because
  it makes the `**` vs `*` distinction explicit and avoids the separator-doubling problem by
  construction (path separators adjacent to `**` tokens are consumed as literal parts).

> Approach C is selected. It is the most explicit: each glob token maps directly to a regex
> fragment without requiring sentinel replacement or post-processing hacks. The separator
> problem (`src/**/Foo.java` → must not generate `src//Foo.java`) is solved naturally by
> treating `/` as a literal that gets `Pattern.quote()`d into `\Q/\E`, not by special-casing
> the separator.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Existing `*` tests rely on the current `split("\\*")` implementation; switching
  to token-based iteration must produce identical output for all `*`-only patterns.
- **Mitigation:** The existing `globToRegexHandlesMetacharacters` test and the new unit tests
  for `*` regressions must all pass before merging.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — replace the
  inline glob-to-regex block (lines ~1369-1376) with token-based iteration and add explanatory
  comment
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java` — add unit
  tests for `**` with zero, one, and multiple path segments; add regression tests confirming
  `*` behavior is unchanged

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Invoke `cat:tdd-implementation-agent` to write failing tests for `**` glob support in
  `WorkPrepareTest.java`, then implement the token-based glob-to-regex conversion in
  `WorkPrepare.java`, add the explanatory comment, and run `mvn -f client/pom.xml test` to
  confirm all tests pass. Update STATE.md to 100% on success.
  - Test file: `client/src/test/java/io/github/cowwoc/cat/hooks/test/WorkPrepareTest.java`
  - Implementation file: `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`

  **Detailed implementation instructions for the sub-agent:**

  #### A. Locate the existing glob-to-regex block

  In `WorkPrepare.java`, find the block around lines 1369-1376:

  ```java
  if (plannedFile.contains("*"))
  {
    String[] parts = plannedFile.split("\\*", -1);
    StringJoiner regexJoiner = new StringJoiner("[^/]*");
    for (String part : parts)
      regexJoiner.add(Pattern.quote(part));
    String regexPattern = regexJoiner.toString();
    globPatterns.put(plannedFile, Pattern.compile(".*" + regexPattern));
  }
  ```

  #### B. Replace it with the following token-based implementation

  Add an explanatory block comment immediately before the replacement code:

  ```java
  // Glob-to-regex conversion:
  //   * matches any sequence of characters except path separators (converted to [^/]*)
  //   ** matches zero or more complete path segments, including their separators
  //      (converted to (?:[^/]+/)* for intermediate segments)
  //   All literal text between wildcards is quoted with Pattern.quote() so characters
  //   like "[", "]", ".", and "(" are treated as literals, not regex syntax.
  ```

  Replace the existing block with:

  ```java
  if (plannedFile.contains("*"))
  {
    // Glob-to-regex conversion:
    //   * matches any sequence of characters except path separators (converted to [^/]*)
    //   ** matches zero or more complete path segments, including their separators
    //      (converted to (?:[^/]+/)* for intermediate segments)
    //   All literal text between wildcards is quoted with Pattern.quote() so characters
    //   like "[", "]", ".", and "(" are treated as literals, not regex syntax.
    StringBuilder regexBuilder = new StringBuilder(".*");
    Matcher tokenMatcher = GLOB_TOKEN_PATTERN.matcher(plannedFile);
    while (tokenMatcher.find())
    {
      String token = tokenMatcher.group();
      if (token.equals("**"))
        regexBuilder.append("(?:[^/]+/)*");
      else if (token.equals("*"))
        regexBuilder.append("[^/]*");
      else
        regexBuilder.append(Pattern.quote(token));
    }
    globPatterns.put(plannedFile, Pattern.compile(regexBuilder.toString()));
  }
  ```

  #### C. Add the static Pattern field for GLOB_TOKEN_PATTERN

  In the static fields section of `WorkPrepare.java`, add (keeping the existing Pattern fields
  together):

  ```java
  /**
   * Tokenizes a glob pattern into {@code **}, {@code *}, or literal text segments.
   */
  private static final Pattern GLOB_TOKEN_PATTERN = Pattern.compile("\\*\\*|\\*|[^*]+");
  ```

  #### D. Tests to add in WorkPrepareTest.java

  Add the following test methods (each must be self-contained, use a temp git repo, and test
  the full `execute()` flow so that file-overlap detection exercises the glob-to-regex path):

  **Test 1: doubleStarGlobMatchesZeroIntermediateSegments**
  - PLAN.md "Files to Modify" contains `src/**/Foo.java`
  - A commit on the base branch touches `src/Foo.java` (zero intermediates)
  - Assert: `suspicious_commits` is non-empty (the commit is flagged)

  **Test 2: doubleStarGlobMatchesOneIntermediateSegment**
  - PLAN.md "Files to Modify" contains `src/**/Foo.java`
  - A commit on the base branch touches `src/sub/Foo.java` (one intermediate)
  - Assert: `suspicious_commits` is non-empty

  **Test 3: doubleStarGlobMatchesMultipleIntermediateSegments**
  - PLAN.md "Files to Modify" contains `src/**/Foo.java`
  - A commit on the base branch touches `src/a/b/c/Foo.java` (multiple intermediates)
  - Assert: `suspicious_commits` is non-empty

  **Test 4: doubleStarGlobDoesNotMatchUnrelatedFile**
  - PLAN.md "Files to Modify" contains `src/**/Foo.java`
  - A commit on the base branch touches `src/Bar.java` (different filename)
  - Assert: `suspicious_commits` is empty (no false positive)

  **Test 5: singleStarGlobRegressionUnchanged**
  - PLAN.md "Files to Modify" contains `plugin/agents/stakeholder-*.md`
  - A commit on the base branch touches `plugin/agents/stakeholder-concern-box-agent.md`
  - Assert: `suspicious_commits` is non-empty (existing `*` behavior preserved)

  Each test must follow the same setup pattern as `detectsSuspiciousCommitsOnTargetBranch`
  (create temp git repo, create issue, add a commit that touches the relevant file, call
  `WorkPrepare.execute()`, parse JSON result, assert `potentially_complete` or
  `suspicious_commits`).

## Post-conditions
- [ ] `**` glob matches zero or more intermediate directory segments (e.g., `src/**/Foo.java`
  matches `src/Foo.java`, `src/sub/Foo.java`, and `src/a/b/c/Foo.java`)
- [ ] The glob-to-regex conversion for `**` correctly handles path separators so that
  `src/**/Foo.java` generates a regex matching `src/Foo.java` (separator consumed, not doubled)
- [ ] Explanatory comment added before the glob-to-regex code block in `WorkPrepare.java`
- [ ] Unit tests added to `WorkPrepareTest.java` covering `**` with zero, one, and multiple
  path segments, plus a non-matching case and a single-star regression test
- [ ] `mvn -f client/pom.xml test` exits 0 with all tests passing (no regressions)
- [ ] E2E: a work plan containing `**` glob patterns correctly identifies matching files
