<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Skill Validator

## Purpose

Run should-trigger and should-not-trigger test prompts against a skill's `description:` frontmatter
and return pass/fail results for each prompt. This verifies that the skill's trigger description
routes correctly — activating when intended and staying silent when not intended.

## Inputs

The invoking agent passes:

1. **Skill path**: path to the skill's `SKILL.md` file (used to extract the `description:` frontmatter)
2. **Test prompts** as a JSON object with two arrays:
   ```json
   {
     "should_trigger": ["phrase 1", "phrase 2", "phrase 3"],
     "should_not_trigger": ["phrase A", "phrase B", "phrase C"]
   }
   ```

## Procedure

### Step 1: Extract Description

Read the `description:` field from the skill's SKILL.md frontmatter. This is the text that would
be used for intent routing when Claude decides whether to invoke the skill.

### Step 2: Evaluate Each Should-Trigger Prompt

For each prompt in `should_trigger`:

1. Consider whether a user typing that exact phrase would intend to invoke a skill matching the description.
2. Ask: "Does this phrase fall within the trigger conditions stated in the description?"
3. Record: PASS if the description would route this prompt to the skill, FAIL if it would not.
4. Write a one-sentence explanation of your verdict.

### Step 3: Evaluate Each Should-Not-Trigger Prompt

For each prompt in `should_not_trigger`:

1. Consider whether a user typing that exact phrase would intend to invoke this skill.
2. Ask: "Is this phrase outside the trigger conditions stated in the description?"
3. Record: PASS if the description would NOT route this prompt to the skill, FAIL if it would.
4. Write a one-sentence explanation of your verdict.

### Step 4: Produce Results

Output a structured result for each prompt. Use this exact format:

```
SKILL VALIDATION RESULTS
========================

Should-Trigger Prompts:
  [PASS/FAIL] "<prompt>" — <one-sentence explanation>
  [PASS/FAIL] "<prompt>" — <one-sentence explanation>

Should-Not-Trigger Prompts:
  [PASS/FAIL] "<prompt>" — <one-sentence explanation>
  [PASS/FAIL] "<prompt>" — <one-sentence explanation>

Summary: X/Y passed
Calibration: [GOOD if all pass | NEEDS ADJUSTMENT if any fail]

Issues found:
  - <description of calibration problem if any fail, or "None" if all pass>
```

## Verification

- [ ] Every prompt in both arrays has a verdict and explanation
- [ ] Summary count matches the actual pass/fail tallies
- [ ] Calibration status is GOOD only when all prompts pass
- [ ] Issues section identifies specific calibration problems when any prompt fails
