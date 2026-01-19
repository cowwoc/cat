# Plan: review-feedback-subagent

## Problem
When users provide feedback during the code review phase, the main agent currently implements the
changes directly. This consumes main agent context and can lead to context exhaustion on complex
tasks with multiple review iterations.

## Satisfies
- None (workflow improvement)

## Reproduction
1. Run `/cat:work` on a task
2. During user approval gate, provide feedback like "also add error handling"
3. Main agent implements changes directly, consuming its context

## Expected vs Actual
- **Expected:** Feedback implementation delegated to subagent, preserving main agent context
- **Actual:** Main agent implements changes, risking context exhaustion

## Root Cause
The approval_gate step in work.md handles user feedback inline rather than spawning a subagent.

## Fix Approach Outlines

### Conservative
Add a simple spawn-subagent call when user provides feedback, with minimal context passing.
- **Risk:** LOW
- **Tradeoff:** Subagent may lack context about what was already implemented

### Balanced
Spawn subagent with full task context (PLAN.md, current diff, user feedback) and collect results
back into main workflow. Reuse existing spawn-subagent/collect-results skills.
- **Risk:** MEDIUM
- **Tradeoff:** Adds complexity to approval_gate step

### Aggressive
Create dedicated feedback-handler skill with specialized prompts for incremental changes.
- **Risk:** HIGH
- **Tradeoff:** Over-engineering for current needs

## Acceptance Criteria
- [ ] User feedback during approval gate spawns a subagent
- [ ] Subagent receives: task PLAN.md, current diff, user feedback
- [ ] Main agent context preserved across feedback iterations
- [ ] Results collected and merged back before next approval gate
- [ ] Works with existing spawn-subagent and collect-results skills
