# Plan

## Goal

Refactor LoggerFactory.getLogger to use class literal instead of getClass()

## Type

refactor

## Pre-conditions

(none)

## Post-conditions

- [ ] All LoggerFactory.getLogger(getClass()) calls in client/src/ replaced with LoggerFactory.getLogger(ClassName.class)
- [ ] User-visible behavior unchanged
- [ ] All existing tests pass
- [ ] E2E verification: build succeeds and tests pass after the change

## Research Findings

There are 7 files containing `LoggerFactory.getLogger(getClass())` in `client/src/main/java/`:

1. `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java` — class `PostToolUseFailureHook`
2. `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java` — class `PreToolUseHook`
3. `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java` — class `WorkPrepare`
4. `client/src/main/java/io/github/cowwoc/cat/hooks/util/WriteAndCommit.java` — class `WriteAndCommit`
5. `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillTestRunner.java` — class `SkillTestRunner`
6. `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java` — class `GetAddOutput`
7. `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java` — class `InjectSubAgentRules`

Each file has exactly one occurrence as an instance field declaration:
`private final Logger log = LoggerFactory.getLogger(getClass());`

**Why class literal is preferred:** Using `getClass()` at runtime resolves to the actual runtime class, which can
differ from the declared type in subclasses. `ClassName.class` captures the class at compile time, which is the
intended behavior for a `final` class. It also avoids a redundant virtual dispatch and is more explicit.

## Jobs

### Job 1

- In `client/src/main/java/io/github/cowwoc/cat/hooks/PostToolUseFailureHook.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(PostToolUseFailureHook.class)`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/PreToolUseHook.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(PreToolUseHook.class)`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/WorkPrepare.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(WorkPrepare.class)`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/util/WriteAndCommit.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(WriteAndCommit.class)`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillTestRunner.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(SkillTestRunner.class)`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetAddOutput.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(GetAddOutput.class)`
- In `client/src/main/java/io/github/cowwoc/cat/hooks/session/InjectSubAgentRules.java`:
  Replace `LoggerFactory.getLogger(getClass())` with `LoggerFactory.getLogger(InjectSubAgentRules.class)`
- Run `mvn -f client/pom.xml test` to verify all tests pass
- Update index.json: status=closed, progress=100%
- Commit with message: `refactor: use class literal in LoggerFactory.getLogger calls`
