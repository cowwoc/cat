# Plan: add-rca-method-analyzer-tool

## Goal

Add a jlink-bundled Java tool `rca-method-analyzer` that computes A/B test RCA method statistics
from `mistakes-YYYY-MM.json` files, replacing the `jq` command in the learn skill's milestone
review section. `jq` is not available in the plugin runtime per `.claude/rules/common.md`.

## Satisfies

None (infrastructure issue)

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** None — purely additive new tool, no existing behavior changed
- **Mitigation:** TestNG unit tests cover the analysis logic

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/util/RcaMethodAnalyzer.java` — new tool
- `client/src/test/java/io/github/cowwoc/cat/hooks/util/RcaMethodAnalyzerTest.java` — tests
- `client/build-jlink.sh` — register `rca-method-analyzer` launcher
- `plugin/skills/learn/first-use.md` — replace commented-out jq block with tool invocation

## Pre-conditions

- [ ] All dependent issues are closed

## Execution Steps

1. **Read existing jlink tool for reference pattern:**
   - Read `client/src/main/java/io/github/cowwoc/cat/hooks/util/SessionAnalyzer.java` to understand
     the CLI tool pattern (main method, arg parsing, output format)
   - Read `client/build-jlink.sh` to understand how to register a new launcher

2. **Write failing tests first (TDD):**
   - Create `client/src/test/java/io/github/cowwoc/cat/hooks/util/RcaMethodAnalyzerTest.java`
   - Test cases:
     - Groups mistakes by rca_method and computes count, recurrences, recurrence_rate
     - Filters mistakes by START_ID (includes mistakes with numeric ID >= START_ID)
     - Handles missing `rca_method` field (shows as "unassigned")
     - Handles empty input (returns empty array)
     - Handles month-split file format (`mistakes-YYYY-MM.json`)
   - Run `mvn -f client/pom.xml test` — tests must FAIL at this point

3. **Implement `RcaMethodAnalyzer.java`:**
   - `main(String[] args)`: accepts `--start-id N` optional arg (default 0), reads all
     `mistakes-YYYY-MM.json` files from `.claude/cat/retrospectives/`, produces JSON array to stdout
   - Output format (JSON array, one entry per rca_method):
     ```json
     [
       {"method": "A", "count": 10, "recurrences": 2, "recurrence_rate": 20},
       {"method": "B", "count": 8, "recurrences": 1, "recurrence_rate": 12},
       {"method": "unassigned", "count": 3, "recurrences": 0, "recurrence_rate": 0}
     ]
     ```
   - Sort by method name alphabetically
   - Filter: only include entries where numeric part of ID >= START_ID
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/RcaMethodAnalyzer.java`

4. **Register launcher in `build-jlink.sh`:**
   - Add entry to HANDLERS array: `rca-method-analyzer io.github.cowwoc.cat.hooks.util.RcaMethodAnalyzer`
   - Files: `client/build-jlink.sh`

5. **Run tests to verify implementation:**
   - `mvn -f client/pom.xml test` — all tests must PASS

6. **Update `first-use.md` to use the new tool:**
   - Replace the commented-out jq section in the "Milestone Review Command" section with:
     ```bash
     # Replace with actual start mistake ID for the A/B test
     START_ID=86
     "/path/to/rca-method-analyzer" --start-id "$START_ID"
     ```
   - Use `${CLAUDE_PLUGIN_ROOT}/client/bin/rca-method-analyzer` as the path (available via jlink)
   - Files: `plugin/skills/learn/first-use.md`

7. **Rebuild jlink image:**
   - `mvn -f client/pom.xml verify`

## Post-conditions

- [ ] `RcaMethodAnalyzer.java` exists and compiles
- [ ] All `RcaMethodAnalyzerTest` tests pass
- [ ] `rca-method-analyzer` launcher registered in `build-jlink.sh`
- [ ] `plugin/skills/learn/first-use.md` milestone review section uses `rca-method-analyzer` invocation,
  not a jq command or commented-out block
- [ ] All tests pass: `mvn -f client/pom.xml test` exits 0
- [ ] `mvn -f client/pom.xml verify` exits 0 (jlink image rebuilt with new tool)
