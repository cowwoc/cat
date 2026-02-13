# Plan: cat-workflow-improvements

## Objective
multiple improvements to CAT workflow

## Details
Changes:
1. CAT = "Agents that land on their feet" (not "Coordinated Agentic Tasks")

2. /cat:research - for moderate/high complexity features (not just niche domains)

3. targetContextUsage now uses whole numbers (40 = 40%, not 0.4)

4. Added "Continuous improvement" to feature list - learns from mistakes
   and runs regular retrospectives

5. Commit squashing updated:
   - Implementation commits (feature, bugfix, test, refactor, docs) squashed together
   - Config commits squashed separately

6. Automatic dependency management:
   - add-task: Can specify tasks this new task blocks (updates their dependencies)
   - execute-task: Checks dependent tasks when task completes, unblocks if ready
   - remove-task: Removes task from dependencies lists when deleted

## Acceptance Criteria
- [x] Implementation complete
- [x] Verified working
