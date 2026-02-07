# Plan: migrate-python-to-java

## Current State
CAT plugin hooks are being incrementally migrated from Python to Java. The Java infrastructure is complete, 1 hook is
actively using Java (GetBashPretoolOutput), and 6 more entry points are ready to wire up. The migration follows an
incremental approach: wire up existing Java implementations one at a time, then migrate remaining Python hooks.

## Target State
All hooks migrated to Java with JDK 25 runtime (system JDK or custom jlinked bundle) that includes Jackson 3,
eliminating the Python dependency and providing a self-contained runtime.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Complete rewrite of hook implementation language
- **Mitigation:** Incremental migration one hook at a time; maintain identical external behavior; comprehensive testing

## Already Complete

### Infrastructure (Complete)
- Maven project structure: `plugin/hooks/java/pom.xml`, `module-info.java`
- JDK infrastructure:
  - `plugin/hooks/java.sh` - Java hook entry point with correct package prefix
  - `plugin/hooks/session_start.sh` - JDK detection/download logic
  - `plugin/hooks/jlink-config.sh` - jlink bundle configuration
- System JDK fallback working correctly in `java.sh`

### Java Implementations (53 files)
- 7 entry point handlers (for hooks.json):
  - `GetBashPretoolOutput.java` (wired up in hooks.json line 87)
  - `GetSkillOutput.java` (ready to wire up line 50)
  - `GetReadPretoolOutput.java` (ready to wire up line 96)
  - `GetPosttoolOutput.java` (ready to wire up lines 142, 178)
  - `GetBashPosttoolOutput.java` (ready to wire up line 159)
  - `GetReadPosttoolOutput.java` (ready to wire up line 186)
  - Plus 2 Python hooks without Java equivalents yet (see below)
- 46 supporting classes and handlers (BashHandler, ReadHandler, PosttoolHandler, skill handlers, etc.)

### Tests (1 Java test file)
- `plugin/hooks/src/test/java/io/github/cowwoc/cat/hooks/HookEntryPointTest.java` - 11 tests

## Files to Modify

### Phase 1: Wire Up Existing Java Entry Points
- `plugin/hooks/hooks.json` - Replace 6 Python entry points with java.sh calls:
  - Line 50: `get-skill-output.py` → `GetSkillOutput`
  - Line 96: `get-read-pretool-output.py` → `GetReadPretoolOutput`
  - Line 142: `get-posttool-output.py` → `GetPosttoolOutput`
  - Line 159: `get-bash-posttool-output.py` → `GetBashPosttoolOutput`
  - Line 178: `get-posttool-output.py` → `GetPosttoolOutput` (duplicate entry)
  - Line 186: `get-read-posttool-output.py` → `GetReadPosttoolOutput`

### Phase 2: Migrate Remaining Python Hooks to Java
- `plugin/hooks/enforce-worktree-isolation.py` → `EnforceWorktreeIsolation.java`
- `plugin/hooks/enforce-status-output.py` → `EnforceStatusOutput.java`
- Wire up in hooks.json lines 132 and 196

### Phase 3: Migrate Python Tests to Java
- 18 Python test files in `tests/` → Java TestNG tests
- Target: `plugin/hooks/src/test/java/io/github/cowwoc/cat/hooks/`

### Phase 4: Cleanup
- Remove all Python hook files (`plugin/hooks/*.py`)
- Remove all Python test files (`tests/**/*.py`)

### Phase 5: Token Counting Migration
- `plugin/skills/compare-docs/SKILL.md` - Update token counting from Python tiktoken to Java JTokkit
- Create `TokenCounter.java` utility class
- Usage: `java -cp cat-hooks.jar io.github.cowwoc.cat.hooks.TokenCounter file1.md file2.md`

## Dependencies to Add
- `com.knuddels:jtokkit:1.1.0` - Java tokenizer library (tiktoken equivalent)

## Execution Steps

### Step 1: Wire up 6 existing Java entry points (one at a time)
- Edit `plugin/hooks/hooks.json` to replace Python entry point with java.sh call
- Test the specific hook by triggering it (e.g., run a Bash command, use a skill)
- Verify: Hook output identical to Python version
- Repeat for each of the 6 entry points

### Step 2: Migrate EnforceWorktreeIsolation
- Create `EnforceWorktreeIsolation.java` based on Python implementation
- Wire up in hooks.json line 132
- Test by attempting Write/Edit in base branch
- Verify: Hook blocks operation with correct error message

### Step 3: Migrate EnforceStatusOutput
- Create `EnforceStatusOutput.java` based on Python implementation
- Wire up in hooks.json line 196
- Test by using Stop hook
- Verify: Hook output identical to Python version

### Step 4: Migrate Python tests to Java TestNG
- Convert 18 Python test files to Java TestNG tests
- Run `mvn test` to verify all tests pass
- Verify: Coverage equivalent to Python tests

### Step 5: Remove Python files
- Delete all `plugin/hooks/*.py` files
- Delete all `tests/**/*.py` files
- Run remaining tests to verify no breakage
- Verify: No Python files remain in hook paths

### Step 6: Token counting migration
- Create `TokenCounter.java` with JTokkit integration
- Update `compare-docs/SKILL.md` to use Java command
- Test token counting on sample files
- Verify: Token counts match Python tiktoken (±1% tolerance)

## Acceptance Criteria
- [ ] All 7 entry point hooks use Java (via java.sh in hooks.json)
- [ ] All tests migrated to Java TestNG and passing (`mvn test` exit code 0)
- [ ] All Python hook files removed (`plugin/hooks/*.py`)
- [ ] All Python test files removed (`tests/**/*.py`)
- [ ] Token counting uses JTokkit and matches Python tiktoken (±1% tolerance)
- [ ] System JDK and jlinked JDK bundle both work correctly
