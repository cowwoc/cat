# Plan: unify-posttooluse-hooks

## Current State
6 PostToolUse registrations with 2 bash scripts and 4 Java dispatchers:
- (all): java.sh GetPosttoolOutput (already Java), detect-assistant-giving-up.sh
- Bash: java.sh GetBashPosttoolOutput (already Java)
- Write|Edit: remind-restart-after-skill-modification.sh, java.sh GetPosttoolOutput (already Java)
- Read|Glob|Grep|WebFetch|WebSearch: java.sh GetReadPosttoolOutput (already Java)

## Target State
Absorb detect-assistant-giving-up.sh into GetPosttoolOutput (all-matcher dispatcher) and remind-restart-after-skill-modification.sh into the Write|Edit GetPosttoolOutput dispatcher.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** detect-assistant-giving-up.sh has rate limiting and session JSONL reading
- **Mitigation:** Straightforward port; only 2 scripts

## Files to Modify
- plugin/hooks/java/src/main/java/com/cat/hooks/tool/post/DetectAssistantGivingUp.java - NEW
- plugin/hooks/java/src/main/java/com/cat/hooks/tool/post/RemindRestartAfterSkillModification.java - NEW
- plugin/hooks/java/src/main/java/com/cat/hooks/GetPosttoolOutput.java - Modify to run DetectAssistantGivingUp
- plugin/hooks/java/src/test/java/com/cat/hooks/tool/post/DetectAssistantGivingUpTest.java - NEW
- plugin/hooks/java/src/test/java/com/cat/hooks/tool/post/RemindRestartAfterSkillModificationTest.java - NEW
- plugin/hooks/hooks.json - Remove separate bash entries, consolidate
- plugin/hooks/detect-assistant-giving-up.sh - DELETE
- plugin/hooks/remind-restart-after-skill-modification.sh - DELETE

## Acceptance Criteria
- [ ] detect-assistant-giving-up.sh ported: rate limiting (60s), JSONL reading, token rationalization pattern detection
- [ ] remind-restart-after-skill-modification.sh ported: skill/settings/hook file detection, stderr banner
- [ ] hooks.json PostToolUse consolidated
- [ ] Both bash scripts deleted
- [ ] Tests pass

## Key Implementation Details
- detect-assistant-giving-up.sh: Reads last 20 assistant messages from session JSONL, detects token usage rationalization patterns, rate-limited to once per 60s via /tmp file
- remind-restart-after-skill-modification.sh: Checks if edited file matches skill/settings/hook patterns, outputs banner to stderr

## Execution Steps
1. Create DetectAssistantGivingUp handler
2. Create RemindRestartAfterSkillModification handler
3. Integrate into existing PostToolUse dispatchers
4. Write tests
5. Update hooks.json
6. Delete bash scripts
7. Run full test suite

## Success Criteria
- [ ] All tests pass
- [ ] No bash PostToolUse scripts remain
- [ ] hooks.json PostToolUse entries reduced