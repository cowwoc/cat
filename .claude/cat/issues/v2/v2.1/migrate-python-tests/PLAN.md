# Plan: migrate-python-tests

## Metadata
- **Parent:** migrate-python-to-java
- **Wave:** 4 (sequential - after all handler migration completes)
- **Estimated Tokens:** 30K

## Goal
Convert all Python test files to Java TestNG tests. The existing Python tests validate handler behavior; equivalent Java tests must exist before Python files can be removed.

## Satisfies
None - infrastructure/setup task

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Test coverage gaps during migration; TestNG vs pytest behavior differences
- **Mitigation:** Run both test suites in parallel until Java tests cover all Python test cases

## Python Test Files to Migrate (~20 files)

Handler tests at `tests/handlers/`:
- `test_add_handler.py`
- `test_add_handler_preload.py`
- `test_cleanup_handler.py`
- `test_config_handler.py`
- `test_display_utils.py`
- `test_help_handler.py`
- `test_monitor_subagents_handler.py`
- `test_research_handler.py`
- `test_stakeholder_handler.py`
- `test_status_handler.py`
- `test_work_handler.py`
- `conftest.py` (shared fixtures)

Script tests at `tests/scripts/`:
- `test_create_issue.py`
- `test_get_issue_complete_box.py`
- `test_work_prepare.py`

Root tests at `tests/`:
- `test_analyze_session.py`
- `test_auto_learn.py`
- `test_detect_validation_fabrication.py`
- `test_validate_state_status.py`

## Java Test Target
`plugin/hooks/java/src/test/java/io/github/cowwoc/cat/hooks/test/`

Existing: `HookEntryPointTest.java` (11 tests)

## Dependencies
- All handler subtasks complete (handlers must work in Java before tests can validate them)

## Execution Steps
1. **Read each Python test file** to understand test cases and assertions
2. **Create equivalent Java TestNG tests** at target location
3. **Migrate shared fixtures** from `conftest.py` to Java test utilities
4. **Run `mvn test`** to verify all Java tests pass
5. **Compare coverage** - ensure Java tests cover all Python test scenarios
6. **Run `python3 /workspace/run_tests.py`** to verify Python tests still pass during transition

## Acceptance Criteria
- [ ] All Python test scenarios have equivalent Java TestNG tests
- [ ] `mvn test` passes with all Java tests (exit code 0)
- [ ] Test count is equivalent or greater than Python test count
- [ ] Shared test utilities migrated from conftest.py
