<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Description Tester

## Purpose

Given a skill's `description:` frontmatter, generate calibration queries that test the description's
trigger precision, then evaluate each query to determine whether it correctly triggers or correctly
passes through. This surfaces over-broad and under-broad descriptions before the skill is deployed.

## Inputs

The invoking agent passes the `description:` field text from a skill's SKILL.md frontmatter.

## Procedure

### Step 1: Analyze the Description

Read the description and identify:

1. **Core trigger condition**: The primary action or scenario that activates the skill.
2. **Explicit synonyms**: Any alternative phrasings listed in the description.
3. **Scope boundaries**: What the description implicitly excludes.
4. **Adjacent domains**: Topics close to but outside the skill's scope.

### Step 2: Generate Calibration Queries

Generate queries in four categories:

**Core triggers** (2-3 queries): Direct phrasings of the skill's primary use case.
**Synonym triggers** (1-2 queries): Alternative phrasings that should also activate the skill.
**Boundary cases** (1-2 queries): Edge cases near the boundary of the skill's scope.
**Adjacent non-triggers** (2-3 queries): Phrases from adjacent domains that should NOT activate the skill.

### Step 3: Evaluate Each Query

For each generated query:

1. Determine whether a user typing that phrase would intend to invoke this skill.
2. Determine whether the description, as written, would route that phrase to this skill.
3. Identify whether the routing decision is CORRECT (intended and routed match) or MISCALIBRATED
   (routing decision differs from intent).

### Step 4: Produce Calibration Report

Output the report in this format:

```
DESCRIPTION CALIBRATION REPORT
==============================

Description under test:
  "<description text>"

Calibration Queries:

Core Triggers:
  [CORRECT/MISCALIBRATED] "<query>" — Routes: YES/NO | Intent: MATCH/NO-MATCH | <explanation>

Synonym Triggers:
  [CORRECT/MISCALIBRATED] "<query>" — Routes: YES/NO | Intent: MATCH/NO-MATCH | <explanation>

Boundary Cases:
  [CORRECT/MISCALIBRATED] "<query>" — Routes: YES/NO | Intent: MATCH/NO-MATCH | <explanation>

Adjacent Non-Triggers:
  [CORRECT/MISCALIBRATED] "<query>" — Routes: YES/NO | Intent: MATCH/NO-MATCH | <explanation>

Calibration Score: X/Y correct
Overall: [WELL-CALIBRATED | OVER-BROAD | UNDER-BROAD | AMBIGUOUS]

Recommendations:
  - <specific change to the description to fix any miscalibrations, or "None" if well-calibrated>
```

**Overall status meanings**:
- WELL-CALIBRATED: All queries produce the expected routing outcome
- OVER-BROAD: Description routes phrases that should not trigger the skill
- UNDER-BROAD: Description fails to route phrases that should trigger the skill
- AMBIGUOUS: Description is unclear — some phrases route inconsistently

## Verification

- [ ] All four query categories are represented
- [ ] Each query has a routing decision and intent assessment
- [ ] Overall status matches the pattern of CORRECT/MISCALIBRATED verdicts
- [ ] Recommendations address each miscalibrated query specifically
