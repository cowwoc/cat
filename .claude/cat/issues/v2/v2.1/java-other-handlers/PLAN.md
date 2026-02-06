# Plan: java-other-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 5 of 5 (Wave 3 - concurrent with java-skill-handlers and java-bash-handlers)
- **Estimated Tokens:** 25K

## Objective
Create Java equivalents for 6 missing handlers (prompt, posttool, read) and verify all existing Java handlers in these
categories produce identical output to Python.

## Scope
- 3 Java prompt handlers already exist - verify output matches Python
- 1 Java posttool handler already exists - verify output matches Python
- 2 Java read handlers already exist - verify output matches Python
- 6 Python handlers have NO Java equivalent yet - create them

## Dependencies
- java-core-hooks (entry points must be wired up)

## Existing Java Implementations (verify only)

Prompt handlers at `plugin/hooks/src/io/github/cowwoc/cat/hooks/prompt/`:

| Python | Java (exists) |
|--------|---------------|
| `prompt_handlers/critical_thinking.py` | `CriticalThinking.java` |
| `prompt_handlers/destructive_ops.py` | `DestructiveOps.java` |
| `prompt_handlers/user_issues.py` | `UserIssues.java` |

Posttool handlers at `plugin/hooks/src/io/github/cowwoc/cat/hooks/tool/post/`:

| Python | Java (exists) |
|--------|---------------|
| `posttool_handlers/auto_learn.py` | `AutoLearnMistakes.java` |

Read handlers:

| Python | Java (exists) |
|--------|---------------|
| `read_posttool_handlers/detect_sequential_tools.py` | `read/post/DetectSequentialTools.java` |
| `read_pretool_handlers/predict_batch_opportunity.py` | `read/pre/PredictBatchOpportunity.java` |

## Missing Java Implementations (create new)

| Python | Java (to create) | Location |
|--------|------------------|----------|
| `prompt_handlers/abort_clarification.py` | `AbortClarification.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/prompt/` |
| `posttool_handlers/detect_manual_boxes.py` | `DetectManualBoxes.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/tool/post/` |
| `posttool_handlers/detect_validation_fabrication.py` | `DetectValidationFabrication.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/tool/post/` |
| `posttool_handlers/skill_preprocessor_output.py` | `SkillPreprocessorOutput.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/tool/post/` |
| `posttool_handlers/user_input_reminder.py` | `UserInputReminder.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/tool/post/` |
| `posttool_handlers/validate_state_status.py` | `ValidateStateStatus.java` | `plugin/hooks/src/io/github/cowwoc/cat/hooks/tool/post/` |

## Execution Steps
1. **Create AbortClarification.java** - Port logic from `abort_clarification.py`
2. **Create DetectManualBoxes.java** - Port logic from `detect_manual_boxes.py`
3. **Create DetectValidationFabrication.java** - Port logic from `detect_validation_fabrication.py`
4. **Create SkillPreprocessorOutput.java** - Port logic from `skill_preprocessor_output.py`
5. **Create UserInputReminder.java** - Port logic from `user_input_reminder.py`
6. **Create ValidateStateStatus.java** - Port logic from `validate_state_status.py`
7. **Register new handlers** in appropriate dispatchers (`PromptHandler.java`, `PosttoolHandler.java`)
8. **Verify all handlers** produce identical output to Python equivalents
9. **Run test suite** - `python3 /workspace/run_tests.py` to verify no regressions

## Acceptance Criteria
- [ ] 6 new Java handlers created and registered
- [ ] All prompt handlers (4 total) produce identical output to Python
- [ ] All posttool handlers (6 total) produce identical output to Python
- [ ] All read handlers (2 total) produce identical output to Python
- [ ] All existing tests pass
