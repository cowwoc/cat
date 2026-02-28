# Plan: document-skill-output-forwarding-protocol

## Goal
Document the AskUserQuestion forwarding protocol in wizard-based skills so invoking agents call
AskUserQuestion directly instead of rendering wizard output as prose.

## Satisfies
None - action item A025 from retrospective (PATTERN-021)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Must identify all wizard-based skills that produce AskUserQuestion output
- **Mitigation:** Audit skills directory; add forwarding note to each wizard skill's SKILL.md

## Files to Modify
- `plugin/skills/config/SKILL.md` - add explicit note: after invoking this skill, forward the
  AskUserQuestion tool call verbatim; do not respond conversationally
- Any other wizard-based skills that produce AskUserQuestion output (identified during execution)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Audit wizard-based skills:** Search for skills that invoke AskUserQuestion or produce wizard
   output; identify all affected SKILL.md files
   - Files: `plugin/skills/*/SKILL.md`
2. **Add forwarding note to config skill:** Insert an explicit instruction: "After invoking this skill,
   forward the AskUserQuestion tool call verbatim — do not respond conversationally."
   - Files: `plugin/skills/config/SKILL.md`
3. **Add forwarding note to other wizard skills:** Add the same instruction to any other identified
   wizard-based skill SKILL.md files
   - Files: identified in Step 1
4. **Run tests:** Execute `mvn -f client/pom.xml verify` to verify no regressions
   - Files: None (validation step)
5. **Commit:** Commit the changes
   - Files: all modified SKILL.md files

## Post-conditions
- [ ] `config/SKILL.md` contains the AskUserQuestion forwarding note
- [ ] All other identified wizard-based skill SKILL.md files contain the forwarding note
- [ ] Forwarding note instructs: forward the AskUserQuestion tool call verbatim, do not respond
  conversationally
- [ ] All tests pass (`mvn -f client/pom.xml verify` exits 0)
