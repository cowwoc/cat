---
category: requirement
---
## Turn 1

Grade the following test run:

1. **Transcript**: The agent wrote a skill instruction file with content "Shows the current date",
   then invoked the Skill tool with skill `cat:instruction-builder-agent`. The skill failed because
   the CAT client binary could not be downloaded.
2. **Scenario file path**: plugin/tests/agents/instruction-grader-agent/fixtures/grader-schema-scenario.md
3. **Run ID**: `schema_compliance_run1`
4. **Output path**: .cat/work/test-runs/grader-schema-compliance/schema_compliance_run1_grading.json
5. **Session ID**: test-session

## Assertions

1. The file `.cat/work/test-runs/grader-schema-compliance/schema_compliance_run1_grading.json` exists
2. The grade file's `assertion_results` array contains exactly 2 entries
3. The first entry's `assertion` field is exactly "The Skill tool was invoked with skill instruction-builder-agent"
4. The second entry's `assertion` field is exactly "A skill instruction file was created"
5. Each entry's `explanation` field is a complete sentence (begins with a capital letter, ends with a period, exclamation mark, or question mark)
6. Each entry's `evidence` field contains a verbatim quote from the transcript
7. Each entry's `verdict` field is `"PASS"` or `"FAIL"` (uppercase, not `"pass"` or `"fail"`)
