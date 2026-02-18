# Plan: unify-userpromptsubmit-hooks

## Current State
2 UserPromptSubmit hooks: GetSkillOutput (Java) and forced-eval-skills.py (Python). The Python script spawns a separate
process.

## Target State
Single Java dispatcher handles both skill output and forced skill evaluation. forced-eval-skills.py logic absorbed into
GetSkillOutput via a new ForcedEvalSkills handler.

## Satisfies
None

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** forced-eval-skills.py injects a static instruction string; straightforward to port
- **Mitigation:** Port instruction text verbatim; test instruction content matches expected strings

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/GetSkillOutput.java` - Integrate ForcedEvalSkills handler
- `client/src/main/java/io/github/cowwoc/cat/hooks/prompt/ForcedEvalSkills.java` - NEW: Port of forced-eval-skills.py
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ForcedEvalSkillsTest.java` - NEW: Tests
- `plugin/hooks/hooks.json` - Remove separate forced-eval-skills.py registration

## Files to Delete
- `plugin/hooks/forced-eval-skills.py`

## Acceptance Criteria
- [ ] forced-eval-skills.py logic ported to Java ForcedEvalSkills.java
- [ ] ForcedEvalSkills integrated into GetSkillOutput dispatcher
- [ ] hooks.json has single UserPromptSubmit entry
- [ ] forced-eval-skills.py deleted
- [ ] Tests pass

## Execution Steps
1. Create ForcedEvalSkills handler class with instruction text
2. Integrate into GetSkillOutput dispatcher
3. Write tests verifying instruction content
4. Update hooks.json
5. Delete forced-eval-skills.py
6. Run full test suite

## Success Criteria
- [ ] All tests pass
- [ ] hooks.json UserPromptSubmit section has exactly 1 entry
- [ ] No Python UserPromptSubmit scripts remain