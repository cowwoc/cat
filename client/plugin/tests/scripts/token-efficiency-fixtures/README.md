<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Token Efficiency Fixtures

Test fixtures for the token efficiency detection system. Each file provides a representative
sample of Claude output used to verify the accuracy of pattern matching in the output token
optimization guidance feature.

## Fixture Categories

**True positives** — outputs that should trigger token optimization guidance. Named
`*-true-positive.md`. These contain patterns like boilerplate repetition, verbose re-statements
of prior context, or excessive preamble that inflates token usage without adding value.

**False positives** — outputs that resemble true positives but should NOT trigger guidance.
Named `*-false-positive*.md`. These contain superficially similar patterns (e.g., leading spaces,
structured formatting) that are legitimate and should not be flagged.

**Analysis fixtures** — reference documents used for gap analysis and threshold calibration.
Named `*-analysis.md` or `detection-gap-*.md`.

## Adding New Fixtures

When adding a new fixture:

1. Choose a descriptive name that reflects the pattern being tested.
2. Use the suffix `-true-positive.md` or `-false-positive.md` to indicate expected behavior.
3. Include a brief comment at the top of the file explaining what the fixture represents.
4. Add a corresponding test case in the appropriate test class.
