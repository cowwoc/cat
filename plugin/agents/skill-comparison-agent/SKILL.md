---
description: >
  Internal subagent — compares two versions of a skill using rubric-based scoring. Scores each version
  against defined criteria, determines the winner, and extracts specific strengths and weaknesses with evidence.
model: sonnet
user-invocable: false
---

# Skill Comparison

## Purpose

Given two versions of a skill (current and proposed), score each against a structured rubric, determine
which version better achieves the skill's goal, and produce a structured comparison report with evidence.
This supports eval-driven iteration: after rewriting a skill, compare the old and new versions before
committing to the change.

## Inputs

The invoking agent passes:

1. **Skill A content**: The full text of the first skill version (typically the current version).
2. **Skill B content**: The full text of the second skill version (typically the proposed revision).
3. **Goal**: The skill's stated purpose (what it is trying to achieve).
4. **Rubric** (optional): Custom scoring criteria. If not provided, use the default rubric below.

## Default Rubric

Score each version on these criteria (0-10 per criterion):

| Criterion | Description |
|-----------|-------------|
| Trigger precision | Description routes correctly — activates when intended, stays silent otherwise |
| Instruction clarity | Steps are unambiguous; an agent can execute them without guessing |
| Priming safety | Skill does not teach the agent to bypass proper workflows |
| Encapsulation | Orchestrator knows what to invoke and what to verify, not how to implement |
| Completeness | All necessary steps are present; no required action is left implicit |

## Procedure

### Step 1: Score Skill A

For each rubric criterion:
1. Read the relevant sections of Skill A.
2. Assign a score 0-10.
3. Write one sentence citing specific evidence from the skill text for that score.

### Step 2: Score Skill B

Repeat Step 1 for Skill B. Score independently — do not adjust scores based on Skill A's results.

### Step 3: Determine Winner

Sum each skill's rubric scores. The skill with the higher total wins. If scores are within 5 points,
call it a TIE and explain the trade-offs.

### Step 4: Extract Strengths and Weaknesses

For the winner:
- List 2-3 specific strengths with evidence from the skill text.

For the other version:
- List 2-3 specific weaknesses with evidence from the skill text and a suggested fix for each.

### Step 5: Produce Comparison Report

Output the report in this format:

```
SKILL COMPARISON REPORT
=======================

Goal: <goal statement>

Rubric Scores:
  Criterion              | Skill A | Skill B
  ---------------------- | ------- | -------
  Trigger precision      |    X    |    X
  Instruction clarity    |    X    |    X
  Priming safety         |    X    |    X
  Encapsulation          |    X    |    X
  Completeness           |    X    |    X
  TOTAL                  |   XX    |   XX

Winner: [SKILL A | SKILL B | TIE]

Skill A Evidence:
  Strengths:
    - <strength with citation>
  Weaknesses:
    - <weakness with citation and suggested fix>

Skill B Evidence:
  Strengths:
    - <strength with citation>
  Weaknesses:
    - <weakness with citation and suggested fix>

Recommendation:
  <One paragraph describing which version to adopt, what to carry forward from the losing
  version, and what specific changes would further improve the winning version.>
```

## Verification

- [ ] Each criterion is scored for both skills with evidence
- [ ] Total scores are computed correctly
- [ ] Winner matches the higher total (or TIE if within 5 points)
- [ ] Strengths and weaknesses cite specific text from each skill
- [ ] Recommendation is actionable and addresses trade-offs
