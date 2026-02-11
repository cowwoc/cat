# Plan: enforce-state-schema-hook

## Goal
Add a Java PreToolUse hook that validates any STATE.md file modification against the standardized schema, preventing non-standard keys or invalid value formats from being written.

## Satisfies
None - infrastructure enforcement

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Hook must not block legitimate STATE.md edits
- **Mitigation:** Validate against well-defined schema; run tests with sample valid/invalid files

## Files to Modify
- `hooks/src/main/java/com/cat/hooks/StateSchemaValidator.java` (new)
- `hooks/src/test/java/com/cat/hooks/StateSchemaValidatorTest.java` (new)
- `plugin/hooks/hooks.json` - register the new hook

## Schema to Enforce

### Mandatory Keys (all issues)
- **Status:** open | in-progress | closed
- **Progress:** 0-100% (integer)
- **Dependencies:** [] or [comma-separated-issue-ids]
- **Blocks:** [] or [comma-separated-issue-ids]
- **Last Updated:** YYYY-MM-DD

### Mandatory for Closed Issues
- **Resolution:** implemented | duplicate (<issue-id>) | obsolete (<explanation>) | won't-fix (<explanation>) | not-applicable (<explanation>)

### Optional Keys
- **Parent:** issue-id

### Validation Rules
1. Only recognized keys allowed (Status, Progress, Dependencies, Blocks, Last Updated, Resolution, Parent)
2. Status must be one of: open, in-progress, closed
3. Progress must be integer 0-100 followed by %
4. Dependencies and Blocks must be [] or [comma-separated values]
5. Last Updated must match YYYY-MM-DD format
6. Resolution required when Status is closed
7. Resolution value must start with: implemented, duplicate, obsolete, won't-fix, not-applicable
8. Parent value must be a valid issue slug (lowercase, hyphens, alphanumeric)

## Acceptance Criteria
- [ ] Hook validates all mandatory keys are present
- [ ] Hook rejects non-standard keys
- [ ] Hook validates value formats
- [ ] Hook requires Resolution for closed issues
- [ ] Hook allows optional Parent key
- [ ] All tests pass
- [ ] Hook registered in hooks.json

## Execution Steps
1. **Step 1:** Read Java conventions from `.claude/cat/conventions/java.md`
2. **Step 2:** Create StateSchemaValidator.java implementing PreToolUse hook
   - Parse STATE.md content being written
   - Extract key-value pairs from markdown bullet format
   - Validate against schema rules
   - Return blocking message for violations
   - Files: `hooks/src/main/java/com/cat/hooks/StateSchemaValidator.java`
3. **Step 3:** Create comprehensive TestNG tests
   - Test valid STATE.md (open, closed with resolution, with parent, with dependencies)
   - Test invalid: missing mandatory keys, non-standard keys, bad value formats
   - Test edge cases: empty file, non-STATE.md files (should pass through)
   - Files: `hooks/src/test/java/com/cat/hooks/StateSchemaValidatorTest.java`
4. **Step 4:** Register hook in plugin/hooks/hooks.json for Edit and Write tools targeting STATE.md
5. **Step 5:** Run tests with `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] All tests pass
- [ ] Hook correctly blocks invalid STATE.md modifications
- [ ] Hook passes valid STATE.md modifications
- [ ] Hook only triggers for issue STATE.md files (not version STATE.md files)