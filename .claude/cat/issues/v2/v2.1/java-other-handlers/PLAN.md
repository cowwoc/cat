# Plan: java-other-handlers

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 5 of 5
- **Estimated Tokens:** 25K

## Objective
Migrate remaining handlers: prompt, posttool, and read handlers.

## Scope
- prompt_handlers/ - User prompt processing
- posttool_handlers/ - General post-tool processing
- read_posttool_handlers/ - Read tool post-processing
- read_pretool_handlers/ - Read tool pre-processing

## Dependencies
- java-core-hooks (core infrastructure must exist)

## Files to Migrate
| Python | Java |
|--------|------|
| prompt_handlers/critical_thinking.py | src/cat/hooks/prompt/CriticalThinking.java |
| prompt_handlers/destructive_ops.py | src/cat/hooks/prompt/DestructiveOps.java |
| prompt_handlers/user_issues.py | src/cat/hooks/prompt/UserIssues.java |
| posttool_handlers/auto_learn_mistakes.py | src/cat/hooks/posttool/AutoLearnMistakes.java |
| posttool_handlers/skill_precompute.py | src/cat/hooks/posttool/SkillPrecompute.java |
| read_posttool_handlers/detect_sequential_tools.py | src/cat/hooks/read/post/DetectSequentialTools.java |
| read_pretool_handlers/predict_batch_opportunity.py | src/cat/hooks/read/pre/PredictBatchOpportunity.java |

## Execution Steps
1. Migrate prompt handlers
2. Migrate posttool handlers
3. Migrate read handlers
4. Verify all output matches Python version

## Acceptance Criteria
- [ ] All handlers produce identical output
- [ ] Prompt analysis logic matches Python
- [ ] Post-tool processing works identically
- [ ] Read tool handlers behave identically
