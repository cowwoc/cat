# Plan: python-prerendered-output

## Current State
Python skill handlers use "OUTPUT TEMPLATE" pattern with placeholder text that the LLM is expected to find and output.

## Target State
Python handlers produce pre-rendered output that the skill can display directly, matching the pattern already established in the Java handlers.

## Satisfies
- Part of migrate-python-to-java sequence (architecture alignment)

## Risk Assessment
- **Risk Level:** LOW
- **Breaking Changes:** None - output format remains identical
- **Mitigation:** Tests verify output matches expected format

## Files to Modify
- plugin/hooks/skill_handlers/token_report_handler.py - Use pre-rendered pattern
- plugin/hooks/skill_handlers/render_diff_handler.py - Use pre-rendered pattern
- plugin/hooks/skill_handlers/config_handler.py - Use pre-rendered pattern
- plugin/hooks/skill_handlers/work_handler.py - Use pre-rendered pattern
- plugin/hooks/skill_handlers/cleanup_handler.py - Use pre-rendered pattern

## Acceptance Criteria
- [ ] All tests still pass
- [ ] Python handlers use pre-rendered output pattern
- [ ] No "OUTPUT TEMPLATE" references remain in handler output

## Execution Steps
1. **Update token_report_handler.py**
   - Replace "OUTPUT TEMPLATE TOKEN REPORT" with direct output
   - Verify: python3 run_tests.py

2. **Update render_diff_handler.py**
   - Replace template pattern with pre-rendered output
   - Verify: python3 run_tests.py

3. **Update remaining handlers**
   - Apply same pattern to config, work, cleanup handlers
   - Verify: python3 run_tests.py
