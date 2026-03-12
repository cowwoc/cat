# Plan: remove-learn-quick-tier

## Problem
The learn skill has a "quick tier" that skips the Investigate phase (JSONL analysis). However, proper
RCA always requires understanding the agent's thinking at the time of the mistake, which is only
available in JSONL transcripts. The quick-tier subagent receives only a text description and cannot
perform meaningful root cause analysis — it produces shallow causes like "agent used wrong path"
instead of actionable ones like "context compaction removed the correct path from working memory."

## Satisfies
None - quality improvement

## Expected vs Actual
- **Expected:** All learn invocations perform deep investigation via JSONL
- **Actual:** Quick tier skips investigation, producing shallow RCA

## Root Cause
The quick tier was designed to save tokens for "obvious" mistakes, but RCA quality requires JSONL
access regardless of how obvious the mistake appears.

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** None - removing a code path, not adding one
- **Mitigation:** Existing deep-tier tests still pass

## Files to Modify
- plugin/skills/learn/SKILL.md - Remove quick-tier classification, prompt template, and references
- plugin/skills/learn/phase-analyze.md - Remove quick-tier references if any
- plugin/skills/learn/phase-prevent.md - Remove quick-tier references if any
- plugin/skills/learn/phase-record.md - Remove quick-tier references if any

## Test Cases
- [ ] Learn skill invocations always use deep tier (investigate phase always runs)
- [ ] Existing deep-tier flow still works

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Remove Step 1 (tier classification) from SKILL.md
- Update Step 2 to always use deep-tier prompt (remove quick-tier prompt)
- Remove tier references from Step 3 and Step 4 display templates
- Update phase files to remove quick-tier conditionals if any

## Post-conditions
- [ ] SKILL.md contains no references to "quick tier" or tier classification
- [ ] All learn invocations spawn deep-tier subagent with investigate phase
- [ ] Step 4 summary no longer shows tier information
