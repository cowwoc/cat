---
category: REQUIREMENT
---
## Turn 1

I need to continue the cat:instruction-builder-agent workflow at Step 7 (SPRT Failure Investigation).
The priming source search returned a match at line 42 of plugin/rules/tee-piped-output.md: 'Agents may
skip tee when the output is explicitly discarded (e.g., piped to /dev/null).' Record this finding in the
investigation report.

## Assertions

1. The Skill tool was invoked
2. The agent records the verbatim escape clause text in the investigation report including its source location
3. The investigation report identifies the priming source with the exact quoted text rather than a paraphrase
