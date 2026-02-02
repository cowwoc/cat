# Plan: migrate-python-to-java

## Current State
All CAT plugin hooks are implemented in Python, requiring users to have Python installed and configured.

## Target State
All hooks migrated to Java with a custom jlinked JDK 25 runtime that includes Jackson 3, eliminating the Python dependency and providing a self-contained runtime.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** HIGH
- **Breaking Changes:** Complete rewrite of hook implementation language
- **Mitigation:** Maintain identical external behavior; comprehensive testing

## Files to Modify
- `plugin/hooks/*.py` - All Python hook handlers to be replaced with Java equivalents
- `plugin/hooks/session_start.sh` - New script to check/download JDK
- `plugin/hooks/java_runner.sh` - New intermediary script for Java hooks
- JDK jlink bundle configuration (new)
- `plugin/skills/compare-docs/SKILL.md` - Update token counting from Python tiktoken to Java

## Dependencies to Add
- `com.knuddels:jtokkit:1.1.0` - Java tokenizer library (tiktoken equivalent)

## Execution Steps

1. **Create jlinked JDK 25 bundle**
   - Files: JDK build configuration, jlink script
   - Include Jackson 3 as module
   - Verify: Bundle runs standalone without system JDK

2. **Migrate Python hooks to Java**
   - Files: All `plugin/hooks/*.py` -> Java source files
   - Maintain same behavior and output format
   - Verify: All existing tests pass

3. **Migrate token counting to Java**
   - Current: Python tiktoken (`cl100k_base` encoding)
   - Target: Java JTokkit (`EncodingType.CL100K_BASE`)
   - Files: Create `TokenCounter.java` utility, update `compare-docs/SKILL.md`
   - Usage: `java -cp cat-hooks.jar io.github.cowwoc.cat.hooks.TokenCounter file1.md file2.md`
   - Verify: Token counts match Python tiktoken output (±1% tolerance for edge cases)

4. **Add SessionStart JDK bootstrap script**
   - Files: `plugin/hooks/session_start.sh`
   - Check if JDK 25 on PATH, if not download jlinked bundle from GitHub to CAT cache
   - Verify: Script correctly detects/downloads JDK

5. **Add Java hook runner intermediary script**
   - Files: `plugin/hooks/java_runner.sh`
   - Detect downloaded JDK vs PATH JDK and use appropriate one
   - Verify: Hooks execute correctly with both JDK sources

## Acceptance Criteria
- [ ] Behavior unchanged - hooks produce identical output
- [ ] All tests still pass
- [ ] Code quality improved - Java type safety, better maintainability
- [ ] Token counting via JTokkit matches Python tiktoken (±1% tolerance)
