# Plan

## Goal

Create a skill test that verifies Haiku returns a clear error with explanation when it encounters a plan step that cannot be executed mechanically and requires a decision. The test should use a plan.md containing ambiguous or under-specified steps that require reasoning/judgment, and verify that Haiku fails fast with an informative error rather than guessing or producing incorrect output.

## Pre-conditions

(none)

## Post-conditions

- [ ] A skill test exists with a plan.md containing steps that require non-mechanical decisions (e.g., "choose the best approach", "handle edge cases appropriately")
- [ ] When Haiku encounters such steps, it returns an error explaining why the step cannot be executed mechanically
- [ ] The error message identifies the specific step and explains what decision is needed
- [ ] E2E: Running the test confirms Haiku correctly identifies non-mechanical steps and refuses to guess
