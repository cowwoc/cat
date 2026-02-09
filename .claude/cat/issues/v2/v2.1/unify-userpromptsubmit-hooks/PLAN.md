# Plan: unify-userpromptsubmit-hooks

## Current State
2 UserPromptSubmit hooks: GetSkillOutput (Java) and detect-giving-up.sh (bash). The bash script spawns a separate process.

## Target State
Single Java dispatcher handles both skill output and giving-up detection. detect-giving-up.sh logic absorbed into GetSkillOutput or a new unified dispatcher.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** detect-giving-up.sh has complex pattern matching with composable keyword functions, rate limiting, and session tracking
- **Mitigation:** Port pattern logic carefully; test with known trigger phrases

## Files to Modify
- hooks/src/main/java/com/cat/hooks/prompt/DetectGivingUp.java - NEW: Port of detect-giving-up.sh
- hooks/src/main/java/com/cat/hooks/GetSkillOutput.java - Modify to also run DetectGivingUp handler
- hooks/src/test/java/com/cat/hooks/prompt/DetectGivingUpTest.java - NEW: Tests
- plugin/hooks/hooks.json - Remove separate detect-giving-up.sh registration
- plugin/hooks/detect-giving-up.sh - DELETE after migration

## Acceptance Criteria
- [ ] detect-giving-up.sh pattern detection logic ported to Java
- [ ] Rate limiting preserved (max 1/second)
- [ ] Session tracking preserved (notify only once per pattern per session)
- [ ] Composable keyword detection preserved (constraint+abandonment, broken+removal, etc.)
- [ ] Quoted text removal for false positive prevention preserved
- [ ] hooks.json has single UserPromptSubmit entry
- [ ] detect-giving-up.sh deleted
- [ ] Tests pass

## Key Implementation Details
- Pattern categories to port:
  - Constraint rationalization: constraint words + abandonment words
  - Broken code removal: failure words + removal words
  - Compilation abandonment: compile error words + simplification words
  - Permission seeking: "would you like" patterns mid-task
- Rate limit: Use timestamp file in /tmp/ or System.currentTimeMillis()
- Session tracking: Use /tmp/claude-hooks-session-{sessionId}/ directory
- Quote removal: Strip text between backticks and quotes before pattern matching
- Stale file cleanup: Delete session dirs older than 7 days

## Execution Steps
1. Create DetectGivingUp handler class with all pattern categories
2. Integrate into GetSkillOutput dispatcher
3. Write tests covering each pattern category
4. Update hooks.json
5. Delete detect-giving-up.sh
6. Run full test suite

## Success Criteria
- [ ] All tests pass
- [ ] hooks.json UserPromptSubmit section has exactly 1 entry
- [ ] No bash UserPromptSubmit scripts remain