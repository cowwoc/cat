# Plan: fix-work-complete-json-args

## Goal
Fix the work-complete skill invocation failure when JSON arguments containing curly braces are interpreted by the shell
before reaching the handler.

## Satisfies
None - bugfix for infrastructure

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Shell quoting is subtle; fix must work across all skill invocations with JSON args
- **Mitigation:** Test with the exact failing invocation pattern

## Reproduction
Invoking `/cat:work-complete` with JSON arguments like:
```
{"issue_id":"2.1-remove-render-add-complete-script","commits":[{"hash":"9f446bf9","message":"...","type":"refactor"}]}
```
Produces: `(eval):1: parse error near '}'`

The curly braces in the JSON are interpreted by shell brace expansion before the skill preprocessor receives them.

## Files to Modify
- TBD after investigation (likely in skill preprocessor or load-skill.sh quoting)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Investigate the quoting pipeline:** Trace how arguments flow from Skill tool invocation through
   load-skill.sh and the skill preprocessor to identify where quoting is lost.
2. **Fix the quoting:** Ensure JSON arguments are properly quoted/escaped through the entire pipeline.
3. **Test:** Verify `/cat:work-complete` works with JSON arguments containing curly braces and arrays.

## Post-conditions
- [ ] `/cat:work-complete` succeeds when invoked with JSON arguments containing `{}` and `[]`
- [ ] No regressions in other skill invocations that pass arguments
- [ ] All tests pass (`mvn -f client/pom.xml test`)
