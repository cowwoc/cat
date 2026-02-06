# Plan: add-homograph-detection

## Goal
Protect against Unicode homograph attacks by detecting visually confusable characters (e.g., Cyrillic "і" vs Latin "i")
in URLs, domains, and user input prompts. Inspired by Tirith's terminal security approach.

## Satisfies
- REQ-001: Homograph detection for URLs/domains
- REQ-002: Hook integration for pre-execution validation
- REQ-004: Input prompt validation

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** False positives on legitimate Unicode text, performance overhead on every command
- **Mitigation:** Configurable sensitivity, allowlist for known safe domains, efficient confusable character lookup

## Files to Modify
- `plugin/hooks/` - Add validation hook
- `plugin/` - Core detection logic

## Acceptance Criteria
- [ ] Functionality works as described
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] No regressions

## Execution Steps
1. **Step 1:** Research Unicode confusable characters and build detection logic
   - Files: new detection module
   - Verify: unit tests pass for known homograph examples

2. **Step 2:** Integrate with hook system for pre-execution validation
   - Files: plugin/hooks/
   - Verify: hook triggers on commands with suspicious URLs

3. **Step 3:** Add input prompt validation
   - Files: plugin/hooks/
   - Verify: user prompts are scanned for homograph attacks

4. **Step 4:** Add configuration options (warn vs block)
   - Files: cat-config.json schema
   - Verify: behavior changes based on config

5. **Step 5:** Write tests and documentation
   - Files: tests/, docs/
   - Verify: all tests pass, docs explain the feature
