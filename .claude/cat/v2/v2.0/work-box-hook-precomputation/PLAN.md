# Plan: work-box-hook-precomputation

## Problem
LLMs cannot reliably render box-drawing characters with correct alignment because they can't accurately calculate emoji widths (e.g., ✓ renders as 2 columns but is 1 character). The boxes in `/cat:work` output (Task Complete, Scope Complete) have misaligned right borders when rendered by the LLM.

## Satisfies
- None (UX bugfix)

## Reproduction Code
```
Run /cat:work and complete a task. The completion boxes show misaligned borders
because the LLM guesses at character widths instead of computing them precisely.
```

## Expected vs Actual
- **Expected:** Box borders align perfectly using Python's unicodedata for precise width calculation
- **Actual:** LLM renders boxes with inconsistent alignment due to emoji width miscalculation

## Root Cause
Box templates are embedded directly in work.md and rendered by the LLM at runtime. The LLM cannot perform accurate character width calculations, especially for emojis and special characters.

## Solution
Implement hook-based pre-computation (like status_handler.py) where a Python script computes all possible outcome boxes at skill invocation, and the LLM outputs them verbatim.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Could affect work command output if handler fails
- **Mitigation:** Handler returns pre-computed boxes in additionalContext; skill falls back to inline templates if not found

## Files to Modify
- plugin/hooks/skill_handlers/work_handler.py - Create new handler for pre-computing work boxes
- plugin/commands/work.md - Update to use pre-computed boxes from handler

## Test Cases
- [ ] Task Complete box renders with aligned borders
- [ ] Scope Complete box renders with aligned borders
- [ ] Low-trust Task Complete box renders with aligned borders
- [ ] Handler gracefully handles missing task info

## Execution Steps
1. **Create work_handler.py**
   - Files: plugin/hooks/skill_handlers/work_handler.py
   - Verify: Handler registered and returns PRE-COMPUTED WORK BOXES in additionalContext

2. **Update work.md to use pre-computed boxes**
   - Files: plugin/commands/work.md
   - Verify: Skill instructions reference PRE-COMPUTED WORK BOXES and output verbatim
