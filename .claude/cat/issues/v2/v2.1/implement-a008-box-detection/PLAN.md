# Plan: implement-a008-box-detection

## Goal
Add PostToolUse hook to detect when Claude manually constructs boxes/banners instead of using pre-rendered scripts,
preventing PATTERN-008 mistakes.

## Satisfies
- A008 action item from retrospective R006

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** False positives on legitimate box-drawing character usage
- **Mitigation:** Only trigger when box chars appear in agent output without preceding script execution

## Files to Modify
- plugin/hooks/posttool_handlers/detect_manual_boxes.py - New handler
- plugin/hooks/posttool_handlers/__init__.py - Register handler

## Acceptance Criteria
- [ ] Hook detects box-drawing characters (─│┌┐└┘├┤┬┴┼) in agent messages
- [ ] Warning emitted when boxes appear without get-progress-banner.sh or get-work-boxes.py execution
- [ ] No false positives on legitimate uses (e.g., in code blocks)
- [ ] Tests pass

## Execution Steps
1. Create detect_manual_boxes.py PostToolUse handler
2. Register in __init__.py
3. Add tests
4. Verify with run_tests.py
