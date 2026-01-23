# Plan: add-handler-tests

## Goal
Add comprehensive test coverage for all skill handler scripts, ensuring each business use-case has a dedicated test.

## Satisfies
None (infrastructure task)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Test framework selection, mocking dependencies
- **Mitigation:** Use pytest with fixtures for isolation

## Files to Modify
- `plugin/hooks/skill_handlers/status_handler.py` - Add tests
- `plugin/hooks/skill_handlers/work_handler.py` - Add tests
- `plugin/hooks/skill_handlers/add_handler.py` - Add tests
- `tests/test_handlers.py` - New test file(s)

## Acceptance Criteria
- [ ] Each business use-case has a dedicated test
- [ ] All handler scripts have test coverage
- [ ] Tests pass in CI

## Execution Steps
1. **Step 1:** Identify all handler scripts and their use-cases
   - Files: plugin/hooks/skill_handlers/*.py
   - Verify: List of handlers and use-cases documented

2. **Step 2:** Create test structure
   - Files: tests/test_handlers.py or tests/handlers/
   - Verify: Test files exist with proper imports

3. **Step 3:** Implement tests for each handler
   - Files: Test files for each handler
   - Verify: pytest runs successfully
