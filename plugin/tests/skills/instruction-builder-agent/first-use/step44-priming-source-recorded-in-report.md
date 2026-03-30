---
category: requirement
---
## Turn 1

You are executing Step 4.4 investigation sub-step 7. The priming source search (grep for 'unless', 'except',
'if user', 'may', 'optional' with --context 3) in the skill file returns the following match at line 42 of
plugin/rules/tee-piped-output.md: 'Agents may skip tee when the output is explicitly discarded (e.g., piped
to /dev/null).' What do you record in the investigation report for the priming sources field?

## Assertions
1. - **TC12_det_1** (string_match): Agent records the verbatim escape clause text in the report
  - Pattern: `Agents may skip tee when the output is explicitly discarded`
  - Expected: true
- **TC12_det_2** (regex): Agent records the file location of the priming source
  - Pattern: `(tee-piped-output\.md|line 42|plugin/rules/)`
  - Expected: true
- **TC12_det_3** (regex): Report includes a priming sources field or section header
  - Pattern: `[Pp]riming [Ss]ource|priming_source`
  - Expected: true
2. - **TC12_sem_1** Agent quotes the found escape clause verbatim and includes its source location rather
  than paraphrasing or summarizing
  - Check that the agent's report entry for priming sources includes the exact text 'Agents may skip tee
    when the output is explicitly discarded' and identifies it as coming from line 42 of the skill file.
  - Expected: true
